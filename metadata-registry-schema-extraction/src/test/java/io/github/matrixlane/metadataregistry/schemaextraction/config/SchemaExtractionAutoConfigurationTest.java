package io.github.matrixlane.metadataregistry.schemaextraction.config;

import io.github.matrixlane.metadataregistry.schemaextraction.SchemaExtractionOrchestrationService;
import io.github.matrixlane.metadataregistry.schemaextraction.audit.SchemaExtractionAuditLogger;
import io.github.matrixlane.metadataregistry.schemaextraction.connection.ConnectionEncryption;
import io.github.matrixlane.metadataregistry.schemaextraction.connection.DatabaseConnectionManager;
import io.github.matrixlane.metadataregistry.schemaextraction.mapping.AdministeredItemMappingService;
import io.github.matrixlane.metadataregistry.schemaextraction.mapping.SchemaMappingService;
import io.github.matrixlane.metadataregistry.schemaextraction.strategy.SchemaExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.*;

class SchemaExtractionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SchemaExtractionAutoConfiguration.class));

    @Test
    void shouldAutoConfigureSchemaExtractor() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SchemaExtractor.class);
            assertThat(context.getBean(SchemaExtractor.class)).isNotNull();
        });
    }

    @Test
    void shouldAutoConfigureConnectionEncryption() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ConnectionEncryption.class);
            assertThat(context.getBean(ConnectionEncryption.class)).isNotNull();
        });
    }

    @Test
    void shouldAutoConfigureDatabaseConnectionManager() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DatabaseConnectionManager.class);
            assertThat(context.getBean(DatabaseConnectionManager.class)).isNotNull();
        });
    }

    @Test
    void shouldAutoConfigureSchemaMappingService() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SchemaMappingService.class);
            assertThat(context.getBean(SchemaMappingService.class)).isNotNull();
        });
    }

    @Test
    void shouldAutoConfigureAuditLogger() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SchemaExtractionAuditLogger.class);
            assertThat(context.getBean(SchemaExtractionAuditLogger.class)).isNotNull();
        });
    }

    @Test
    void shouldAutoConfigureAdministeredItemMappingService() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AdministeredItemMappingService.class);
            assertThat(context.getBean(AdministeredItemMappingService.class)).isNotNull();
        });
    }

    @Test
    void shouldAutoConfigureOrchestrationService() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SchemaExtractionOrchestrationService.class);
            assertThat(context.getBean(SchemaExtractionOrchestrationService.class)).isNotNull();
        });
    }

    @Test
    void shouldNotAutoConfigureWhenSchemaExtractorBeanAlreadyExists() {
        this.contextRunner
                .withBean(SchemaExtractor.class, () -> {
                    // Provide a dummy bean
                    return new SchemaExtractor(java.util.Collections.emptyList());
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(SchemaExtractor.class);
                });
    }
}
