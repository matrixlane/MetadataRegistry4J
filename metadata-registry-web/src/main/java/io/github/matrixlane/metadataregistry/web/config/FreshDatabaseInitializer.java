package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全新空库启动引导（sqlite-empty-db-bootstrap；baseline-data-init-rebuild 扩展
 * SQL Server/达梦）。
 *
 * <p>个人版默认 SQLite 文件库在新检出环境不存在时，旧迁移链（如 SQLite 不支持的
 * {@code DROP COLUMN IF EXISTS}）无法从空库重放，导致首次启动失败；SQL Server/达梦
 * 全新空库同样断裂：team profile 的 {@code spring.datasource.schema} 受
 * {@code defer-datasource-initialization: true} 影响被推迟到 EMF 之后，ddl-auto 建表时
 * mdr/ext schema 不存在而全部失败，Flyway 又把空 schema 判为无需基线化而从头重放
 * 不可重放的中段迁移。本初始化器在 main() 中以 {@code ApplicationContextInitializer}
 * 形式注册：此时 Spring 环境已准备完毕（config data 与 spring.config.import 均已解析）、
 * 上下文尚未刷新（数据源/EMF/Flyway 均未创建），正好完成"DDL 前识别"：
 *
 * <ol>
 *   <li>SQLite：确保库文件父目录存在，用 JDBC 元数据判定"全新空库"（文件不存在，
 *       或文件中没有任何用户表）；仅对全新库注入 {@code ddl-auto=update}、Flyway 以
 *       共同基线版本 {@link #FRESH_BASELINE_VERSION} 基线化、schema 固定 {@code main}。</li>
 *   <li>SQL Server：查 mdr/ext/dbo 全域无任何用户表判为空库；fresh 时先用裸 JDBC
 *       预建 flyway schemas 配置值对应的 schema（EMF 之前），再注入
 *       {@code ddl-auto=update} 与基线化覆盖，基线版本动态扫描
 *       {@code db/migration/sqlserver} 最大版本号。达梦同理（用户即模式，无需建 schema）。</li>
 * </ol>
 *
 * <p>属性被插入到 systemEnvironment 之后、config data 之前：环境变量与命令行参数
 * 仍可覆盖，而 profile 默认（如 personal 的 ddl-auto=none）被让位。既有库
 * （含表）完全不受影响；有业务表但无 Flyway 历史的旧库维持既有
 * baseline-on-migrate 兼容链并输出明确警告。
 *
 * <p>不支持的方言时不做任何事。"Flyway 关闭"仅对非全新库生效（测试/本地开发）；
 * 全新空库仍强制启用 Flyway 完成基线化（2026-10-20 修正：旧格式本地配置的
 * flyway.enabled=false 不再阻断全新库基线化）。
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
    public static final String FRESH_BASELINE_VERSION = "20261028001";

    private static final String SQLITE_URL_PREFIX = "jdbc:sqlite:";
    private static final String SQLSERVER_URL_PREFIX = "jdbc:sqlserver:";
    private static final String DM_URL_PREFIX = "jdbc:dm:";
    private static final String PROPERTY_SOURCE_NAME = "mdrFreshDatabase";
    private static final String HISTORY_TABLE = "flyway_schema_history";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        apply(applicationContext.getEnvironment());
    }

    /**
     * 空库识别与属性注入。单独抽出便于测试直接调用。
     *
     * <p>init-phase-database-free 之后分两种形态：
     * <ul>
     *   <li>总开关开启且数据源未就绪（向导配置数据库之前）：不做任何数据库探测
     *       （零数据库行为）；</li>
     *   <li>总开关开启且数据源就绪（完整相，或向导相已确认数据源）：全新库仅注入
     *       {@code app.bootstrap.fresh-database=true} 供数据库初始化引导建表，不再
     *       注入 ddl-auto/Flyway 基线化覆盖（建表与迁移由引导步骤负责，失败不得
     *       摧毁上下文）；</li>
     *   <li>总开关关闭（集成测试等自行管理 schema 的环境）：维持本类既有行为不变。</li>
     * </ul>
     */
    public void apply(ConfigurableEnvironment environment) {
        boolean bootstrapEnabled = environment.getProperty(
                BootstrapPhaseInitializer.PROPERTY_ENABLED, Boolean.class, false);
        boolean datasourceReady = environment.getProperty(
                BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY, Boolean.class, false);
        if (bootstrapEnabled && !datasourceReady) {
            // 零数据库向导段：不探测、不注入。
            return;
        }
        String url = environment.getProperty("spring.datasource.url");
        if (url == null) {
            return;
        }
        if (url.startsWith(SQLSERVER_URL_PREFIX)) {
            applyServerFresh(environment, "sqlserver", url, bootstrapEnabled);
            return;
        }
        if (url.startsWith(DM_URL_PREFIX)) {
            applyServerFresh(environment, "dm", url, bootstrapEnabled);
            return;
        }
        if (!url.startsWith(SQLITE_URL_PREFIX)) {
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
            if (bootstrapEnabled) {
                markFreshForBootstrap(environment, dbFile);
            } else {
                // 全新库必须启用 Flyway 完成基线化：即便旧格式本地配置显式关闭了 Flyway
                // （该关闭语义针对无 Flyway 历史的既有漂移库），全新空库不受其影响。
                applyFreshOverrides(environment, dbFile);
            }
            return;
        }
        if (bootstrapEnabled) {
            // 新架构：非全新库无需任何动作（迁移由引导负责）。
            return;
        }
        if (!environment.getProperty("spring.flyway.enabled", Boolean.class, true)) {
            // Flyway 关闭且非全新库（测试/本地开发）：无引导动作。
            return;
        }
        if (freshness == Freshness.LEGACY_UNHISTORIED) {
            log.warn("SQLite 库 {} 存在业务表但没有 Flyway 历史表：维持既有兼容链"
                            + "（baseline-on-migrate 按 application.yml 基线版本处理，重放带幂等守卫的迁移）。"
                            + "若启动失败说明该库来源无法自动识别，请人工核对迁移历史。",
                    dbFile);
        }
    }

    /** 完整相全新库：仅标记"待建表"，数据库结构由初始化引导创建。 */
    private void markFreshForBootstrap(ConfigurableEnvironment environment, Path dbFile) {
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put(BootstrapPhaseInitializer.PROPERTY_FRESH_DATABASE, "true");
        addSchemaScriptOverrides(overrides);
        environment.getPropertySources().addAfter(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
        log.info("检测到全新 SQLite 空库 {}：数据库结构将由初始化引导创建（Flyway 基线化由引导负责）",
                dbFile);
    }

    /**
     * 全新库建表 DDL 脚本生成属性：EMF 创建期把当前实体模型的建表 DDL 写入
     * schema-create.sql（JPA 标准脚本生成，只在"完整相全新库"时注入——
     * 避免向导相为生成脚本而建立数据库连接），数据库初始化引导在完整相
     * 全新库时读取执行。建表因此从 EMF 创建期移到引导期，失败仅记录原因，
     * 不会摧毁上下文。
     *
     * <p>Hibernate 写脚本文件是追加语义：多次完整相全新库启动会叠出重复
     * 建表语句（执行即 "table already exists"）。注入前先删除旧文件，
     * 保证脚本与当前实体模型一一对应。
     */
    private void addSchemaScriptOverrides(Map<String, Object> overrides) {
        java.nio.file.Path scriptPath = BootstrapPhaseInitializer.defaultSchemaScriptPath();
        try {
            Files.deleteIfExists(scriptPath);
        } catch (Exception e) {
            log.warn("无法删除旧建表脚本 {}: {}", scriptPath, e.getMessage());
        }
        overrides.put(BootstrapPhaseInitializer.PROPERTY_SCHEMA_SCRIPT, scriptPath.toString());
        overrides.put("spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action", "create");
        overrides.put("spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target",
                scriptPath.toString());
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
        // flyway.enabled 强制为 true：旧格式 application-local.yml 里用户显式关掉的
        // Flyway 只对既有库生效；全新库必须启用 Flyway 才能完成基线化。本属性源位于
        // 本地配置源之上（main() 中后注册），故覆盖生效。
        overrides.put("spring.flyway.enabled", "true");
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

    // ── SQL Server / 达梦 全新空库引导（baseline-data-init-rebuild）──────────

    /**
     * 服务端方言空库识别与引导：空库（无任何用户表）时预建 schema（仅 sqlserver）
     * 并注入 ddl-auto/Flyway 基线化覆盖；探测失败或既有库一律不动。
     */
    private void applyServerFresh(ConfigurableEnvironment environment, String dialect, String url,
                                  boolean bootstrapEnabled) {
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        Boolean hasUserTables = serverHasUserTables(dialect, url, username, password);
        if (hasUserTables == null) {
            // 探测失败不阻断启动：保持原行为，让连接池初始化给出原始诊断。
            return;
        }
        if (hasUserTables) {
            return;
        }
        if ("sqlserver".equals(dialect)) {
            // EMF 之前预建 schema（team profile 的 spring.datasource.schema 受
            // defer-datasource-initialization 影响要到 EMF 之后才执行，赶不上 ddl-auto；
            // 新架构下供初始化引导的建表步骤使用，见 markFreshForBootstrap 语义）
            List<String> schemas = parseSchemas(
                    environment.getProperty("spring.flyway.schemas", "mdr,ext"));
            createSqlServerSchemas(url, username, password, schemas);
        }
        if (bootstrapEnabled) {
            // 新架构：全新库仅标记"待建表"，ddl-auto/Flyway 覆盖不再注入
            // （建表与迁移由数据库初始化引导负责，失败不得摧毁上下文）。
            Map<String, Object> overrides = new LinkedHashMap<>();
            overrides.put(BootstrapPhaseInitializer.PROPERTY_FRESH_DATABASE, "true");
            addSchemaScriptOverrides(overrides);
            environment.getPropertySources().addAfter(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
            log.info("检测到全新 {} 空库 {}：数据库结构将由初始化引导创建", dialect, url);
            return;
        }
        String baselineVersion = maxMigrationVersion(dialect);
        if (baselineVersion == null) {
            log.warn("未在 classpath:db/migration/{} 扫描到任何迁移，回退共同基线 v{}",
                    dialect, FRESH_BASELINE_VERSION);
            baselineVersion = FRESH_BASELINE_VERSION;
        }
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("spring.flyway.enabled", "true");
        overrides.put("spring.jpa.hibernate.ddl-auto", "update");
        overrides.put("spring.flyway.baseline-on-migrate", "true");
        overrides.put("spring.flyway.baseline-version", baselineVersion);
        // flyway schemas/default-schema 维持 profile/本地配置，引导层不覆盖
        environment.getPropertySources().addAfter(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
        log.info("检测到全新 {} 空库 {}：启用 ORM 建表，Flyway 以当前最大迁移版本 v{} 基线化"
                        + "（遗留迁移整段跳过）。应用表将由当前实体模型创建。",
                dialect, url, baselineVersion);
    }

    /**
     * 探测库内是否存在任何用户表：SQL Server 查 mdr/ext/dbo 全域，达梦按当前用户模式。
     * 返回 null 表示探测失败（保持默认启动路径）。包级可见供测试注入假实现。
     */
    Boolean serverHasUserTables(String dialect, String url, String username, String password) {
        String driverClass = "sqlserver".equals(dialect)
                ? "com.microsoft.sqlserver.jdbc.SQLServerDriver"
                : "dm.jdbc.driver.DmDriver";
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            log.warn("未找到 {} JDBC 驱动，跳过空库识别: {}", dialect, e.getMessage());
            return null;
        }
        String sql = "sqlserver".equals(dialect)
                ? "SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id"
                        + " WHERE s.name IN ('mdr', 'ext', 'dbo')"
                : "SELECT COUNT(*) FROM USER_TABLES";
        try (Connection conn = openServerConnection(url, username, password);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            log.warn("{} 空库识别探测失败（保持默认启动路径）: {}", dialect, e.getMessage());
            return null;
        }
    }

    /**
     * SQL Server fresh：在 EMF 之前用裸 JDBC 预建 schema（IF NOT EXISTS 幂等）。
     * 仅在 fresh 判定后调用，对既有库无副作用。包级可见供测试注入假实现。
     */
    void createSqlServerSchemas(String url, String username, String password, List<String> schemas) {
        try (Connection conn = openServerConnection(url, username, password);
             Statement st = conn.createStatement()) {
            for (String schema : schemas) {
                st.execute("IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = '" + schema
                        + "') EXEC('CREATE SCHEMA " + schema + "')");
            }
            log.info("全新 SQL Server 库：已确保 schema 存在 {}", schemas);
        } catch (SQLException e) {
            throw new IllegalStateException("全新 SQL Server 库 schema 预建失败 " + schemas + "：" + e.getMessage(), e);
        }
    }

    private Connection openServerConnection(String url, String username, String password) throws SQLException {
        boolean hasCredentials = username != null && !username.isBlank();
        return hasCredentials
                ? DriverManager.getConnection(url, username, password == null ? "" : password)
                : DriverManager.getConnection(url);
    }

    private List<String> parseSchemas(String schemas) {
        List<String> result = new ArrayList<>();
        for (String part : schemas.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 扫描 {@code classpath*:db/migration/<方言>/V*.sql} 的最大版本号，作为 fresh 库
     * 基线版本（随迁移增加自动更新，避免硬编码过期）。目录为空/不存在返回 null。
     */
    static String maxMigrationVersion(String dialect) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources("classpath*:db/migration/" + dialect + "/V*.sql");
        } catch (IOException e) {
            return null;
        }
        long max = -1;
        for (Resource resource : resources) {
            String name = resource.getFilename();
            if (name == null) {
                continue;
            }
            int sep = name.indexOf("__");
            if (sep <= 1) {
                continue;
            }
            try {
                max = Math.max(max, Long.parseLong(name.substring(1, sep)));
            } catch (NumberFormatException ignored) {
                // 非标准命名不参与版本判定
            }
        }
        return max >= 0 ? Long.toString(max) : null;
    }
}




