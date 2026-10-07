package io.github.matrixlane.metadataregistry.web.service.database;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Flyway schema 历史表归位器（见 openspec: relocate-flyway-history-to-ext）。
 *
 * <p>flyway_schema_history 是 Flyway 工具表，不属于 ISO/IEC 11179 标准表，
 * 必须与标准表分离、归 ext schema 管理。本组件在 Flyway 执行任何迁移探测
 * 之前运行（由 FlywayHistoryRelocationConfig 固定顺序，目标 bean 为
 * {@code flywayInitializer}，经类型查找而非字面量名），把仍留在主 schema
 * 的历史表整体迁入 Flyway 实际查找历史表的位置：
 *
 * <ul>
 *   <li>仅 SQL Server 方言且 Flyway 启用时执行；SQLite 无 schema 概念、
 *       达梦用户即模式，均 no-op；</li>
 *   <li>目标 schema 取自 Flyway 自身解析结果（{@code defaultSchema}，未设时
 *       取 {@code schemas[0]}），与 {@code spring.flyway} 配置永不分叉——
 *       搬去的地点就是 Flyway 要去找的地点；</li>
 *   <li>仅当"目标处无历史表 + 主 schema 有历史表"时执行
 *       {@code ALTER SCHEMA <目标> TRANSFER <主>.<表>}（历史行、索引、约束
 *       整体搬迁，与 V20260901014 同款手法）；</li>
 *   <li>搬迁 DDL 前显式 {@code setAutoCommit(true)} 并恢复原值——连接池
 *       统一 {@code auto-commit: false}（application.yml），不显式提交会被
 *       连接池在 close 时按脏状态回滚，导致"日志报成功、实际未搬迁"；</li>
 *   <li>目标 schema 不存在时 fail fast：这是唯一会把 Flyway 送进
 *       re-baseline（重放全部基线后迁移）的状态，与本组件的存在目的矛盾，
 *       必须中断启动交人工处置；</li>
 *   <li>两表并存（异常态）跳过并 WARN（附处置 SQL），不自动合并行数据。</li>
 * </ul>
 */
@Slf4j
@Component
public class FlywayHistoryRelocator {

    private final DataSource dataSource;
    private final DatabaseEnvironment env;
    private final Flyway flyway;

    public FlywayHistoryRelocator(DataSource dataSource,
                                  DatabaseEnvironment env,
                                  ObjectProvider<Flyway> flywayProvider) {
        this.dataSource = dataSource;
        this.env = env;
        this.flyway = flywayProvider.getIfAvailable();
    }

    @PostConstruct
    void relocate() {
        if (env.vendor() != DatabaseVendor.SQLSERVER) {
            log.debug("flyway-history-relocator: {} 方言无 mdr/ext schema 之分,跳过", env.vendor());
            return;
        }
        if (flyway == null) {
            log.debug("flyway-history-relocator: Flyway 未启用,跳过");
            return;
        }
        Connection conn = null;
        try {
            conn = DataSourceUtils.getConnection(dataSource);
            relocate(conn);
        } catch (IllegalStateException e) {
            throw e;
        } catch (SQLException | RuntimeException e) {
            throw new IllegalStateException(
                    "搬迁 flyway_schema_history 失败"
                            + "(为避免 Flyway 在缺历史表状态下 re-baseline,必须中断启动): " + e.getMessage(), e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    private void relocate(Connection conn) throws SQLException {
        Configuration configuration = flyway.getConfiguration();
        String table = configuration.getTable();
        String targetSchema = configuration.getDefaultSchema();
        if (targetSchema == null) {
            String[] schemas = configuration.getSchemas();
            targetSchema = schemas.length > 0 ? schemas[0] : null;
        }
        if (targetSchema == null) {
            log.debug("flyway-history-relocator: Flyway 未配置 default-schema/schemas,跳过");
            return;
        }
        String sourceSchema = env.schema();
        String catalog = conn.getCatalog();
        DatabaseMetaData md = conn.getMetaData();
        boolean inTarget = tableExists(md, catalog, targetSchema, table);
        boolean inSource = tableExists(md, catalog, sourceSchema, table);
        if (inTarget && inSource) {
            log.warn("flyway-history-relocator: {}.{} 与 {}.{} 并存,跳过自动搬迁;"
                            + "请人工核对后以 Flyway 实际使用的 {}.{} 为准,处置示例:"
                            + " ALTER SCHEMA {} TRANSFER {}.{}(保留目标侧)或备份后 DROP {}",
                    targetSchema, table, sourceSchema, table,
                    targetSchema, table, targetSchema, sourceSchema, table, targetSchema, table);
            return;
        }
        if (inTarget) {
            log.debug("flyway-history-relocator: {}.{} 已存在,无需搬迁", targetSchema, table);
            return;
        }
        if (!inSource) {
            log.debug("flyway-history-relocator: 主 schema 无 {},全新库将由 Flyway 直接创建", table);
            return;
        }
        if (!schemaExists(md, catalog, targetSchema)) {
            throw new IllegalStateException("flyway-history-relocator: 目标 schema ["
                    + targetSchema + "] 不存在,若继续启动 Flyway 将 re-baseline 并重放全部基线后迁移;"
                    + "请先创建该 schema(团队库通常由 db/schema-init.sql 负责)再启动");
        }
        transfer(conn, targetSchema, sourceSchema, table);
    }

    private void transfer(Connection conn, String targetSchema, String sourceSchema, String table)
            throws SQLException {
        // 与 DatabaseEnvironment.qualify() 同款双引号限定
        String transfer = "ALTER SCHEMA " + quote(targetSchema)
                + " TRANSFER " + quote(sourceSchema) + "." + quote(table);
        boolean restoreAutoCommit = false;
        try {
            if (!conn.getAutoCommit()) {
                conn.setAutoCommit(true);
                restoreAutoCommit = true;
            }
            log.info("flyway-history-relocator: 历史表归位 {} → {}", targetSchema, transfer);
            try (Statement st = conn.createStatement()) {
                st.execute(transfer);
            }
        } finally {
            if (restoreAutoCommit) {
                conn.setAutoCommit(false);
            }
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier + "\"";
    }

    private boolean tableExists(DatabaseMetaData md, String catalog, String schema, String table)
            throws SQLException {
        try (ResultSet rs = md.getTables(catalog, schema, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private boolean schemaExists(DatabaseMetaData md, String catalog, String schema) throws SQLException {
        try (ResultSet rs = md.getSchemas(catalog, schema)) {
            return rs.next();
        }
    }
}
