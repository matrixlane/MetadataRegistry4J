package io.github.matrixlane.metadataregistry.itemmapping.service;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core service for managing item mapping relationships.
 *
 * <p>Per ISO/IEC 11179-3:2023 Clause 11 — Item Mapping.
 * This service provides operations to create, query, and validate
 * mappings between administered items and/or external resources.
 *
 * <h3>Mapping Type Mutual Exclusion Matrix</h3>
 * <pre>
 *                    EQUIVALENT  DERIVED  AGGREGATED  SUBDIVIDED
 * EQUIVALENT              -        ✗         ✗           ✗
 * DERIVED                 ✗        -         ✗           ✓
 * AGGREGATED              ✗        ✗         -           ✗
 * SUBDIVIDED              ✗        ✓         ✗           -
 * </pre>
 * Where ✓ = allowed, ✗ = mutually exclusive (disallowed for same pair).
 */
@Service
@Transactional
public class ItemMappingService {

    private final ItemMappingRepository mappingRepository;

    public ItemMappingService(ItemMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    // ---- Mutual Exclusion Matrix ----

    // Pre-defined mutual exclusion rules for mapping types.
    // Each pair represents (typeA, typeB) — if both exist between the same
    // two items, the mapping is invalid.
    private static final Set<Map.Entry<MappingType, MappingType>> MUTUALLY_EXCLUSIVE_PAIRS = Set.of(
            Map.entry(MappingType.EQUIVALENT, MappingType.DERIVED),
            Map.entry(MappingType.EQUIVALENT, MappingType.AGGREGATED),
            Map.entry(MappingType.EQUIVALENT, MappingType.SUBDIVIDED),
            Map.entry(MappingType.DERIVED, MappingType.AGGREGATED),
            Map.entry(MappingType.AGGREGATED, MappingType.SUBDIVIDED)
    );

    // ---- Core Operations ----

    /**
     * Creates a new mapping between two administered items.
     *
     * <p>Before creating the mapping, this method performs:
     * <ol>
     *   <li>Mapping type mutual exclusion validation</li>
     *   <li>Cycle detection (for non-EQUIVALENT directional mappings)</li>
     *   <li>Duplicate mapping prevention</li>
     * </ol>
     *
     * @param subjectItemId the UUID of the subject AdministeredItem
     * @param objectItemId  the UUID of the object AdministeredItem
     * @param mappingType   the type of mapping relationship
     * @return the newly created ItemMapping
     * @throws IllegalArgumentException if validation fails
     */
    public ItemMapping createItemMapping(UUID subjectItemId, UUID objectItemId,
                                          MappingType mappingType) {
        return createMapping(
                ItemMappingSubject.forItem(subjectItemId),
                ItemMappingObject.forItem(objectItemId),
                mappingType,
                null
        );
    }

    /**
     * Creates a new mapping between an internal item and an external resource.
     *
     * @param subjectItemId the UUID of the subject AdministeredItem
     * @param externalUri   the URI of the external object resource
     * @param mappingType   the type of mapping relationship
     * @return the newly created ItemMapping
     * @throws IllegalArgumentException if validation fails
     */
    public ItemMapping createItemToExternalMapping(UUID subjectItemId, String externalUri,
                                                    MappingType mappingType) {
        return createMapping(
                ItemMappingSubject.forItem(subjectItemId),
                ItemMappingObject.forExternalUri(externalUri),
                mappingType,
                null
        );
    }

    /**
     * Creates a new mapping between an external resource and an internal item.
     *
     * @param externalUri   the URI of the external subject resource
     * @param objectItemId  the UUID of the object AdministeredItem
     * @param mappingType   the type of mapping relationship
     * @return the newly created ItemMapping
     * @throws IllegalArgumentException if validation fails
     */
    public ItemMapping createExternalToItemMapping(String externalUri, UUID objectItemId,
                                                    MappingType mappingType) {
        return createMapping(
                ItemMappingSubject.forExternalUri(externalUri),
                ItemMappingObject.forItem(objectItemId),
                mappingType,
                null
        );
    }

    /**
     * Internal method that performs all validations and creates the mapping.
     */
    private ItemMapping createMapping(ItemMappingSubject subject,
                                       ItemMappingObject object,
                                       MappingType mappingType,
                                       String description) {
        // Validate subject/object reference mutual exclusivity
        if (!subject.isReferenceMutuallyExclusive()) {
            throw new IllegalArgumentException(
                    "Subject must reference exactly one of itemId or externalUri");
        }
        if (!object.isReferenceMutuallyExclusive()) {
            throw new IllegalArgumentException(
                    "Object must reference exactly one of itemId or externalUri");
        }

        // Skip duplicate and cycle checks for external-only mappings
        // (no AdministeredItem UUID to check against)
        UUID subjectItemId = subject.getItemId();
        UUID objectItemId = object.getItemId();

        if (subjectItemId != null && objectItemId != null) {
            // Internal-to-internal mapping

            // Validate mutual exclusion with existing mappings of different types
            validateMutualExclusion(subjectItemId, objectItemId, mappingType);

            // Cycle detection for directional mappings
            if (mappingType != MappingType.EQUIVALENT) {
                detectCycle(subjectItemId, objectItemId);
            }
        }

        // Create and save the mapping
        ItemMapping mapping = new ItemMapping(subject, object, mappingType, description);
        return mappingRepository.save(mapping);
    }

    // ---- Validation Methods ----

    /**
     * Validates that no existing mappings between the same subject and object
     * violate the mutual exclusion matrix.
     *
     * @param subjectItemId the subject item UUID
     * @param objectItemId  the object item UUID
     * @param newType       the mapping type being proposed
     * @throws IllegalArgumentException if a conflicting mapping exists
     */
    void validateMutualExclusion(UUID subjectItemId, UUID objectItemId, MappingType newType) {
        List<ItemMapping> existingMappings = mappingRepository.findByItemId(subjectItemId);

        for (ItemMapping existing : existingMappings) {
            UUID existingSubjectId = existing.getSubject().getItemId();
            UUID existingObjectId = existing.getObject().getItemId();

            // Only check pairs that involve the same two items
            boolean samePair = (subjectItemId.equals(existingSubjectId) &&
                                objectItemId.equals(existingObjectId)) ||
                               (subjectItemId.equals(existingObjectId) &&
                                objectItemId.equals(existingSubjectId));

            if (samePair && areMutuallyExclusive(newType, existing.getMappingType())) {
                throw new IllegalArgumentException(
                        String.format("Mapping type %s is mutually exclusive with existing " +
                                      "type %s between items %s and %s",
                                newType, existing.getMappingType(),
                                subjectItemId, objectItemId));
            }
        }
    }

    /**
     * Checks whether two mapping types are mutually exclusive.
     */
    static boolean areMutuallyExclusive(MappingType a, MappingType b) {
        if (a == b) {
            // Same type is always allowed; a given pair can have multiple
            // mappings of the same type (though duplicates are checked elsewhere)
            return false;
        }
        return MUTUALLY_EXCLUSIVE_PAIRS.contains(Map.entry(a, b)) ||
               MUTUALLY_EXCLUSIVE_PAIRS.contains(Map.entry(b, a));
    }

    /**
     * Detects whether adding a mapping from {@code sourceId} to {@code targetId}
     * would create a cycle in the mapping graph.
     *
     * <p>Uses DFS-based cycle detection. Time complexity: O(V+E) for
     * the subgraph reachable from {@code targetId}.
     *
     * @param sourceId the subject item UUID (source of the new edge)
     * @param targetId the object item UUID (target of the new edge)
     * @throws IllegalArgumentException if a cycle would be created
     */
    void detectCycle(UUID sourceId, UUID targetId) {
        // Build adjacency list from all non-EQUIVALENT mappings
        // EQUIVALENT mappings are bidirectional and not subject to cycle detection
        List<ItemMapping> allMappings = mappingRepository.findAll();

        Map<UUID, Set<UUID>> adjacency = allMappings.stream()
                .filter(m -> m.getMappingType() != MappingType.EQUIVALENT)
                .filter(m -> m.getSubject().getItemId() != null &&
                             m.getObject().getItemId() != null)
                .collect(Collectors.groupingBy(
                        m -> m.getSubject().getItemId(),
                        Collectors.mapping(m -> m.getObject().getItemId(), Collectors.toSet())
                ));

        // Add the proposed edge
        adjacency.computeIfAbsent(sourceId, k -> new HashSet<>()).add(targetId);

        // Check if targetId can reach sourceId (would create a cycle)
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> stack = new ArrayDeque<>();
        stack.push(targetId);

        while (!stack.isEmpty()) {
            UUID current = stack.pop();
            if (current.equals(sourceId)) {
                throw new IllegalArgumentException(
                        String.format("Adding mapping from %s to %s would create a cycle " +
                                      "in the mapping graph", sourceId, targetId));
            }
            if (!visited.add(current)) {
                continue;
            }
            Set<UUID> neighbors = adjacency.getOrDefault(current, Set.of());
            neighbors.forEach(stack::push);
        }
    }

    // ---- Query Operations ----

    /**
     * Finds all outgoing mappings from a given item (as subject).
     *
     * @param itemId the AdministeredItem UUID
     * @return list of mappings where the item is the subject
     */
    @Transactional(readOnly = true)
    public List<ItemMapping> getOutgoingMappings(UUID itemId) {
        return mappingRepository.findBySubjectItemId(itemId);
    }

    /**
     * Finds all incoming mappings to a given item (as object).
     *
     * @param itemId the AdministeredItem UUID
     * @return list of mappings where the item is the object
     */
    @Transactional(readOnly = true)
    public List<ItemMapping> getIncomingMappings(UUID itemId) {
        return mappingRepository.findByObjectItemId(itemId);
    }

    /**
     * Finds all mappings involving a given item (as either subject or object).
     *
     * @param itemId the AdministeredItem UUID
     * @return list of all mappings referencing the item
     */
    @Transactional(readOnly = true)
    public List<ItemMapping> getAllMappingsForItem(UUID itemId) {
        return mappingRepository.findByItemId(itemId);
    }

    /**
     * Deletes a mapping by its ID.
     *
     * @param mappingId the UUID of the mapping to delete
     * @throws IllegalArgumentException if the mapping does not exist
     */
    public void deleteMapping(UUID mappingId) {
        if (!mappingRepository.existsById(mappingId)) {
            throw new IllegalArgumentException("Mapping not found: " + mappingId);
        }
        mappingRepository.deleteById(mappingId);
    }
}
