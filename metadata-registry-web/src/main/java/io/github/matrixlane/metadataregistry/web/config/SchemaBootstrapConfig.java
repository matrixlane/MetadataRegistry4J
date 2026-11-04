package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

/**
 * 在 JPA ddl-auto 之前确保应用 schema 存在。
 *
 * <p>Spring Boot 会在任何 EntityManagerFactory 创建之前执行所有
 * {@link DataSourceInitializer}。SQL Server 的 {@code mdr} schema 历史上由
 * 手工步骤创建，新空库上不存在会导致 ddl-auto 建表失败。
 * SQLite 无 schema 概念；达梦通常以用户即模式（MDR 用户），无需创建。
 *
 * <p>init-phase-database-free 之后：数据源未就绪（向导配置数据库之前的零数据库段）
 * 连接池被引导机制指向一次性内存库，本 initializer 的方言 SQL 决不允许发往该池，
 * 故直接跳过；就绪后（完整相，或向导相已确认数据源）恢复执行——真实库的 schema
 * 预建仍须在 EMF 之前完成（全新 SQL Server 库上引导建表依赖 schema 已存在）。
 * 总开关关闭（集成测试等自行管理 schema 的环境）时维持本类既有行为不变。
 */
@Configuration
public class SchemaBootstrapConfig {

    @Bean
    public DataSourceInitializer mdrSchemaInitializer(DataSource dataSource, DatabaseEnvironment env,
                                                      Environment springEnvironment) {
        boolean bootstrapEnabled = springEnvironment.getProperty(
                BootstrapPhaseInitializer.PROPERTY_ENABLED, Boolean.class, false);
        boolean datasourceReady = springEnvironment.getProperty(
                BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY, Boolean.class, false);
        if (bootstrapEnabled && !datasourceReady) {
            // 零数据库向导段：池为内存库，跳过方言 SQL
            return null;
        }
        String schema = env.schema();
        if (env.vendor() != DatabaseVendor.SQLSERVER || schema == null) {
            // SQLite / 达梦：无需预建 schema
            return null;
        }
        String sql = "IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = '" + schema + "')"
                + " EXEC('CREATE SCHEMA " + schema + "')";
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }
}
