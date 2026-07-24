package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 */
@Configuration
public class SchemaBootstrapConfig {

    @Bean
    public DataSourceInitializer mdrSchemaInitializer(DataSource dataSource, DatabaseEnvironment env) {
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
