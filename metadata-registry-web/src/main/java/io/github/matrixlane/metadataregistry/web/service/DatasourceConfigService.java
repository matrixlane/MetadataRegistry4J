package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceConfigRequest;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceConfigView;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceTestResult;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 数据源配置服务（见 openspec: datasource-configuration）。
 *
 * <p>职责：连接测试（不落地的预检）、配置合法性校验（版本×方言约束）、
 * 将数据源配置合并写入固定位置的 {@code application-local.yml}（保留文件中
 * 既有其他配置），统一"写配置 + 引导重启"生效。
 */
@Slf4j
@Service
public class DatasourceConfigService {

    /**
     * 本地配置文件固定放在用户主目录下，避免依赖 JVM 启动时的工作目录（user.dir）。
     * 以前使用相对路径 {@code ./application-local.yml} 时，从 IDE、Maven、jar 包等
     * 不同方式启动会导致保存位置和读取位置不一致，配置重启后无法生效。
     *
     * <p>可通过系统属性 {@code metadata.registry.config.file} 覆盖该路径，便于测试。
     */
    private static final Path DEFAULT_LOCAL_CONFIG = Paths.get(
            System.getProperty("user.home"), ".metadata-registry", "application-local.yml");

    private static final int LOGIN_TIMEOUT_SECONDS = 5;

    private final SystemSettingService systemSettingService;
    private final DatabaseEnvironment databaseEnvironment;
    private final Path localConfig;

    @Value("${spring.datasource.url:}")
    private String currentUrl;

    @Value("${spring.datasource.driver-class-name:}")
    private String currentDriver;

    @Autowired
    public DatasourceConfigService(SystemSettingService systemSettingService,
                                     DatabaseEnvironment databaseEnvironment) {
        this(systemSettingService, databaseEnvironment, resolveLocalConfig());
    }

    /**
     * 测试专用构造器：避免静态字段初始化顺序导致真实用户配置被测试污染。
     */
    DatasourceConfigService(SystemSettingService systemSettingService,
                            DatabaseEnvironment databaseEnvironment,
                            Path localConfig) {
        this.systemSettingService = systemSettingService;
        this.databaseEnvironment = databaseEnvironment;
        this.localConfig = localConfig;
    }

    private static Path resolveLocalConfig() {
        String override = System.getProperty("metadata.registry.config.file");
        return override != null && !override.isBlank()
                ? Paths.get(override)
                : DEFAULT_LOCAL_CONFIG;
    }

    /**
     * 本地配置文件路径（用户主目录下，或经 {@code metadata.registry.config.file}
     * 系统属性覆盖）。供 {@code LocalConfigBootstrapInitializer} 与启动迁移逻辑共享，
     * 保证"保存"与"加载"指向同一文件。
     */
    public static Path localConfigPath() {
        return resolveLocalConfig();
    }

    /**
     * 当前数据源只读视图（不回传口令）。
     */
    public DatasourceConfigView currentConfig() {
        ParsedUrl parsed = parseCurrentUrl();
        return new DatasourceConfigView(
                databaseEnvironment.vendor().name().toLowerCase(),
                maskUrl(currentUrl),
                databaseEnvironment.schema(),
                currentDriver,
                parsed.host(),
                parsed.port(),
                parsed.database(),
                parsed.filePath(),
                parsed.hasCredentials()
        );
    }

    /**
     * 当前 JDBC URL 的结构化解析结果。URL 各方言的格式由本类 {@link #buildJdbcUrl}
     * 产生（或手工按同格式编写），因此可以可靠地反向解析。
     */
    private record ParsedUrl(String host, Integer port, String database, String filePath, boolean hasCredentials) {
        static ParsedUrl empty() {
            return new ParsedUrl(null, null, null, null, false);
        }
    }

    private ParsedUrl parseCurrentUrl() {
        String url = currentUrl;
        if (url == null || url.isBlank()) {
            return ParsedUrl.empty();
        }
        if (url.startsWith("jdbc:sqlite:")) {
            return new ParsedUrl(null, null, null, url.substring("jdbc:sqlite:".length()), false);
        }
        if (url.startsWith("jdbc:sqlserver://")) {
            String rest = url.substring("jdbc:sqlserver://".length());
            int semi = rest.indexOf(';');
            String hostPort = semi >= 0 ? rest.substring(0, semi) : rest;
            String host = hostPort;
            Integer port = null;
            int colon = hostPort.indexOf(':');
            if (colon >= 0) {
                host = hostPort.substring(0, colon);
                try {
                    port = Integer.parseInt(hostPort.substring(colon + 1));
                } catch (NumberFormatException ignored) {
                    // 端口无法解析时按未提供处理
                }
            }
            String database = null;
            var matcher = Pattern.compile("(?i)(?:^|;)databaseName=([^;]*)").matcher(url);
            if (matcher.find()) {
                database = matcher.group(1);
            }
            boolean hasCredentials = !url.toLowerCase().contains("integratedsecurity=true");
            return new ParsedUrl(host, port, database, null, hasCredentials);
        }
        if (url.startsWith("jdbc:dm://")) {
            String hostPort = url.substring("jdbc:dm://".length());
            String host = hostPort;
            Integer port = null;
            int colon = hostPort.indexOf(':');
            if (colon >= 0) {
                host = hostPort.substring(0, colon);
                try {
                    port = Integer.parseInt(hostPort.substring(colon + 1));
                } catch (NumberFormatException ignored) {
                    // 端口无法解析时按未提供处理
                }
            }
            // DM 的凭据不体现在 URL 中，无法判断，按无凭据处理
            return new ParsedUrl(host, port, null, null, false);
        }
        return ParsedUrl.empty();
    }

    /**
     * 连接测试：使用所给参数建立真实连接并探测产品名与版本。失败抛业务异常，
     * 不影响当前运行中的数据源。
     */
    public DatasourceTestResult testConnection(DatasourceConfigRequest request) {
        DatabaseVendor vendor = parseVendor(request.vendor());
        validateEditionConstraint(vendor, request.edition());
        prepareFilePath(vendor, request);
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
        // 以 ${ENV:值} 形式落盘：保存值在重启后对 profile 默认值生效
        // （profile 经 spring.config.import 让位于本文件，见 application-personal.yml），
        // 环境变量仍可覆盖已保存值（占位符在属性绑定时对整体 Environment 解析）。
        datasource.put("url", envWrapped("DB_URL", url));
        datasource.put("driver-class-name", envWrapped("DB_DRIVER", driverClass(vendor)));
        // SQLite 无认证概念：凭据一律不落盘（浏览器自动填充/表单残留也会随请求提交）
        if (vendor != DatabaseVendor.SQLITE && hasText(request.username())) {
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
        // ORM 映射随方言切换：SQLite 用 ext_ 前缀的 orm-sqlite.xml，
        // SQL Server/达梦用 schema="ext" 的 orm-team.xml。沿用上一方言的映射会让
        // 新库建表失败（SQLite 无法创建 ext 模式表）。
        jpa.put("mapping-resources", mappingResourcesFor(vendor));
        Map<String, Object> jpaProps = child(jpa, "properties");
        Map<String, Object> hibernate = child(jpaProps, "hibernate");
        hibernate.put("default_schema", envWrapped("DB_DEFAULT_SCHEMA", defaultSchemaFor(vendor, request.schema())));
        hibernate.remove("globally_quoted_identifiers");
        hibernate.put("dialect", envWrapped("HIBERNATE_DIALECT", dialectClass(vendor)));
        if (vendor == DatabaseVendor.DM) {
            // context 为 DM 保留字，需全局引用标识符
            hibernate.put("globally_quoted_identifiers", true);
        }

        Map<String, Object> flyway = child(spring, "flyway");
        flyway.put("locations", envWrapped("DB_VENDOR", "classpath:db/migration/" + flywayDir(vendor)));
        // Flyway schemas/default-schema MUST 按方言落盘：SQLite 用 main；
        // SQL Server/达梦用 schemas=mdr,ext + default-schema=ext——flyway_schema_history
        // 非 ISO/IEC 11179 内容，依项目 schema 分层规则只能落 ext（AGENTS.md），
        // Flyway 以 default-schema 为会话默认 schema，历史表随之落 ext。
        // 只给 SQLite 写、切服务端方言时沿用旧值，会让 Flyway 在服务端库的
        // [main] 里找不到历史表而从头重放不可重放的老迁移（2026-10-27 真实事故）。
        if (vendor == DatabaseVendor.SQLITE) {
            flyway.put("schemas", envWrapped("FLYWAY_SCHEMAS", "main"));
            flyway.put("default-schema", envWrapped("FLYWAY_DEFAULT_SCHEMA", "main"));
        } else {
            flyway.put("schemas", envWrapped("FLYWAY_SCHEMAS", "mdr,ext"));
            flyway.put("default-schema", envWrapped("FLYWAY_DEFAULT_SCHEMA", "ext"));
        }

        writeBack(root);
        recordDatasourceConfirmation(vendor, url, request);
        log.warn("数据源配置已写入 {}（vendor={}），需重启生效", localConfig.toAbsolutePath(), vendor);
    }

    /**
     * 保存即确认（init-phase-database-free）：写本地初始化状态记录
     * （datasourceConfigured=true 与当前配置指纹）。指纹的 6 项输入与
     * {@code BootstrapPhaseResolver.currentFingerprint} 严格一致——全部取自
     * saveConfig 对任何方言都会落盘的键，并对环境变量占位符按当前环境求值，
     * 保证"保存时计算"与"下次启动时计算"结果相同。
     *
     * <p>写入失败不改变保存结果：下次启动因无确认记录而退回向导相，
     * 用户重新确认一次即可。
     */
    private void recordDatasourceConfirmation(DatabaseVendor vendor, String url, DatasourceConfigRequest request) {
        try {
            String fingerprint = io.github.matrixlane.metadataregistry.web.bootstrap.DatasourceFingerprint.compute(
                    vendor.name(),
                    resolveEnvWrapped(url),
                    resolveEnvWrapped(dialectClass(vendor)),
                    mappingResourcesFor(vendor),
                    resolveEnvWrapped("classpath:db/migration/" + flywayDir(vendor)),
                    resolveEnvWrapped(defaultSchemaFor(vendor, request.schema())));
            io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStateStore store =
                    new io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStateStore(
                            localConfig.resolveSibling("initialization-state.yml"));
            io.github.matrixlane.metadataregistry.web.bootstrap.InitializationState current =
                    store.read().orElse(io.github.matrixlane.metadataregistry.web.bootstrap.InitializationState.fresh());
            store.write(current.withDatasourceConfirmed(fingerprint));
        } catch (Exception e) {
            log.warn("初始化状态记录写入失败（下次启动将要求重新确认数据源）: {}", e.getMessage());
        }
    }

    /** 按保存时的环境解析 {@code ${ENV:默认值}} 占位符（环境变量优先，与启动期占位符语义一致）。 */
    private static String resolveEnvWrapped(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            String inner = trimmed.substring(2, trimmed.length() - 1);
            int separator = inner.indexOf(':');
            if (separator > 0) {
                String envKey = inner.substring(0, separator);
                String defaultValue = inner.substring(separator + 1);
                String envValue = System.getenv(envKey);
                return envValue != null && !envValue.isBlank() ? envValue : defaultValue;
            }
        }
        return trimmed;
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

    /**
     * SQLite 场景下预先创建数据库文件所在目录，避免 JDBC 驱动因目录不存在而报
     * "path to db: dir does not exist"。
     */
    private void prepareFilePath(DatabaseVendor vendor, DatasourceConfigRequest request) {
        if (vendor != DatabaseVendor.SQLITE || !hasText(request.filePath())) {
            return;
        }
        Path file = Paths.get(resolveSqliteFilePath(request.filePath()));
        Path parent = file.getParent();
        if (parent != null && !Files.exists(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED, "无法创建 SQLite 数据库目录: " + e.getMessage());
            }
        }
    }

    /**
     * SQLite 文件路径规整：展开 {@code ~} 前缀、转为绝对路径并规范化，
     * 保证落盘 JDBC URL 指向唯一确定位置（相对路径会随 JVM 启动目录漂移）。
     */
    private String resolveSqliteFilePath(String filePath) {
        if (!hasText(filePath)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "SQLite 需要指定数据库文件路径");
        }
        String expanded = filePath.equals("~") || filePath.startsWith("~/") || filePath.startsWith("~\\")
                ? System.getProperty("user.home") + filePath.substring(1)
                : filePath;
        return Paths.get(expanded).toAbsolutePath().normalize().toString();
    }

    /**
     * 生成 {@code ${ENV:值}} 占位包装串；值为 null 时落空默认。
     */
    private static String envWrapped(String envKey, String value) {
        return "${" + envKey + ":" + (value == null ? "" : value) + "}";
    }

    private String buildJdbcUrl(DatabaseVendor vendor, DatasourceConfigRequest request) {
        return switch (vendor) {
            case SQLITE -> "jdbc:sqlite:" + resolveSqliteFilePath(request.filePath());
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
        if (vendor != DatabaseVendor.SQLITE && hasText(request.username())) {
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

    private String dialectClass(DatabaseVendor vendor) {
        return switch (vendor) {
            case SQLITE -> "org.hibernate.community.dialect.SQLiteDialect";
            case SQLSERVER -> "org.hibernate.dialect.SQLServerDialect";
            case DM -> "io.github.matrixlane.metadataregistry.web.config.DmOracleDialect";
        };
    }

    private String flywayDir(DatabaseVendor vendor) {
        return switch (vendor) {
            case SQLITE -> "sqlite";
            case SQLSERVER -> "sqlserver";
            case DM -> "dm";
        };
    }

    String mappingResourcesFor(DatabaseVendor vendor) {
        return vendor == DatabaseVendor.SQLITE ? "orm/orm-sqlite.xml" : "orm/orm-team.xml";
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
        migrateTo(localConfig);
        if (Files.exists(localConfig)) {
            try (InputStream in = Files.newInputStream(localConfig)) {
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

    /**
     * 兼容旧部署：以前配置写在 JVM 启动目录下的 {@code application-local.yml}，
     * 现在固定到用户主目录。若新位置不存在、或新位置配置已损坏（例如指向已不存在的
     * SQLite 临时目录），而旧位置存在，自动复制一次，避免用户已保存的配置"丢失"。
     *
     * <p>该方法在 Spring Boot 启动前由 {@code main} 调用，确保数据源初始化前配置
     * 已经位于正确位置。
     */
    public static void migrateLegacyConfigIfNeeded() {
        migrateTo(resolveLocalConfig());
        sanitizeLegacyBehaviorKeys(resolveLocalConfig());
    }

    /**
     * 剥离旧格式 application-local.yml 中的 {@code spring.jpa.hibernate.ddl-auto}
     *（sqlite-empty-db-bootstrap）。
     *
     * <p>本地配置经 {@link io.github.matrixlane.metadataregistry.web.config.LocalConfigBootstrapInitializer}
     * 提升到 profile 之上后，旧文件里远古版本写入的 {@code ddl-auto=update} 会在
     * personal profile 的 SQLite 上触发批量元数据读取失败（既有库 ddl-auto 必须为
     * none，见 application-personal.yml），必须剥离。
     *
     * <p>注意：{@code spring.flyway.enabled} 刻意<strong>不剥离</strong>——旧部署依赖
     * 它关闭 Flyway 来启动"schema 已漂移、无 Flyway 历史"的既有库（实测 2026-10-20：
     * 此类库重放 V20260919003 会因旧 ddl-auto 建表结构与迁移假设不符而启动失败）。
     * 该键是用户侧显式选择，且全新空库由 FreshDatabaseInitializer 强制启用 Flyway，
     * 不受其影响。
     */
    public static void sanitizeLegacyBehaviorKeys(Path target) {
        if (!Files.exists(target)) {
            return;
        }
        Map<String, Object> root;
        try (InputStream in = Files.newInputStream(target)) {
            root = new Yaml().load(in);
        } catch (IOException e) {
            log.warn("清理旧格式本地配置失败（保持原文件）: {}", e.getMessage());
            return;
        }
        if (root == null) {
            return;
        }
        if (!removeNested(root, "spring", "jpa", "hibernate", "ddl-auto")) {
            return;
        }
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        try (Writer writer = Files.newBufferedWriter(target)) {
            new Yaml(options).dump(root, writer);
        } catch (IOException e) {
            log.warn("回写清理后的本地配置失败: {}", e.getMessage());
        }
        log.warn("已从 {} 剥离旧版 jpa.hibernate.ddl-auto（旧值 update 在新配置优先级下会"
                + "覆盖 personal profile 的 none，导致既有 SQLite 库批量元数据读取失败）。", target);
    }

    /** 删除嵌套 Map 中的键；变空的父 Map 一并清理。返回是否有键被删除。 */
    private static boolean removeNested(Map<String, Object> root, String... keys) {
        return removeNested(root, keys, 0);
    }

    @SuppressWarnings("unchecked")
    private static boolean removeNested(Map<String, Object> node, String[] keys, int depth) {
        if (depth == keys.length - 1) {
            if (!node.containsKey(keys[depth])) {
                return false;
            }
            node.remove(keys[depth]);
            return true;
        }
        Object next = node.get(keys[depth]);
        if (!(next instanceof Map<?, ?>)) {
            return false;
        }
        Map<String, Object> child = (Map<String, Object>) next;
        boolean removed = removeNested(child, keys, depth + 1);
        if (removed && child.isEmpty()) {
            node.remove(keys[depth]);
        }
        return removed;
    }

    private static void migrateTo(Path target) {
        if (!shouldMigrateTo(target)) {
            return;
        }
        Path legacy = findLegacyConfig();
        if (legacy == null) {
            return;
        }
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(target)) {
                Files.copy(legacy, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(legacy, target);
            }
            log.info("已从旧位置 {} 迁移数据源配置到 {}", legacy.toAbsolutePath(), target.toAbsolutePath());
        } catch (IOException e) {
            log.warn("迁移旧数据源配置失败: {}", e.getMessage());
        }
    }

    private static boolean shouldMigrateTo(Path target) {
        if (!Files.exists(target)) {
            return findLegacyConfig() != null;
        }
        return isBrokenSqliteConfig(target) && findLegacyConfig() != null;
    }

    /**
     * 查找旧版 application-local.yml。旧版放在 JVM 工作目录下，而项目通常从仓库根目录
     * 或 web 模块目录启动，因此同时检查当前目录及其父目录。
     */
    private static Path findLegacyConfig() {
        Path current = Paths.get("application-local.yml");
        if (Files.exists(current)) {
            return current;
        }
        Path parent = Paths.get("..", "application-local.yml");
        if (Files.exists(parent)) {
            return parent;
        }
        return null;
    }

    private static boolean isBrokenSqliteConfig(Path config) {
        try (InputStream in = Files.newInputStream(config)) {
            Map<String, Object> loaded = new Yaml().load(in);
            if (loaded == null) {
                return false;
            }
            Object url = nestedValue(loaded, "spring", "datasource", "url");
            if (!(url instanceof String urlStr) || !urlStr.startsWith("jdbc:sqlite:")) {
                return false;
            }
            Path dbFile = Paths.get(urlStr.substring("jdbc:sqlite:".length()));
            Path dbParent = dbFile.getParent();
            return dbParent != null && !Files.exists(dbParent);
        } catch (IOException e) {
            return false;
        }
    }

    private static Object nestedValue(Map<String, Object> root, String... keys) {
        Object current = root;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
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

    Path localConfig() {
        return localConfig;
    }

    private void writeBack(Map<String, Object> root) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        Path target = localConfig.toAbsolutePath().normalize();
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp)) {
                yaml.dump(root, writer);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException("写入 application-local.yml 失败: " + e.getMessage());
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }
}
