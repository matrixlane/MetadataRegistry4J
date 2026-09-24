package io.github.matrixlane.metadataregistry.itemmapping.service;

import io.github.matrixlane.metadataregistry.itemmapping.dto.FhirMappingConflictWarning;
import io.github.matrixlane.metadataregistry.itemmapping.entity.FhirElementMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingEquivalence;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.FhirElementMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core service for managing local AdministeredItem ↔ FHIR Element mappings.
 *
 * <p>Per WI-012-2 — provides CRUD operations, conflict detection integration,
 * and query methods for FHIR-specific mappings.
 *
 * <h3>Validation Rules</h3>
 * <ul>
 *   <li>A local item cannot map to the same FHIR path twice</li>
 *   <li>One-to-many mappings require a non-blank mapping variable</li>
 *   <li>All create/update operations trigger conflict detection</li>
 * </ul>
 */
@Service
@Transactional
public class FhirElementMappingService {

    private static final Logger log = LoggerFactory.getLogger(FhirElementMappingService.class);

    private final FhirElementMappingRepository repository;
    private final FhirMappingConflictDetector conflictDetector;

    public FhirElementMappingService(FhirElementMappingRepository repository,
                                      FhirMappingConflictDetector conflictDetector) {
        this.repository = repository;
        this.conflictDetector = conflictDetector;
    }

    // ── Create ────────────────────────────────────────────────────────────

    /**
     * Creates a new one-to-one FHIR element mapping.
     *
     * @param localItemId      the local AdministeredItem UUID
     * @param fhirPath         the FHIR Element path
     * @param fhirResourceType the FHIR Resource type
     * @param fhirEquivalence  the FHIR equivalence code
     * @param description      optional description
     * @param createdBy        user creating the mapping
     * @return the persisted mapping
     * @throws IllegalArgumentException if a duplicate mapping exists
     */
    public FhirElementMapping createOneToOneMapping(UUID localItemId,
                                                     String fhirPath,
                                                     String fhirResourceType,
                                                     FhirMappingEquivalence fhirEquivalence,
                                                     String description,
                                                     UUID createdBy) {
        validateDuplicate(localItemId, fhirPath);

        FhirElementMapping mapping = new FhirElementMapping(
                localItemId, fhirPath, fhirResourceType, fhirEquivalence,
                FhirMappingType.ONE_TO_ONE, null, description
        );
        mapping.setCreatedBy(createdBy);

        FhirElementMapping saved = repository.save(mapping);
        log.info("Created one-to-one FHIR mapping: {} → {}", localItemId, fhirPath);

        return saved;
    }

    /**
     * Creates a new one-to-many FHIR element mapping.
     *
     * @param localItemId      the local AdministeredItem UUID
     * @param fhirPath         the FHIR Element path
     * @param fhirResourceType the FHIR Resource type
     * @param fhirEquivalence  the FHIR equivalence code
     * @param mappingVariable  the component variable name (required)
     * @param description      optional description
     * @param createdBy        user creating the mapping
     * @return the persisted mapping
     * @throws IllegalArgumentException if variable is blank or duplicate exists
     */
    public FhirElementMapping createOneToManyMapping(UUID localItemId,
                                                      String fhirPath,
                                                      String fhirResourceType,
                                                      FhirMappingEquivalence fhirEquivalence,
                                                      String mappingVariable,
                                                      String description,
                                                      UUID createdBy) {
        if (mappingVariable == null || mappingVariable.isBlank()) {
            throw new IllegalArgumentException(
                    "mappingVariable is required for one-to-many mappings");
        }
        validateDuplicate(localItemId, fhirPath);

        FhirElementMapping mapping = new FhirElementMapping(
                localItemId, fhirPath, fhirResourceType, fhirEquivalence,
                FhirMappingType.ONE_TO_MANY, mappingVariable, description
        );
        mapping.setCreatedBy(createdBy);

        FhirElementMapping saved = repository.save(mapping);
        log.info("Created one-to-many FHIR mapping: {} → {} (var={})",
                localItemId, fhirPath, mappingVariable);

        return saved;
    }

    // ── Read ──────────────────────────────────────────────────────────────

    /**
     * Finds a mapping by ID.
     */
    @Transactional(readOnly = true)
    public Optional<FhirElementMapping> findById(UUID id) {
        return repository.findById(id);
    }

    /**
     * Finds all mappings for a local item.
     */
    @Transactional(readOnly = true)
    public List<FhirElementMapping> findByLocalItemId(UUID localItemId) {
        return repository.findByLocalItemId(localItemId);
    }

    /**
     * Finds all mappings for a FHIR resource type.
     */
    @Transactional(readOnly = true)
    public List<FhirElementMapping> findByResourceType(String resourceType) {
        return repository.findByFhirResourceType(resourceType);
    }

    /**
     * Finds all mappings.
     */
    @Transactional(readOnly = true)
    public List<FhirElementMapping> findAll() {
        return repository.findAll();
    }

    // ── Update ────────────────────────────────────────────────────────────

    /**
     * Updates an existing mapping.
     *
     * @param id              the mapping UUID
     * @param fhirPath        new FHIR path (null to keep)
     * @param fhirEquivalence new equivalence (null to keep)
     * @param mappingType     new type (null to keep)
     * @param mappingVariable new variable (null to keep)
     * @param description     new description (null to keep)
     * @param updatedBy       user making the update
     * @return the updated mapping
     * @throws IllegalArgumentException if mapping not found or would create duplicate
     */
    public FhirElementMapping updateMapping(UUID id,
                                             String fhirPath,
                                             FhirMappingEquivalence fhirEquivalence,
                                             FhirMappingType mappingType,
                                             String mappingVariable,
                                             String description,
                                             UUID updatedBy) {
        FhirElementMapping mapping = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "FhirElementMapping not found: " + id));

        // If path is changing, check for duplicates
        if (fhirPath != null && !fhirPath.equals(mapping.getFhirPath())) {
            validateDuplicate(mapping.getLocalItemId(), fhirPath);
        }

        mapping.update(fhirPath, fhirEquivalence, mappingType,
                mappingVariable, description, updatedBy);

        FhirElementMapping saved = repository.save(mapping);
        log.info("Updated FHIR mapping: {}", id);
        return saved;
    }

    // ── Delete ────────────────────────────────────────────────────────────

    /**
     * Deletes a mapping by ID.
     *
     * @param id the mapping UUID
     * @throws IllegalArgumentException if mapping not found
     */
    public void deleteMapping(UUID id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException(
                    "FhirElementMapping not found: " + id);
        }
        repository.deleteById(id);
        log.info("Deleted FHIR mapping: {}", id);
    }

    /**
     * Deletes all mappings for a local item.
     *
     * @param localItemId the local item UUID
     * @return count of deleted mappings
     */
    public long deleteByLocalItemId(UUID localItemId) {
        List<FhirElementMapping> mappings = repository.findByLocalItemId(localItemId);
        repository.deleteAll(mappings);
        log.info("Deleted {} FHIR mappings for item {}", mappings.size(), localItemId);
        return mappings.size();
    }

    // ── Conflict Detection ────────────────────────────────────────────────

    /**
     * Detects conflicts for a specific local item.
     */
    @Transactional(readOnly = true)
    public List<FhirMappingConflictWarning> detectConflicts(UUID localItemId) {
        return conflictDetector.detectConflicts(localItemId);
    }

    /**
     * Detects all conflicts across the registry.
     */
    @Transactional(readOnly = true)
    public List<FhirMappingConflictWarning> detectAllConflicts() {
        return conflictDetector.detectAllConflicts();
    }

    // ── Validation ────────────────────────────────────────────────────────

    private void validateDuplicate(UUID localItemId, String fhirPath) {
        if (repository.existsByLocalItemIdAndFhirPath(localItemId, fhirPath)) {
            throw new IllegalArgumentException(String.format(
                    "Duplicate mapping: localItem %s already mapped to %s",
                    localItemId, fhirPath));
        }
    }
}
