package io.github.matrixlane.metadataregistry.web.service.schemaextraction;

import io.github.matrixlane.metadataregistry.schemaextraction.audit.SchemaExtractionAuditEvent;
import io.github.matrixlane.metadataregistry.schemaextraction.audit.SchemaExtractionAuditLogger;
import io.github.matrixlane.metadataregistry.schemaextraction.connection.ConnectionEncryption;
import io.github.matrixlane.metadataregistry.schemaextraction.connection.DatabaseConnectionConfig;
import io.github.matrixlane.metadataregistry.schemaextraction.connection.DatabaseConnectionManager;
import io.github.matrixlane.metadataregistry.schemaextraction.mapping.*;
import io.github.matrixlane.metadataregistry.schemaextraction.model.DatabaseSchema;
import io.github.matrixlane.metadataregistry.schemaextraction.strategy.SchemaExtractor;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineContextEntry;
import io.github.matrixlane.metadataregistry.web.dto.concept.ConceptRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementRequest;
import io.github.matrixlane.metadataregistry.web.dto.dec.DataElementConceptRequest;
import io.github.matrixlane.metadataregistry.web.dto.schemaextraction.*;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.ValueDomainRequest;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.ValueDomainType;
import io.github.matrixlane.metadataregistry.web.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Application service for database schema extraction workflow.
 * Orchestrates connection management, extraction, mapping, review, and import.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaExtractionApplicationService {

    private final DatabaseConnectionManager connectionManager;
    private final ConnectionEncryption connectionEncryption;
    private final SchemaExtractor schemaExtractor;
    private final SchemaMappingService schemaMappingService;
    private final SchemaExtractionAuditLogger auditLogger;

    private final ObjectClassService objectClassService;
    private final PropertyService propertyService;
    private final DataElementConceptService dataElementConceptService;
    private final ValueDomainService valueDomainService;
    private final DataElementService dataElementService;

    @Value("${schema.extraction.default-language-id:}")
    private String defaultLanguageIdStr;

    @Value("${schema.extraction.default-context-id:}")
    private String defaultContextIdStr;

    @Value("${schema.extraction.default-registration-authority-id:}")
    private String defaultRegistrationAuthorityIdStr;

    @Value("${schema.extraction.default-namespace-id:}")
    private String defaultNamespaceIdStr;

    // In-memory session storage for review workflow
    private final Map<UUID, ExtractionSession> sessions = new ConcurrentHashMap<>();

    // ==================== Connection Management ====================

    public void registerConnection(DatabaseConnectionRequest request) {
        String encryptedPassword = connectionEncryption.encrypt(request.password());

        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .connectionName(request.connectionName())
                .jdbcUrl(request.jdbcUrl())
                .username(request.username())
                .password(encryptedPassword)
                .driverClassName(request.driverClassName())
                .connectionTimeout(request.connectionTimeout() != null ? request.connectionTimeout() : 3000)
                .maximumPoolSize(request.maximumPoolSize() != null ? request.maximumPoolSize() : 5)
                .minimumIdle(request.minimumIdle() != null ? request.minimumIdle() : 1)
                .idleTimeout(request.idleTimeout() != null ? request.idleTimeout() : 600000L)
                .maxLifetime(request.maxLifetime() != null ? request.maxLifetime() : 1800000L)
                .additionalProperties(request.additionalProperties() != null ? request.additionalProperties() : new Properties())
                .build();

        connectionManager.registerConnection(config);
    }

    public void removeConnection(String connectionName) {
        connectionManager.removeConnection(connectionName);
    }

    public boolean testConnection(String connectionName) {
        return connectionManager.testConnection(connectionName);
    }

    public List<DatabaseConnectionResponse> listConnections() {
        // DatabaseConnectionManager doesn't expose list, but we can track separately if needed.
        // For now, return empty as the internal map is not exposed.
        return Collections.emptyList();
    }

    // ==================== Schema Extraction ====================

    public ExtractionPreviewResponse extractAndPreview(String connectionName, SchemaExtractionRequest request) {
        long startTime = System.currentTimeMillis();
        String jdbcUrl = null;
        String dbType = null;
        int tableCount = 0;

        try (Connection connection = connectionManager.getConnection(connectionName)) {
            jdbcUrl = connection.getMetaData().getURL();
            dbType = connection.getMetaData().getDatabaseProductName();

            DatabaseSchema schema = schemaExtractor.extract(connection, request.catalog(), request.schema());
            tableCount = schema.getTables().size();

            SchemaMappingResult mapping = schemaMappingService.mapSchema(schema);

            UUID sessionId = UUID.randomUUID();
            sessions.put(sessionId, new ExtractionSession(sessionId, connectionName, mapping));

            long duration = System.currentTimeMillis() - startTime;
            logAudit(connectionName, jdbcUrl, dbType, "EXTRACT", "SUCCESS",
                    "Extracted " + tableCount + " tables", tableCount, duration);

            return toPreviewResponse(sessionId, schema, mapping);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logAudit(connectionName, jdbcUrl, dbType, "EXTRACT", "FAILED",
                    e.getMessage(), tableCount, duration);
            throw new BusinessException(
                    "Schema extraction failed: " + e.getMessage());
        }
    }

    public ExtractionPreviewResponse getPreview(UUID sessionId) {
        ExtractionSession session = sessions.get(sessionId);
        if (session == null) {
            throw new BusinessException("Extraction session not found: " + sessionId);
        }
        return toPreviewResponse(sessionId, null, session.mappingResult);
    }

    // ==================== Review & Import ====================

    @Transactional
    public SchemaImportResponse importSelected(SchemaImportRequest request) {
        ExtractionSession session = sessions.get(request.sessionId());
        if (session == null) {
            throw new BusinessException("Extraction session not found: " + request.sessionId());
        }

        SchemaMappingResult mapping = session.mappingResult;
        List<String> errors = new ArrayList<>();
        int importedDec = 0;
        int importedDe = 0;
        int importedVd = 0;

        // Import ValueDomains first (needed by DataElements)
        Map<String, UUID> vdNameToId = new HashMap<>();
        for (MappedValueDomain vd : mapping.getValueDomains()) {
            if (request.importAll() || (request.selectedVdNames() != null && request.selectedVdNames().contains(vd.getName()))) {
                try {
                    UUID id = importValueDomain(vd);
                    vdNameToId.put(vd.getName(), id);
                    importedVd++;
                } catch (Exception e) {
                    errors.add("ValueDomain " + vd.getName() + ": " + e.getMessage());
                }
            }
        }

        // Import DataElementConcepts (and underlying ObjectClass + Property)
        Map<String, UUID> decNameToId = new HashMap<>();
        for (MappedDataElementConcept dec : mapping.getDataElementConcepts()) {
            if (request.importAll() || (request.selectedDecNames() != null && request.selectedDecNames().contains(dec.getName()))) {
                try {
                    UUID id = importDataElementConcept(dec);
                    decNameToId.put(dec.getName(), id);
                    importedDec++;
                } catch (Exception e) {
                    errors.add("DEC " + dec.getName() + ": " + e.getMessage());
                }
            }
        }

        // Import DataElements
        for (MappedDataElement de : mapping.getDataElements()) {
            if (request.importAll() || (request.selectedDeNames() != null && request.selectedDeNames().contains(buildDeKey(de)))) {
                try {
                    UUID decId = decNameToId.get(de.getDataElementConcept().getName());
                    UUID vdId = vdNameToId.get(de.getValueDomain().getName());
                    if (decId == null) {
                        errors.add("DE " + de.getName() + ": DataElementConcept not found");
                        continue;
                    }
                    importDataElement(de, decId, vdId);
                    importedDe++;
                } catch (Exception e) {
                    errors.add("DE " + de.getName() + ": " + e.getMessage());
                }
            }
        }

        return new SchemaImportResponse(request.sessionId(), importedDec, importedDe, importedVd, errors);
    }

    // ==================== Private Helpers ====================

    private UUID importValueDomain(MappedValueDomain vd) {
        List<TextLine> designations = List.of(createTextLine(vd.getName()));
        List<TextLine> definitions = vd.getDescription() != null
                ? List.of(createTextLine(vd.getDescription()))
                : Collections.emptyList();

        ValueDomainRequest request = new ValueDomainRequest(
                ValueDomainType.DESCRIBED,
                null, // dataTypeId - would need mapping
                null, // unitOfMeasureId
                vd.getMaximumCharacterQuantity(),
                null, // format
                null, null, // validPeriod
                null, // describedConceptualDomainId
                null, // permissibleValues
                designations,
                definitions,
                createDefaultRegistration()
        );

        return valueDomainService.create(request).id();
    }

    private UUID importDataElementConcept(MappedDataElementConcept dec) {
        // Create ObjectClass
        List<TextLine> ocDesignations = List.of(createTextLine(dec.getObjectClassName()));
        ConceptRequest ocRequest = new ConceptRequest(
                null, ocDesignations, Collections.emptyList(), createDefaultRegistration()
        );
        UUID ocId = objectClassService.create(ocRequest).id();

        // Create or reuse Property "record"
        UUID propId = findOrCreateProperty(dec.getPropertyName());

        List<TextLine> decDesignations = List.of(createTextLine(dec.getName()));
        List<TextLine> decDefinitions = dec.getDescription() != null
                ? List.of(createTextLine(dec.getDescription()))
                : Collections.emptyList();

        DataElementConceptRequest request = new DataElementConceptRequest(
                ocId, propId, null, decDesignations, decDefinitions, createDefaultRegistration()
        );

        return dataElementConceptService.create(request).id();
    }

    private UUID findOrCreateProperty(String propertyName) {
        // For simplicity, always create a new property.
        // In production, you'd search for an existing one first.
        List<TextLine> designations = List.of(createTextLine(propertyName));
        ConceptRequest request = new ConceptRequest(
                null, designations, Collections.emptyList(), createDefaultRegistration()
        );
        return propertyService.create(request).id();
    }

    private void importDataElement(MappedDataElement de, UUID decId, UUID vdId) {
        List<TextLine> designations = List.of(createTextLine(de.getName()));
        List<TextLine> definitions = de.getDescription() != null
                ? List.of(createTextLine(de.getDescription()))
                : Collections.emptyList();

        DataElementRequest request = new DataElementRequest(
                decId, vdId, designations, definitions, createDefaultRegistration()
        );

        dataElementService.create(request);
    }

    private TextLine createTextLine(String value) {
        UUID langId = parseUuid(defaultLanguageIdStr);
        UUID ctxId = parseUuid(defaultContextIdStr);

        List<TextLineContextEntry> contexts = ctxId != null
                ? List.of(new TextLineContextEntry(ctxId,
                io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability.PREFERRED))
                : Collections.emptyList();

        return new TextLine(langId != null ? langId : UUID.randomUUID(), value, null, contexts);
    }

    private ItemRegistrationRequest createDefaultRegistration() {
        UUID raId = parseUuid(defaultRegistrationAuthorityIdStr);
        UUID nsId = parseUuid(defaultNamespaceIdStr);
        List<TextLine> designations = List.of(createTextLine("Auto-generated"));
        return new ItemRegistrationRequest(
                nsId,
                UUID.randomUUID().toString().substring(0, 8),
                "1.0",
                raId,
                designations,
                Collections.emptyList()
        );
    }

    private UUID parseUuid(String str) {
        if (str == null || str.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String buildDeKey(MappedDataElement de) {
        return de.getTableName() + "." + de.getColumnName();
    }

    private void logAudit(String connectionName, String jdbcUrl, String dbType,
                          String operation, String status, String details,
                          int tableCount, long durationMs) {
        try {
            SchemaExtractionAuditEvent event = SchemaExtractionAuditEvent.builder()
                    .timestamp(Instant.now())
                    .connectionName(connectionName)
                    .jdbcUrl(jdbcUrl)
                    .databaseType(dbType)
                    .operation(operation)
                    .status(status)
                    .details(details)
                    .username(currentUser())
                    .tableCount(tableCount)
                    .durationMs(durationMs)
                    .build();
            auditLogger.log(event);
        } catch (Exception e) {
            log.warn("Failed to log audit event", e);
        }
    }

    private String currentUser() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth.getName() : "system";
    }

    private ExtractionPreviewResponse toPreviewResponse(UUID sessionId, DatabaseSchema schema,
                                                         SchemaMappingResult mapping) {
        int tableCount = schema != null ? schema.getTables().size() : 0;
        int columnCount = schema != null
                ? schema.getTables().stream().mapToInt(t -> t.getColumns().size()).sum()
                : 0;

        return new ExtractionPreviewResponse(
                sessionId,
                mapping.getDatabaseProductName(),
                mapping.getCatalog(),
                mapping.getSchema(),
                tableCount,
                columnCount,
                mapping.getDataElementConcepts().stream()
                        .map(d -> new ExtractionPreviewResponse.MappedDataElementConceptPreview(
                                d.getName(), d.getDescription(), d.getTableName(),
                                d.getObjectClassName(), d.getPropertyName()))
                        .collect(Collectors.toList()),
                mapping.getDataElements().stream()
                        .map(d -> new ExtractionPreviewResponse.MappedDataElementPreview(
                                d.getName(), d.getDescription(), d.getTableName(), d.getColumnName(),
                                d.isNullable(), d.isPrimaryKey(), d.isAutoIncrement(),
                                d.getValueDomain() != null ? d.getValueDomain().getDataTypeName() : null,
                                d.getValueDomain() != null ? d.getValueDomain().getMaximumCharacterQuantity() : null,
                                d.getValueDomain() != null ? d.getValueDomain().getDecimalScale() : null,
                                d.getValueDomain() != null ? d.getValueDomain().getName() : null,
                                d.getDataElementConcept() != null ? d.getDataElementConcept().getName() : null))
                        .collect(Collectors.toList()),
                mapping.getValueDomains().stream()
                        .map(v -> new ExtractionPreviewResponse.MappedValueDomainPreview(
                                v.getName(), v.getDescription(), v.getDataTypeName(),
                                v.getMaximumCharacterQuantity(), v.getDecimalScale()))
                        .collect(Collectors.toList()),
                mapping.getRelationships().stream()
                        .map(r -> new ExtractionPreviewResponse.MappedRelationshipPreview(
                                r.getSourceTable(), r.getSourceColumn(),
                                r.getTargetTable(), r.getTargetColumn(), r.getConstraintName()))
                        .collect(Collectors.toList())
        );
    }

    // ==================== Inner Class ====================

    private record ExtractionSession(UUID sessionId, String connectionName,
                                      SchemaMappingResult mappingResult) {
    }
}
