package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.MappingScheme;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingSchemeStatus;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.itemmapping.repository.MappingSchemeRepository;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingDiffEntry;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingDiffResponse;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingVersionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MappingDiffService}.
 *
 * <p>Per WI-011-2-3-2: validates diff computation and version lineage
 * traversal with mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
class MappingDiffServiceTest {

    @Mock
    private MappingSchemeRepository schemeRepository;

    @Mock
    private ItemMappingRepository mappingRepository;

    @InjectMocks
    private MappingDiffService diffService;

    private static final UUID BASE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID COMPARE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ITEM_A = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID ITEM_B = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID ITEM_C = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    private MappingScheme baseScheme;
    private MappingScheme compareScheme;

    @BeforeEach
    void setUp() {
        baseScheme = new MappingScheme("Test", "http://src", "http://dst", null);
        baseScheme.setId(BASE_ID);
        baseScheme.setVersion("0.1.0");
        baseScheme.setStatus(MappingSchemeStatus.PUBLISHED);
        baseScheme.setCreatedAt(LocalDateTime.of(2026, 5, 1, 9, 0));

        compareScheme = new MappingScheme("Test", "http://src", "http://dst", null);
        compareScheme.setId(COMPARE_ID);
        compareScheme.setVersion("0.1.1");
        compareScheme.setStatus(MappingSchemeStatus.DRAFT);
        compareScheme.setParentId(BASE_ID);
        compareScheme.setCreatedAt(LocalDateTime.of(2026, 5, 2, 9, 0));
    }

    @Test
    void computeDiff_identicalSchemes_shouldReturnEmptyDiff() {
        when(schemeRepository.findById(BASE_ID)).thenReturn(Optional.of(baseScheme));
        when(schemeRepository.findById(COMPARE_ID)).thenReturn(Optional.of(compareScheme));
        when(mappingRepository.findByMappingSchemeId(BASE_ID)).thenReturn(List.of());
        when(mappingRepository.findByMappingSchemeId(COMPARE_ID)).thenReturn(List.of());

        MappingDiffResponse response = diffService.computeDiff(BASE_ID, COMPARE_ID, null);

        assertThat(response.identical()).isTrue();
        assertThat(response.totalDiffCount()).isZero();
        assertThat(response.added()).isEmpty();
        assertThat(response.removed()).isEmpty();
        assertThat(response.modified()).isEmpty();
    }

    @Test
    void computeDiff_addedMapping_shouldDetectAddition() {
        ItemMapping addedMapping = createMapping(ITEM_A, ITEM_B, MappingType.EQUIVALENT, null, null);

        when(schemeRepository.findById(BASE_ID)).thenReturn(Optional.of(baseScheme));
        when(schemeRepository.findById(COMPARE_ID)).thenReturn(Optional.of(compareScheme));
        when(mappingRepository.findByMappingSchemeId(BASE_ID)).thenReturn(List.of());
        when(mappingRepository.findByMappingSchemeId(COMPARE_ID)).thenReturn(List.of(addedMapping));

        MappingDiffResponse response = diffService.computeDiff(BASE_ID, COMPARE_ID, null);

        assertThat(response.identical()).isFalse();
        assertThat(response.added()).hasSize(1);
        assertThat(response.removed()).isEmpty();
        assertThat(response.modified()).isEmpty();
        assertThat(response.totalDiffCount()).isEqualTo(1);

        MappingDiffEntry entry = response.added().get(0);
        assertThat(entry.source()).isEqualTo(ITEM_A.toString());
        assertThat(entry.target()).isEqualTo(ITEM_B.toString());
        assertThat(entry.mappingType()).isEqualTo(MappingType.EQUIVALENT);
    }

    @Test
    void computeDiff_removedMapping_shouldDetectRemoval() {
        ItemMapping removedMapping = createMapping(ITEM_A, ITEM_B, MappingType.EQUIVALENT, null, null);

        when(schemeRepository.findById(BASE_ID)).thenReturn(Optional.of(baseScheme));
        when(schemeRepository.findById(COMPARE_ID)).thenReturn(Optional.of(compareScheme));
        when(mappingRepository.findByMappingSchemeId(BASE_ID)).thenReturn(List.of(removedMapping));
        when(mappingRepository.findByMappingSchemeId(COMPARE_ID)).thenReturn(List.of());

        MappingDiffResponse response = diffService.computeDiff(BASE_ID, COMPARE_ID, null);

        assertThat(response.removed()).hasSize(1);
        assertThat(response.added()).isEmpty();
        assertThat(response.modified()).isEmpty();

        MappingDiffEntry entry = response.removed().get(0);
        assertThat(entry.source()).isEqualTo(ITEM_A.toString());
        assertThat(entry.target()).isEqualTo(ITEM_B.toString());
    }

    @Test
    void computeDiff_modifiedMapping_shouldDetectModification() {
        ItemMapping baseMapping = createMapping(ITEM_A, ITEM_B, MappingType.EQUIVALENT, "old desc", null);
        ItemMapping compareMapping = createMapping(ITEM_A, ITEM_B, MappingType.DERIVED, "new desc", "rule1");

        when(schemeRepository.findById(BASE_ID)).thenReturn(Optional.of(baseScheme));
        when(schemeRepository.findById(COMPARE_ID)).thenReturn(Optional.of(compareScheme));
        when(mappingRepository.findByMappingSchemeId(BASE_ID)).thenReturn(List.of(baseMapping));
        when(mappingRepository.findByMappingSchemeId(COMPARE_ID)).thenReturn(List.of(compareMapping));

        MappingDiffResponse response = diffService.computeDiff(BASE_ID, COMPARE_ID, null);

        assertThat(response.modified()).hasSize(1);
        assertThat(response.added()).isEmpty();
        assertThat(response.removed()).isEmpty();

        MappingDiffEntry entry = response.modified().get(0);
        assertThat(entry.source()).isEqualTo(ITEM_A.toString());
        assertThat(entry.target()).isEqualTo(ITEM_B.toString());
        assertThat(entry.changedFields()).containsExactlyInAnyOrder("mappingType", "description", "transformationRule");
        assertThat(entry.from().mappingType()).isEqualTo(MappingType.EQUIVALENT);
        assertThat(entry.to().mappingType()).isEqualTo(MappingType.DERIVED);
        assertThat(entry.from().description()).isEqualTo("old desc");
        assertThat(entry.to().description()).isEqualTo("new desc");
    }

    @Test
    void computeDiff_externalUriMapping_shouldHandleExternalReferences() {
        ItemMapping baseMapping = createExternalMapping("http://ext-src", ITEM_A, MappingType.EQUIVALENT, null, null);
        ItemMapping compareMapping = createExternalMapping("http://ext-src", ITEM_A, MappingType.EQUIVALENT, "updated", null);

        when(schemeRepository.findById(BASE_ID)).thenReturn(Optional.of(baseScheme));
        when(schemeRepository.findById(COMPARE_ID)).thenReturn(Optional.of(compareScheme));
        when(mappingRepository.findByMappingSchemeId(BASE_ID)).thenReturn(List.of(baseMapping));
        when(mappingRepository.findByMappingSchemeId(COMPARE_ID)).thenReturn(List.of(compareMapping));

        MappingDiffResponse response = diffService.computeDiff(BASE_ID, COMPARE_ID, null);

        assertThat(response.modified()).hasSize(1);
        assertThat(response.modified().get(0).changedFields()).containsExactly("description");
    }

    @Test
    void computeDiff_mixedChanges_shouldReturnAllTypes() {
        ItemMapping baseOnly = createMapping(ITEM_A, ITEM_B, MappingType.EQUIVALENT, null, null);
        ItemMapping compareOnly = createMapping(ITEM_B, ITEM_C, MappingType.DERIVED, null, null);
        ItemMapping bothModifiedBase = createMapping(ITEM_A, ITEM_C, MappingType.EQUIVALENT, "old", null);
        ItemMapping bothModifiedCompare = createMapping(ITEM_A, ITEM_C, MappingType.EQUIVALENT, "new", null);

        when(schemeRepository.findById(BASE_ID)).thenReturn(Optional.of(baseScheme));
        when(schemeRepository.findById(COMPARE_ID)).thenReturn(Optional.of(compareScheme));
        when(mappingRepository.findByMappingSchemeId(BASE_ID))
                .thenReturn(List.of(baseOnly, bothModifiedBase));
        when(mappingRepository.findByMappingSchemeId(COMPARE_ID))
                .thenReturn(List.of(compareOnly, bothModifiedCompare));

        MappingDiffResponse response = diffService.computeDiff(BASE_ID, COMPARE_ID, null);

        assertThat(response.added()).hasSize(1);
        assertThat(response.removed()).hasSize(1);
        assertThat(response.modified()).hasSize(1);
        assertThat(response.totalDiffCount()).isEqualTo(3);
    }

    @Test
    void computeDiff_withMappingTypeFilter_shouldReturnOnlyMatchingType() {
        ItemMapping addedEquivalent = createMapping(ITEM_A, ITEM_B, MappingType.EQUIVALENT, null, null);
        ItemMapping addedDerived = createMapping(ITEM_B, ITEM_C, MappingType.DERIVED, null, null);

        when(schemeRepository.findById(BASE_ID)).thenReturn(Optional.of(baseScheme));
        when(schemeRepository.findById(COMPARE_ID)).thenReturn(Optional.of(compareScheme));
        when(mappingRepository.findByMappingSchemeId(BASE_ID)).thenReturn(List.of());
        when(mappingRepository.findByMappingSchemeId(COMPARE_ID)).thenReturn(List.of(addedEquivalent, addedDerived));

        MappingDiffResponse response = diffService.computeDiff(BASE_ID, COMPARE_ID, MappingType.EQUIVALENT);

        assertThat(response.added()).hasSize(1);
        assertThat(response.added().get(0).mappingType()).isEqualTo(MappingType.EQUIVALENT);
        assertThat(response.totalDiffCount()).isEqualTo(1);
    }

    @Test
    void getVersionLineage_shouldReturnAllVersionsInOrder() {
        UUID rootId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID childId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        MappingScheme root = new MappingScheme("Root", "http://src", "http://dst", null);
        root.setId(rootId);
        root.setVersion("0.1.0");
        root.setStatus(MappingSchemeStatus.PUBLISHED);
        root.setCreatedAt(LocalDateTime.of(2026, 5, 1, 9, 0));

        MappingScheme child = new MappingScheme("Root", "http://src", "http://dst", null);
        child.setId(childId);
        child.setVersion("0.1.1");
        child.setStatus(MappingSchemeStatus.DRAFT);
        child.setParentId(rootId);
        child.setCreatedAt(LocalDateTime.of(2026, 5, 2, 9, 0));

        when(schemeRepository.findById(childId)).thenReturn(Optional.of(child));
        when(schemeRepository.findById(rootId)).thenReturn(Optional.of(root));
        when(schemeRepository.findByParentId(rootId)).thenReturn(List.of(child));
        when(schemeRepository.findByParentId(childId)).thenReturn(List.of());

        List<MappingVersionInfo> lineage = diffService.getVersionLineage(childId);

        assertThat(lineage).hasSize(2);
        assertThat(lineage.get(0).id()).isEqualTo(rootId);
        assertThat(lineage.get(0).version()).isEqualTo("0.1.0");
        assertThat(lineage.get(1).id()).isEqualTo(childId);
        assertThat(lineage.get(1).version()).isEqualTo("0.1.1");
    }

    @Test
    void computeDiff_schemeNotFound_shouldThrowResourceNotFoundException() {
        UUID nonExistentId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(schemeRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException.class,
                () -> diffService.computeDiff(nonExistentId, COMPARE_ID, null)
        );
    }

    @Test
    void computeDiff_sameSchemeId_shouldReturnEmptyDiff() {
        when(schemeRepository.findById(BASE_ID)).thenReturn(Optional.of(baseScheme));
        when(mappingRepository.findByMappingSchemeId(BASE_ID)).thenReturn(List.of());

        MappingDiffResponse response = diffService.computeDiff(BASE_ID, BASE_ID, null);

        assertThat(response.identical()).isTrue();
        assertThat(response.totalDiffCount()).isZero();
    }

    @Test
    void getVersionLineage_schemeNotFound_shouldThrowResourceNotFoundException() {
        UUID nonExistentId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(schemeRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException.class,
                () -> diffService.getVersionLineage(nonExistentId)
        );
    }

    @Test
    void getVersionLineage_cycleProtection_shouldBreakLoop() {
        // A -> B -> A (cycle)
        UUID idA = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID idB = UUID.fromString("22222222-2222-2222-2222-222222222222");

        MappingScheme schemeA = new MappingScheme("A", "http://src", "http://dst", null);
        schemeA.setId(idA);
        schemeA.setVersion("0.1.0");
        schemeA.setParentId(idB);
        schemeA.setCreatedAt(LocalDateTime.of(2026, 5, 1, 9, 0));

        MappingScheme schemeB = new MappingScheme("B", "http://src", "http://dst", null);
        schemeB.setId(idB);
        schemeB.setVersion("0.1.1");
        schemeB.setParentId(idA);
        schemeB.setCreatedAt(LocalDateTime.of(2026, 5, 2, 9, 0));

        when(schemeRepository.findById(idA)).thenReturn(Optional.of(schemeA));
        when(schemeRepository.findById(idB)).thenReturn(Optional.of(schemeB));
        when(schemeRepository.findByParentId(idA)).thenReturn(List.of(schemeB));
        when(schemeRepository.findByParentId(idB)).thenReturn(List.of(schemeA));

        List<MappingVersionInfo> lineage = diffService.getVersionLineage(idA);

        // Should not infinite loop; should return both schemes
        assertThat(lineage).hasSize(2);
    }

    @Test
    void computeDiff_largeScheme_shouldCompleteInReasonableTime() {
        // Generate 1000 mappings in base and 1000 in compare with ~50% overlap
        List<ItemMapping> baseMappings = new ArrayList<>();
        List<ItemMapping> compareMappings = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            UUID sub = UUID.nameUUIDFromBytes(("sub" + i).getBytes());
            UUID obj = UUID.nameUUIDFromBytes(("obj" + i).getBytes());
            baseMappings.add(createMapping(sub, obj, MappingType.EQUIVALENT, "desc" + i, null));
            if (i < 500) {
                // 500 shared mappings
                compareMappings.add(createMapping(sub, obj, MappingType.EQUIVALENT, "desc" + i, null));
            } else if (i < 750) {
                // 250 modified mappings
                compareMappings.add(createMapping(sub, obj, MappingType.DERIVED, "desc" + i + "_mod", null));
            }
            // 250 mappings only in base (removed)
        }
        // 250 mappings only in compare (added)
        for (int i = 1000; i < 1250; i++) {
            UUID sub = UUID.nameUUIDFromBytes(("sub" + i).getBytes());
            UUID obj = UUID.nameUUIDFromBytes(("obj" + i).getBytes());
            compareMappings.add(createMapping(sub, obj, MappingType.EQUIVALENT, "desc" + i, null));
        }

        when(schemeRepository.findById(BASE_ID)).thenReturn(Optional.of(baseScheme));
        when(schemeRepository.findById(COMPARE_ID)).thenReturn(Optional.of(compareScheme));
        when(mappingRepository.findByMappingSchemeId(BASE_ID)).thenReturn(baseMappings);
        when(mappingRepository.findByMappingSchemeId(COMPARE_ID)).thenReturn(compareMappings);

        long start = System.currentTimeMillis();
        MappingDiffResponse response = diffService.computeDiff(BASE_ID, COMPARE_ID, null);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.added()).hasSize(250);
        assertThat(response.removed()).hasSize(250);
        assertThat(response.modified()).hasSize(250);
        assertThat(response.totalDiffCount()).isEqualTo(750);
        // Should complete in under 1 second for 1000 mappings
        assertThat(elapsed).isLessThan(1000);
    }

    // ---- Helpers ----

    private ItemMapping createMapping(UUID subjectId, UUID objectId,
                                       MappingType type, String description,
                                       String transformationRule) {
        ItemMappingSubject subject = ItemMappingSubject.forItem(subjectId);
        ItemMappingObject object = ItemMappingObject.forItem(objectId);
        return new ItemMapping(subject, object, type, description,
                transformationRule, null);
    }

    private ItemMapping createExternalMapping(String externalUri, UUID objectId,
                                               MappingType type, String description,
                                               String transformationRule) {
        ItemMappingSubject subject = ItemMappingSubject.forExternalUri(externalUri);
        ItemMappingObject object = ItemMappingObject.forItem(objectId);
        return new ItemMapping(subject, object, type, description,
                transformationRule, null);
    }
}
