package io.github.matrixlane.metadataregistry.schemaextraction.config;

import io.github.matrixlane.metadataregistry.schemaextraction.SchemaExtractionFactory;
import io.github.matrixlane.metadataregistry.schemaextraction.SchemaExtractionOrchestrationService;
import io.github.matrixlane.metadataregistry.schemaextraction.audit.SchemaExtractionAuditLogger;
import io.github.matrixlane.metadataregistry.schemaextraction.connection.ConnectionEncryption;
import io.github.matrixlane.metadataregistry.schemaextraction.connection.DatabaseConnectionManager;
import io.github.matrixlane.metadataregistry.schemaextraction.mapping.AdministeredItemMappingService;
import io.github.matrixlane.metadataregistry.schemaextraction.mapping.SchemaMappingService;
import io.github.matrixlane.metadataregistry.schemaextraction.strategy.SchemaExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for schema extraction components.
 */
@Configuration
public class SchemaExtractionAutoConfiguration {

    @Value("${schema.extraction.master-key:default-master-key-change-me}")
    private String masterKey;

    @Bean
    @ConditionalOnMissingBean
    public SchemaExtractor schemaExtractor() {
        return SchemaExtractionFactory.createSchemaExtractor();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectionEncryption connectionEncryption() {
        return SchemaExtractionFactory.createEncryption(masterKey);
    }

    @Bean
    @ConditionalOnMissingBean
    public DatabaseConnectionManager databaseConnectionManager(ConnectionEncryption encryption) {
        return SchemaExtractionFactory.createConnectionManager(masterKey);
    }

    @Bean
    @ConditionalOnMissingBean
    public SchemaMappingService schemaMappingService() {
        return new SchemaMappingService();
    }

    @Bean
    @ConditionalOnMissingBean
    public SchemaExtractionAuditLogger schemaExtractionAuditLogger() {
        return new SchemaExtractionAuditLogger();
    }

    @Bean
    @ConditionalOnMissingBean
    public AdministeredItemMappingService administeredItemMappingService() {
        return new AdministeredItemMappingService();
    }

    @Bean
    @ConditionalOnMissingBean
    public SchemaExtractionOrchestrationService schemaExtractionOrchestrationService(
            DatabaseConnectionManager connectionManager,
            SchemaExtractor schemaExtractor,
            SchemaMappingService schemaMappingService,
            SchemaExtractionAuditLogger auditLogger) {
        return new SchemaExtractionOrchestrationService(connectionManager, schemaExtractor, schemaMappingService, auditLogger);
    }
}
