package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * SchemaBootstrapConfig 的相位守卫（init-phase-database-free）：
 * 向导相连接池被引导机制指向一次性内存库，schema 预建 SQL 决不允许发往该池。
 */
class SchemaBootstrapConfigTest {

    private final SchemaBootstrapConfig config = new SchemaBootstrapConfig();
    private final DataSource dataSource = mock(DataSource.class);

    private DatabaseEnvironment sqlServerEnv() {
        return new DatabaseEnvironment(
                "sqlserver", "jdbc:sqlserver://localhost:1433;databaseName=x", "mdr", "ext");
    }

    @Test
    void 数据源未就绪且引导开启时跳过schema预建() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(BootstrapPhaseInitializer.PROPERTY_ENABLED, "true")
                .withProperty(BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY, "false");

        DataSourceInitializer bean = config.mdrSchemaInitializer(dataSource, sqlServerEnv(), env);

        assertThat(bean).isNull();
    }

    @Test
    void 数据源就绪且引导开启时保留schema预建() {
        MockEnvironment env = new MockEnvironment()
                .withProperty(BootstrapPhaseInitializer.PROPERTY_ENABLED, "true")
                .withProperty(BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY, "true");

        assertThat(config.mdrSchemaInitializer(dataSource, sqlServerEnv(), env)).isNotNull();
    }

    @Test
    void 引导关闭时维持既有行为不受影响() {
        MockEnvironment env = new MockEnvironment();

        assertThat(config.mdrSchemaInitializer(dataSource, sqlServerEnv(), env)).isNotNull();
        // SQLite 无 schema 概念，依旧跳过
        DatabaseEnvironment sqlite = new DatabaseEnvironment("", "jdbc:sqlite:/x.db", "", "");
        assertThat(config.mdrSchemaInitializer(dataSource, sqlite, env)).isNull();
    }
}
