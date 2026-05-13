package io.github.matrixlane.metadataregistry.web.service.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import io.github.matrixlane.metadataregistry.itemmapping.entity.FhirElementMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingEquivalence;
import io.github.matrixlane.metadataregistry.itemmapping.repository.FhirElementMappingRepository;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirConceptMapExportResult;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirValidationError;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for exporting {@link FhirElementMapping} records as FHIR R4 ConceptMap.
 *
 * <p>Per WI-012-2 — exports local AdministeredItem ↔ FHIR Element mappings
 * in standard FHIR R4 ConceptMap JSON format, validated with HAPI FHIR.
 *
 * <p>Mapping groups are organised by {@code fhirResourceType}.
 * Each group contains source elements (local item UUIDs) and target elements
 * (FHIR paths) with equivalence relationships.
 */
@Service
public class FhirElementMappingExportService {

    private static final Logger log = LoggerFactory.getLogger(FhirElementMappingExportService.class);

    private final FhirElementMappingRepository mappingRepository;
    private final FhirContext fhirContext = FhirContext.forR4();
    private FhirValidator validator;

    public FhirElementMappingExportService(FhirElementMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    /**
     * Exports all FHIR element mappings as a ConceptMap.
     *
     * @return export result with JSON and validation status
     */
    @Transactional(readOnly = true)
    public FhirConceptMapExportResult exportAllMappings() {
        List<FhirElementMapping> mappings = mappingRepository.findAll();
        return doExport(mappings, "all");
    }

    /**
     * Exports mappings for a specific FHIR resource type.
     *
     * @param resourceType the FHIR resource type, e.g. "Patient"
     * @return export result with JSON and validation status
     */
    @Transactional(readOnly = true)
    public FhirConceptMapExportResult exportByResourceType(String resourceType) {
        List<FhirElementMapping> mappings =
                mappingRepository.findByFhirResourceType(resourceType);
        return doExport(mappings, resourceType);
    }

    /**
     * Exports mappings belonging to a specific mapping scheme.
     *
     * @param schemeId the mapping scheme UUID
     * @return export result with JSON and validation status
     */
    @Transactional(readOnly = true)
    public FhirConceptMapExportResult exportByScheme(UUID schemeId) {
        List<FhirElementMapping> mappings =
                mappingRepository.findByMappingSchemeId(schemeId);
        return doExport(mappings, schemeId.toString());
    }

    // ── Internal export logic ─────────────────────────────────────────────

    FhirConceptMapExportResult doExport(List<FhirElementMapping> mappings, String scope) {
        if (mappings.isEmpty()) {
            return FhirConceptMapExportResult.failure(
                    "No FHIR element mappings found for scope: " + scope);
        }

        try {
            ConceptMap conceptMap = buildConceptMap(mappings, scope);

            IParser jsonParser = fhirContext.newJsonParser();
            jsonParser.setPrettyPrint(true);
            String conceptMapJson = jsonParser.encodeResourceToString(conceptMap);

            List<FhirValidationError> validationErrors = validateConceptMap(conceptMap);
            int elementCount = countTargets(conceptMap);

            return FhirConceptMapExportResult.syncSuccess(
                    conceptMapJson,
                    validationErrors.isEmpty(),
                    validationErrors,
                    mappings.size(),
                    elementCount
            );
        } catch (Exception e) {
            log.error("Failed to export ConceptMap for scope: {}", scope, e);
            return FhirConceptMapExportResult.failure(
                    "Export failed: " + e.getMessage());
        }
    }

    ConceptMap buildConceptMap(List<FhirElementMapping> mappings, String scope) {
        ConceptMap conceptMap = new ConceptMap();

        conceptMap.setId("fhir-element-mapping-" + sanitizeName(scope));
        conceptMap.setUrl("urn:uuid:fhir-element-mapping:" + scope);
        conceptMap.setName("FhirElementMapping_" + sanitizeName(scope));
        conceptMap.setTitle("Local Metadata to FHIR Element Mapping — " + scope);
        conceptMap.setStatus(Enumerations.PublicationStatus.DRAFT);
        conceptMap.setVersion("1.0");
        conceptMap.setDescription(
                "ISO/IEC 11179 AdministeredItem to FHIR R4 Element mapping export");
        conceptMap.setPublisher("MetadataRegistry4j");

        // Group mappings by fhirResourceType
        Map<String, List<FhirElementMapping>> byResource = new LinkedHashMap<>();
        for (FhirElementMapping m : mappings) {
            byResource.computeIfAbsent(m.getFhirResourceType(), k -> new ArrayList<>())
                    .add(m);
        }

        for (Map.Entry<String, List<FhirElementMapping>> entry : byResource.entrySet()) {
            ConceptMap.ConceptMapGroupComponent group = conceptMap.addGroup();
            group.setSource("urn:uuid:local-metadata-registry");
            group.setTarget("http://hl7.org/fhir/StructureDefinition/" + entry.getKey());

            for (FhirElementMapping m : entry.getValue()) {
                ConceptMap.SourceElementComponent element = group.addElement();
                // Source = local item UUID
                element.setCode(m.getLocalItemId().toString());
                element.setDisplay(m.getDisplayName());

                ConceptMap.TargetElementComponent target = element.addTarget();
                // Target = FHIR path
                target.setCode(m.getFhirPath());
                target.setEquivalence(toHapiEquivalence(m.getFhirEquivalence()));

                if (m.getDescription() != null && !m.getDescription().isBlank()) {
                    target.setComment(m.getDescription());
                }

                // Add mapping variable as extension for one-to-many
                if (m.isOneToMany() && m.getMappingVariable() != null) {
                    target.addExtension(
                            "http://metadataregistry4j.io/fhir-mapping-variable",
                            new StringType(m.getMappingVariable())
                    );
                }
            }
        }

        return conceptMap;
    }

    List<FhirValidationError> validateConceptMap(ConceptMap conceptMap) {
        try {
            ValidationResult result = getValidator().validateWithResult(conceptMap);
            if (result.isSuccessful()) {
                return Collections.emptyList();
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
            log.warn("FHIR validation encountered an error: {}", e.getMessage());
            return List.of(
                    FhirValidationError.of("(validation)", "warning",
                            "Validation engine note: " + e.getMessage())
            );
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

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

    private static Enumerations.ConceptMapEquivalence toHapiEquivalence(
            FhirMappingEquivalence equivalence) {
        return switch (equivalence) {
            case EQUIVALENT -> Enumerations.ConceptMapEquivalence.EQUIVALENT;
            case EQUAL -> Enumerations.ConceptMapEquivalence.EQUAL;
            case WIDER -> Enumerations.ConceptMapEquivalence.WIDER;
            case NARROWER -> Enumerations.ConceptMapEquivalence.NARROWER;
            case SUBSUMES -> Enumerations.ConceptMapEquivalence.SUBSUMES;
            case SPECIALIZES -> Enumerations.ConceptMapEquivalence.SPECIALIZES;
            case RELATEDTO -> Enumerations.ConceptMapEquivalence.RELATEDTO;
            case INEXACT -> Enumerations.ConceptMapEquivalence.INEXACT;
            case UNMATCHED -> Enumerations.ConceptMapEquivalence.UNMATCHED;
            case DISJOINT -> Enumerations.ConceptMapEquivalence.DISJOINT;
        };
    }

    private static int countTargets(ConceptMap conceptMap) {
        int count = 0;
        for (ConceptMap.ConceptMapGroupComponent group : conceptMap.getGroup()) {
            for (ConceptMap.SourceElementComponent element : group.getElement()) {
                count += element.getTarget().size();
            }
        }
        return count;
    }

    private static String sanitizeName(String input) {
        if (input == null) return "Unknown";
        return input.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
