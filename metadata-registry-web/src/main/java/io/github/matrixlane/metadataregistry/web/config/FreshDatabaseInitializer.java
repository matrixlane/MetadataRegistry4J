package io.github.matrixlane.metadataregistry.web.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全新 SQLite 空库启动引导（sqlite-empty-db-bootstrap，见 openspec 变更）。
 *
 * <p>个人版默认 SQLite 文件库在新检出环境不存在时，旧迁移链（如 SQLite 不支持的
 * {@code DROP COLUMN IF EXISTS}）无法从空库重放，导致首次启动失败。本初始化器在
 * main() 中以 {@code ApplicationContextInitializer} 形式注册：此时 Spring 环境已
 * 准备完毕（config data 与 spring.config.import 均已解析）、上下文尚未刷新
 * （数据源/EMF/Flyway 均未创建），正好完成"DDL 前识别"：
 *
 * <ol>
 *   <li>确保所选 SQLite 文件的父目录存在（驱动自身负责创建空文件）；</li>
 *   <li>用 JDBC 元数据判定"全新空库"（文件不存在，或文件中没有任何用户表）；</li>
 *   <li>仅对全新库注入高优先级属性：{@code ddl-auto=update}（由 ORM 建出最终结构）、
 *       Flyway 以共同基线版本 {@link #FRESH_BASELINE_VERSION} 基线化、SQLite 下
 *       Flyway 的 default/唯一 schema 固定为 {@code main}。</li>
 * </ol>
 *
 * <p>属性被插入到 systemEnvironment 之后、config data 之前：环境变量与命令行参数
 * 仍可覆盖，而 profile 默认（如 personal 的 ddl-auto=none）被让位。既有库
 * （含表）完全不受影响；有业务表但无 Flyway 历史的旧库维持既有
 * baseline-on-migrate 兼容链并输出明确警告。
 *
 * <p>Flyway 关闭（测试/本地开发）或非 SQLite 方言时不做任何事。
 *
 * <p>注：未采用 {@code META-INF/spring/*.imports} 注册 EnvironmentPostProcessor，
 * Spring Boot 3.5 的 repackage 不会把应用自身的 META-INF/spring 目录迁入
 * BOOT-INF/classes（Packager.isTransformable 仅放行 META-INF/services），可执行
 * jar 内该注册文件不可见。
 */
@Slf4j
public class FreshDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /**
     * 新库共同基线版本：以 SQL Server 当前最终迁移版本为权威（见 openspec
     * sqlite-empty-db-bootstrap）。后续新增迁移在三方言使用相同版本号时，
     * 需同步提高本常量——CommonBaselineGuardTest 扫描三个迁移目录做静态守卫。
     */
    public static final String FRESH_BASELINE_VERSION = "20261009012";

    private static final String SQLITE_URL_PREFIX = "jdbc:sqlite:";
    private static final String PROPERTY_SOURCE_NAME = "mdrFreshDatabase";
    private static final String HISTORY_TABLE = "flyway_schema_history";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        apply(applicationContext.getEnvironment());
    }

    /**
     * 空库识别与属性注入。单独抽出便于测试直接调用。
     */
    public void apply(ConfigurableEnvironment environment) {
        if (!environment.getProperty("spring.flyway.enabled", Boolean.class, true)) {
            return;
        }
        String url = environment.getProperty("spring.datasource.url");
        if (url == null || !url.startsWith(SQLITE_URL_PREFIX)) {
            return;
        }
        String filePath = stripQuery(url.substring(SQLITE_URL_PREFIX.length()));
        if (filePath.isBlank()) {
            return;
        }
        Path dbFile = Paths.get(filePath).toAbsolutePath().normalize();
        ensureParentDirectory(dbFile);

        Freshness freshness = detectFreshness(dbFile);
        if (freshness == Freshness.FRESH) {
            applyFreshOverrides(environment, dbFile);
        } else if (freshness == Freshness.LEGACY_UNHISTORIED) {
            log.warn("SQLite 库 {} 存在业务表但没有 Flyway 历史表：维持既有兼容链"
                            + "（baseline-on-migrate 按 application.yml 基线版本处理，重放带幂等守卫的迁移）。"
                            + "若启动失败说明该库来源无法自动识别，请人工核对迁移历史。",
                    dbFile);
        }
    }

    private enum Freshness {
        /** 全新空库：启用 ORM 建表 + 共同基线。 */
        FRESH,
        /** 有业务表且有 Flyway 历史：完全维持现状。 */
        EXISTING,
        /** 有业务表但无 Flyway 历史：旧 ddl-auto 时代遗留库，维持兼容链并告警。 */
        LEGACY_UNHISTORIED
    }

    private void ensureParentDirectory(Path dbFile) {
        Path parent = dbFile.getParent();
        if (parent == null || Files.exists(parent)) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "无法创建 SQLite 数据库目录 " + parent + "：" + e.getMessage(), e);
        }
    }

    private Freshness detectFreshness(Path dbFile) {
        if (!Files.exists(dbFile)) {
            // 文件由 SQLite 驱动在首次连接时创建；parent 已确保存在。
            return Freshness.FRESH;
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            log.warn("未找到 sqlite-jdbc 驱动，跳过空库识别: {}", e.getMessage());
            return Freshness.EXISTING;
        }
        // 连接不存在的文件会创建它；此处文件已存在，连接是纯只读探测。
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement st = conn.createStatement()) {
            int tables;
            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                rs.next();
                tables = rs.getInt(1);
            }
            if (tables == 0) {
                return Freshness.FRESH;
            }
            boolean hasHistory;
            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='" + HISTORY_TABLE + "'")) {
                rs.next();
                hasHistory = rs.getInt(1) > 0;
            }
            return hasHistory ? Freshness.EXISTING : Freshness.LEGACY_UNHISTORIED;
        } catch (SQLException e) {
            // 探测失败不阻断启动：保持原行为，让连接池初始化给出原始诊断。
            log.warn("SQLite 空库识别探测失败（保持默认启动路径）: {}", e.getMessage());
            return Freshness.EXISTING;
        }
    }

    private void applyFreshOverrides(ConfigurableEnvironment environment, Path dbFile) {
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("spring.jpa.hibernate.ddl-auto", "update");
        overrides.put("spring.flyway.baseline-on-migrate", "true");
        overrides.put("spring.flyway.baseline-version", FRESH_BASELINE_VERSION);
        overrides.put("spring.flyway.schemas", "main");
        overrides.put("spring.flyway.default-schema", "main");
        environment.getPropertySources().addAfter(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
        log.info("检测到全新 SQLite 空库 {}：启用 ORM 建表，Flyway 以共同基线 v{} 初始化"
                        + "（schemas=main）。应用表将由当前实体模型创建。",
                dbFile, FRESH_BASELINE_VERSION);
    }

    /** 去掉 JDBC URL 中的查询参数（如 journal_mode=WAL），仅用于定位文件。 */
    private String stripQuery(String pathPart) {
        int q = pathPart.indexOf('?');
        return q >= 0 ? pathPart.substring(0, q) : pathPart;
    }
}


