package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceConfigRequest;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceConfigView;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceTestResult;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源配置服务（见 openspec: datasource-configuration）。
 *
 * <p>职责：连接测试（不落地的预检）、配置合法性校验（版本×方言约束）、
 * 将数据源配置合并写入工作目录下的 {@code application-local.yml}（保留文件
 * 中既有其他配置），统一"写配置 + 引导重启"生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceConfigService {

    private static final Path LOCAL_CONFIG = Paths.get("application-local.yml");
    private static final int LOGIN_TIMEOUT_SECONDS = 5;

    private final SystemSettingService systemSettingService;
    private final DatabaseEnvironment databaseEnvironment;

    @Value("${spring.datasource.url:}")
    private String currentUrl;

    @Value("${spring.datasource.driver-class-name:}")
    private String currentDriver;

    /**
     * 当前数据源只读视图（不回传口令）。
     */
    public DatasourceConfigView currentConfig() {
        return new DatasourceConfigView(
                databaseEnvironment.vendor().name().toLowerCase(),
                maskUrl(currentUrl),
                databaseEnvironment.schema(),
                currentDriver
        );
    }

    /**
     * 连接测试：使用所给参数建立真实连接并探测产品名与版本。失败抛业务异常，
     * 不影响当前运行中的数据源。
     */
    public DatasourceTestResult testConnection(DatasourceConfigRequest request) {
        DatabaseVendor vendor = parseVendor(request.vendor());
        validateEditionConstraint(vendor, request.edition());
        String url = buildJdbcUrl(vendor, request);
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        try (Connection conn = openConnection(vendor, url, request)) {
            var md = conn.getMetaData();
            return new DatasourceTestResult(true, md.getDatabaseProductName(), md.getDatabaseProductVersion(), "连接成功");
        } catch (SQLException e) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "连接测试失败: " + e.getMessage());
        }
    }

    /**
     * 保存数据源配置：合法性校验 → 连接测试 → 合并写入 application-local.yml。
     * 调用方负责提示"重启后生效"。
     */
    public void saveConfig(DatasourceConfigRequest request) {
        DatabaseVendor vendor = parseVendor(request.vendor());
        validateEditionConstraint(vendor, request.edition());
        // 连接测试通过才允许落地
        testConnection(request);

        String url = buildJdbcUrl(vendor, request);
        Map<String, Object> root = loadExisting();
        Map<String, Object> spring = child(root, "spring");
        Map<String, Object> datasource = child(spring, "datasource");
        datasource.put("url", url);
        datasource.put("driver-class-name", driverClass(vendor));
        if (hasText(request.username())) {
            datasource.put("username", request.username());
            datasource.put("password", request.password() == null ? "" : request.password());
        } else {
            datasource.remove("username");
            datasource.remove("password");
        }
        if (vendor == DatabaseVendor.SQLITE) {
            Map<String, Object> hikari = child(datasource, "hikari");
            hikari.put("maximum-pool-size", 1);
            hikari.put("minimum-idle", 1);
        }

        Map<String, Object> jpa = child(spring, "jpa");
        Map<String, Object> jpaProps = child(jpa, "properties");
        Map<String, Object> hibernate = child(jpaProps, "hibernate");
        hibernate.put("default_schema", defaultSchemaFor(vendor, request.schema()));
        if (vendor == DatabaseVendor.DM) {
            // 达梦使用本项目 DM 方言（Oracle 兼容 + DM IDENTITY 语法）；
            // context 为 DM 保留字，需全局引用标识符
            hibernate.put("dialect", "io.github.matrixlane.metadataregistry.web.config.DmOracleDialect");
            hibernate.put("globally_quoted_identifiers", true);
        }

        Map<String, Object> flyway = child(spring, "flyway");
        flyway.put("locations", "classpath:db/migration/" + flywayDir(vendor));

        writeBack(root);
        log.warn("数据源配置已写入 {}（vendor={}），需重启生效", LOCAL_CONFIG.toAbsolutePath(), vendor);
    }

    // ── 合法性校验 ──────────────────────────────────────────────

    private DatabaseVendor parseVendor(String vendor) {
        try {
            return DatabaseVendor.resolve(vendor, null);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, e.getMessage());
        }
    }

    /**
     * 版本×方言约束：TEAM 版不得使用 SQLite；PERSONAL 版三种方言均可。
     * edition 参数优先（向导场景），否则读取已持久化的版本。
     */
    private void validateEditionConstraint(DatabaseVendor vendor, String editionParam) {
        DeploymentEdition edition = null;
        if (hasText(editionParam)) {
            edition = DeploymentEdition.parse(editionParam);
        } else {
            edition = systemSettingService.getEdition();
        }
        if (edition == DeploymentEdition.TEAM && vendor == DatabaseVendor.SQLITE) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "团队版不支持 SQLite，请配置 SQL Server 或达梦等独立数据库");
        }
    }

    // ── JDBC URL 与驱动 ─────────────────────────────────────────

    private String buildJdbcUrl(DatabaseVendor vendor, DatasourceConfigRequest request) {
        return switch (vendor) {
            case SQLITE -> {
                if (!hasText(request.filePath())) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED, "SQLite 需要指定数据库文件路径");
                }
                yield "jdbc:sqlite:" + request.filePath();
            }
            case SQLSERVER -> {
                requireHostDatabase(request);
                StringBuilder sb = new StringBuilder("jdbc:sqlserver://")
                        .append(request.host()).append(':').append(portOr(request.port(), 1433))
                        .append(";databaseName=").append(request.database())
                        .append(";encrypt=true;trustServerCertificate=true");
                if (!hasText(request.username())) {
                    sb.append(";integratedSecurity=true");
                }
                yield sb.toString();
            }
            case DM -> {
                requireHostDatabase(request);
                yield "jdbc:dm://" + request.host() + ':' + portOr(request.port(), 5236);
            }
        };
    }

    private void requireHostDatabase(DatasourceConfigRequest request) {
        if (!hasText(request.host())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "数据库主机不能为空");
        }
    }

    private Connection openConnection(DatabaseVendor vendor, String url, DatasourceConfigRequest request) throws SQLException {
        if (hasText(request.username())) {
            return DriverManager.getConnection(url, request.username(), request.password() == null ? "" : request.password());
        }
        return DriverManager.getConnection(url);
    }

    private String driverClass(DatabaseVendor vendor) {
        return switch (vendor) {
            case SQLITE -> "org.sqlite.JDBC";
            case SQLSERVER -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case DM -> "dm.jdbc.driver.DmDriver";
        };
    }

    private String defaultSchemaFor(DatabaseVendor vendor, String requested) {
        if (hasText(requested)) {
            return requested.trim();
        }
        return switch (vendor) {
            case SQLITE -> "";
            case SQLSERVER -> "mdr";
            case DM -> "MDR";
        };
    }

    private String flywayDir(DatabaseVendor vendor) {
        return switch (vendor) {
            case SQLITE -> "sqlite";
            case SQLSERVER -> "sqlserver";
            case DM -> "dm";
        };
    }

    private int portOr(Integer port, int defaultPort) {
        return port == null ? defaultPort : port;
    }

    private String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("(?i)(password=)[^;]*", "$1***");
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    // ── application-local.yml 合并读写 ──────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadExisting() {
        if (Files.exists(LOCAL_CONFIG)) {
            try (InputStream in = Files.newInputStream(LOCAL_CONFIG)) {
                Map<String, Object> loaded = new Yaml().load(in);
                if (loaded != null) {
                    return new LinkedHashMap<>(loaded);
                }
            } catch (IOException e) {
                throw new BusinessException("读取 application-local.yml 失败: " + e.getMessage());
            }
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> child(Map<String, Object> parent, String key) {
        Object existing = parent.get(key);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        parent.put(key, created);
        return created;
    }

    private void writeBack(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        try (Writer writer = Files.newBufferedWriter(LOCAL_CONFIG)) {
            yaml.dump(root, writer);
        } catch (IOException e) {
            throw new BusinessException("写入 application-local.yml 失败: " + e.getMessage());
        }
    }
}
