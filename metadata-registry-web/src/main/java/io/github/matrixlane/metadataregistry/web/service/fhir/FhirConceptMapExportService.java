package io.github.matrixlane.metadataregistry.web.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirConceptMapExportResult;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirValidationError;
import io.github.matrixlane.metadataregistry.web.entity.ExportTask;
import io.github.matrixlane.metadataregistry.web.entity.ExportTaskStatus;
import io.github.matrixlane.metadataregistry.web.repository.ExportTaskRepository;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.r4.model.ConceptMap.SourceElementComponent;
import org.hl7.fhir.r4.model.ConceptMap.TargetElementComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for exporting mapping relationships as FHIR R4 ConceptMap resources.
 *
 * <p>Provides single-scheme and bulk export, synchronous and asynchronous modes,
 * and built-in FHIR R4 compliance validation.
 *
 * <p>Per WI-011-2-2 — FHIR ConceptMap Export.
 */
@Service
public class FhirConceptMapExportService {

    private static final Logger log = LoggerFactory.getLogger(FhirConceptMapExportService.class);

    // ---- Constants ----
    static final int ASYNC_THRESHOLD = 500;
    static final String ALL_SCHEMES = "all";

    // ---- Dependencies ----
    private final ItemMappingRepository mappingRepository;
    private final ExportTaskRepository exportTaskRepository;

    // ---- FHIR Context (thread-safe, reusable) ----
    private final FhirContext fhirContext = FhirContext.forR4();
    private FhirValidator validator;

    @Value("${app.export.fhir.output-dir:./exports/fhir}")
    private String outputDir;

    /**
     * Constructor injection for dependencies.
     * Note: Validator is lazily initialized via getValidator().
     */
    public FhirConceptMapExportService(
            ItemMappingRepository mappingRepository,
            ExportTaskRepository exportTaskRepository) {
        this.mappingRepository = Objects.requireNonNull(mappingRepository, "mappingRepository must not be null");
        this.exportTaskRepository = Objects.requireNonNull(exportTaskRepository, "exportTaskRepository must not be null");
    }

    /**
     * Returns the FHIR validator, initializing it lazily if needed.
     * Registers the FHIR instance validator with R4 validation support.
     */
    private FhirValidator getValidator() {
        if (validator == null) {
            this.validator = fhirContext.newValidator();

            org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain supportChain =
                    new org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain(
                            new ca.uhn.fhir.context.support.DefaultProfileValidationSupport(fhirContext)
                    );

            org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator instanceValidator =
                    new org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator(supportChain);
            this.validator.registerValidatorModule(instanceValidator);
        }
        return validator;
    }

    // ========================================================================
    // Public API
    // ========================================================================

    /**
     * Exports mappings as a FHIR ConceptMap.
     *
     * @param schemeId the subject item UUID, or "all" for all mappings
     * @param userId   the ID of the requesting user (for async task tracking)
     * @return export result (sync or async-submitted)
     */
    @Transactional(readOnly = true)
    public FhirConceptMapExportResult exportConceptMap(String schemeId, UUID userId) {
        List<ItemMapping> mappings = fetchMappings(schemeId);
        if (mappings.isEmpty()) {
            return FhirConceptMapExportResult.failure(
                    "No mappings found for schemeId: " + schemeId);
        }

        if (mappings.size() > ASYNC_THRESHOLD) {
            log.info("Mapping count ({}) exceeds async threshold ({}), " +
                     "creating async export task", mappings.size(), ASYNC_THRESHOLD);
            return submitAsyncExport(schemeId, userId, mappings.size());
        }

        return doSyncExport(mappings, schemeId);
    }

    /**
     * Returns the status of an async export task.
     *
     * @param taskId the export task UUID
     * @return current export result with progress info
     */
    @Transactional(readOnly = true)
    public FhirConceptMapExportResult getExportTaskStatus(UUID taskId) {
        ExportTask task = exportTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return FhirConceptMapExportResult.failure("Export task not found: " + taskId);
        }

        return switch (task.getStatus()) {
            case COMPLETED -> {
                String json = readExportFile(task.getResultFilePath());
                if (json == null) {
                    yield FhirConceptMapExportResult.failure(
                            "Export result file not found for task: " + taskId);
                }
                yield new FhirConceptMapExportResult(
                        taskId, true, json, true, List.of(),
                        task.getProcessedCount(), task.getProcessedCount(),
                        java.time.OffsetDateTime.now(), null, true
                );
            }
            case FAILED ->
                    new FhirConceptMapExportResult(
                            taskId, false, null, false, List.of(),
                            0, 0, java.time.OffsetDateTime.now(),
                            task.getErrorMessage(), true
                    );
            default ->
                    new FhirConceptMapExportResult(
                            taskId, true, null, false, null,
                            task.getProcessedCount(), task.getTotalCount(),
                            java.time.OffsetDateTime.now(), null, true
                    );
        };
    }

    // ========================================================================
    // Internal: Fetching
    // ========================================================================

    List<ItemMapping> fetchMappings(String schemeId) {
        if (ALL_SCHEMES.equalsIgnoreCase(schemeId)) {
            return mappingRepository.findAll();
        }

        UUID subjectItemId;
        try {
            subjectItemId = UUID.fromString(schemeId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid schemeId format, must be UUID or 'all': {}", schemeId);
            return List.of();
        }

        return mappingRepository.findBySubjectItemId(subjectItemId);
    }

    // ========================================================================
    // Internal: Synchronous Export
    // ========================================================================

    FhirConceptMapExportResult doSyncExport(List<ItemMapping> mappings, String schemeId) {
        try {
            ConceptMap conceptMap = buildConceptMap(mappings, schemeId);

            IParser jsonParser = fhirContext.newJsonParser();
            jsonParser.setPrettyPrint(true);
            String conceptMapJson = jsonParser.encodeResourceToString(conceptMap);

            List<FhirValidationError> validationErrors = validateConceptMap(conceptMap);
            int elementCount = countElements(conceptMap);

            return FhirConceptMapExportResult.syncSuccess(
                    conceptMapJson,
                    validationErrors.isEmpty(),
                    validationErrors,
                    mappings.size(),
                    elementCount
            );
        } catch (Exception e) {
            log.error("Failed to export ConceptMap for schemeId: {}", schemeId, e);
            return FhirConceptMapExportResult.failure(
                    "Export failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // Internal: FHIR ConceptMap Builder
    // ========================================================================

    ConceptMap buildConceptMap(List<ItemMapping> mappings, String schemeId) {
        ConceptMap conceptMap = new ConceptMap();

        conceptMap.setId(schemeId);
        conceptMap.setUrl("urn:uuid:" + schemeId);
        conceptMap.setName("MappingScheme_" + sanitizeName(schemeId));
        conceptMap.setTitle("Mapping Export - " + schemeId);
        conceptMap.setStatus(Enumerations.PublicationStatus.DRAFT);
        conceptMap.setVersion("1.0");
        conceptMap.setDescription("ISO/IEC 11179 Item Mapping export as FHIR ConceptMap");

        List<GroupKey> groups = groupMappings(mappings);

        for (GroupKey groupKey : groups) {
            ConceptMapGroupComponent group = conceptMap.addGroup();
            group.setSource(groupKey.sourceUri());
            group.setTarget(groupKey.targetUri());

            for (ItemMapping mapping : groupKey.mappings()) {
                SourceElementComponent element = group.addElement();
                element.setCode(resolveSourceCode(mapping.getSubject()));

                TargetElementComponent target = element.addTarget();
                target.setCode(resolveTargetCode(mapping.getObject()));
                target.setEquivalence(
                        FhirEquivalenceMapper.toFhirEquivalence(mapping.getMappingType()));

                if (mapping.getDescription() != null && !mapping.getDescription().isBlank()) {
                    target.setComment(mapping.getDescription());
                }
            }
        }

        return conceptMap;
    }

    private List<GroupKey> groupMappings(List<ItemMapping> mappings) {
        Map<String, Map<String, List<ItemMapping>>> grouped = new LinkedHashMap<>();

        for (ItemMapping mapping : mappings) {
            String sourceUri = resolveSourceUri(mapping.getSubject());
            String targetUri = resolveTargetUri(mapping.getObject());

            grouped.computeIfAbsent(sourceUri, k -> new LinkedHashMap<>())
                    .computeIfAbsent(targetUri, k -> new ArrayList<>())
                    .add(mapping);
        }

        List<GroupKey> result = new ArrayList<>();
        for (var sourceEntry : grouped.entrySet()) {
            for (var targetEntry : sourceEntry.getValue().entrySet()) {
                result.add(new GroupKey(
                        sourceEntry.getKey(),
                        targetEntry.getKey(),
                        targetEntry.getValue()
                ));
            }
        }
        return result;
    }

    private record GroupKey(String sourceUri, String targetUri, List<ItemMapping> mappings) {}

    // ========================================================================
    // Internal: Source / Target Resolution
    // ========================================================================

    String resolveSourceUri(ItemMappingSubject subject) {
        if (subject.getExternalUri() != null) {
            return extractSystemUri(subject.getExternalUri());
        }
        if (subject.getItemId() != null) {
            return "urn:uuid:" + subject.getItemId();
        }
        return "urn:unknown:source";
    }

    String resolveTargetUri(ItemMappingObject object) {
        if (object.getExternalUri() != null) {
            return extractSystemUri(object.getExternalUri());
        }
        if (object.getItemId() != null) {
            return "urn:uuid:" + object.getItemId();
        }
        return "urn:unknown:target";
    }

    String resolveSourceCode(ItemMappingSubject subject) {
        if (subject.getExternalUri() != null) {
            return extractCodeFromUri(subject.getExternalUri());
        }
        if (subject.getItemId() != null) {
            return subject.getItemId().toString();
        }
        return "unknown";
    }

    String resolveTargetCode(ItemMappingObject object) {
        if (object.getExternalUri() != null) {
            return extractCodeFromUri(object.getExternalUri());
        }
        if (object.getItemId() != null) {
            return object.getItemId().toString();
        }
        return "unknown";
    }

    String extractSystemUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return "urn:unknown";
        }
        if (uri.startsWith("http://") || uri.startsWith("https://") || uri.startsWith("urn:")) {
            return uri;
        }
        return "urn:oid:" + uri;
    }

    String extractCodeFromUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return "unknown";
        }
        if (uri.startsWith("urn:uuid:")) {
            return uri.substring("urn:uuid:".length());
        }
        int lastSlash = uri.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < uri.length() - 1) {
            return uri.substring(lastSlash + 1);
        }
        return uri;
    }

    // ========================================================================
    // Internal: FHIR Validation
    // ========================================================================

    List<FhirValidationError> validateConceptMap(ConceptMap conceptMap) {
        try {
            ValidationResult result = getValidator().validateWithResult(conceptMap);
            if (result.isSuccessful()) {
                return List.of();
            }

            List<FhirValidationError> errors = new ArrayList<>();
            for (SingleValidationMessage msg : result.getMessages()) {
                errors.add(new FhirValidationError(
                        msg.getLocationString(),
                        switch (msg.getSeverity()) {
                            case ERROR -> "error";
                            case WARNING -> "warning";
                            case INFORMATION -> "information";
                            case FATAL -> "fatal";
                            default -> "unknown";
                        },
                        msg.getMessageId(),
                        msg.getMessage(),
                        0
                ));
            }
            return errors;
        } catch (Exception e) {
            log.warn("FHIR validation encountered an error (non-fatal for export): {}", e.getMessage());
            return List.of(
                    FhirValidationError.of("(validation)", "warning",
                            "Validation engine note: " + e.getMessage())
            );
        }
    }

    // ========================================================================
    // Internal: Async Export
    // ========================================================================

    private FhirConceptMapExportResult submitAsyncExport(String schemeId, UUID userId, int totalCount) {
        ExportTask task = ExportTask.builder()
                .status(ExportTaskStatus.PENDING)
                .processedCount(0)
                .totalCount(totalCount)
                .requestParams("{\"schemeId\":\"" + schemeId + "\"}")
                .createdAt(LocalDateTime.now())
                .createdBy(userId)
                .format("json")
                .build();
        task = exportTaskRepository.save(task);

        log.info("Created async export task {} for schemeId: {}", task.getId(), schemeId);

        processAsyncExport(task, schemeId);

        return FhirConceptMapExportResult.asyncSubmitted(task.getId());
    }

    void processAsyncExport(ExportTask task, String schemeId) {
        try {
            task.setStatus(ExportTaskStatus.PROCESSING);
            exportTaskRepository.save(task);

            List<ItemMapping> mappings = fetchMappings(schemeId);
            ConceptMap conceptMap = buildConceptMap(mappings, schemeId);

            IParser jsonParser = fhirContext.newJsonParser();
            jsonParser.setPrettyPrint(true);
            String conceptMapJson = jsonParser.encodeResourceToString(conceptMap);

            Path outputPath = ensureOutputDir().resolve(
                    "conceptmap-" + schemeId + "-" + task.getId() + ".json");
            Files.writeString(outputPath, conceptMapJson, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            task.setStatus(ExportTaskStatus.COMPLETED);
            task.setProcessedCount(mappings.size());
            task.setResultFilePath(outputPath.toAbsolutePath().toString());
            task.setCompletedAt(LocalDateTime.now());
            task.setExpiresAt(LocalDateTime.now().plusDays(7));
            exportTaskRepository.save(task);

            log.info("Async export task {} completed: {} mappings written to {}",
                    task.getId(), mappings.size(), outputPath);
        } catch (Exception e) {
            log.error("Async export task {} failed", task.getId(), e);
            task.setStatus(ExportTaskStatus.FAILED);
            task.setErrorMessage("Export failed: " + e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            exportTaskRepository.save(task);
        }
    }

    private String readExportFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("Failed to read export file: {}", filePath, e);
        }
        return null;
    }

    private Path ensureOutputDir() throws IOException {
        Path dir = Paths.get(outputDir);
        Files.createDirectories(dir);
        return dir;
    }

    // ========================================================================
    // Internal: Utilities
    // ========================================================================

    private static String sanitizeName(String input) {
        if (input == null) return "Unknown";
        return input.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static int countElements(ConceptMap conceptMap) {
        int count = 0;
        for (ConceptMapGroupComponent group : conceptMap.getGroup()) {
            for (SourceElementComponent element : group.getElement()) {
                count += element.getTarget().size();
            }
        }
        return count;
    }
}
