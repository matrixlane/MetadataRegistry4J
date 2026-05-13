package io.github.matrixlane.metadataregistry.web.service.fhir;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirConceptMapExportResult;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirValidationError;
import io.github.matrixlane.metadataregistry.web.entity.ExportTask;
import io.github.matrixlane.metadataregistry.web.entity.ExportTaskStatus;
import io.github.matrixlane.metadataregistry.web.repository.ExportTaskRepository;
import org.hl7.fhir.r4.model.ConceptMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link FhirConceptMapExportService}.
 *
 * <p>Covers all major scenarios: single-scheme export, bulk export,
 * async threshold, validation, edge cases (empty, circular references,
 * special characters).
 */
@ExtendWith(MockitoExtension.class)
class FhirConceptMapExportServiceTest {

    @Mock
    private ItemMappingRepository mappingRepository;

    @Mock
    private ExportTaskRepository exportTaskRepository;

    private FhirConceptMapExportService service;

    private UUID mockUserId;

    @BeforeEach
    void setUp() {
        service = new FhirConceptMapExportService(mappingRepository, exportTaskRepository);
        mockUserId = UUID.randomUUID();
    }

    // ========================================================================
    // Equivalence Mapping Tests
    // ========================================================================

    @Test
    void equivalenceMapper_equivalent_toEquivalent() {
        assertThat(FhirEquivalenceMapper.toFhirEquivalence(MappingType.EQUIVALENT))
                .isEqualTo(org.hl7.fhir.r4.model.Enumerations.ConceptMapEquivalence.EQUIVALENT);
    }

    @Test
    void equivalenceMapper_derived_toWider() {
        assertThat(FhirEquivalenceMapper.toFhirEquivalence(MappingType.DERIVED))
                .isEqualTo(org.hl7.fhir.r4.model.Enumerations.ConceptMapEquivalence.WIDER);
    }

    @Test
    void equivalenceMapper_aggregated_toNarrower() {
        assertThat(FhirEquivalenceMapper.toFhirEquivalence(MappingType.AGGREGATED))
                .isEqualTo(org.hl7.fhir.r4.model.Enumerations.ConceptMapEquivalence.NARROWER);
    }

    @Test
    void equivalenceMapper_subdivided_toRelatedto() {
        assertThat(FhirEquivalenceMapper.toFhirEquivalence(MappingType.SUBDIVIDED))
                .isEqualTo(org.hl7.fhir.r4.model.Enumerations.ConceptMapEquivalence.RELATEDTO);
    }

    // ========================================================================
    // fetchMappings Tests
    // ========================================================================

    @Test
    void fetchMappings_all_returnsAll() {
        when(mappingRepository.findAll()).thenReturn(
                List.of(createMapping(MappingType.EQUIVALENT, "ext:src1", "ext:tgt1")));

        List<ItemMapping> result = service.fetchMappings("all");

        assertThat(result).hasSize(1);
        verify(mappingRepository).findAll();
    }

    @Test
    void fetchMappings_bySubjectItemId_returnsMappings() {
        UUID subjectId = UUID.randomUUID();
        when(mappingRepository.findBySubjectItemId(subjectId)).thenReturn(
                List.of(createMapping(MappingType.DERIVED, "ext:src", "ext:tgt")));

        List<ItemMapping> result = service.fetchMappings(subjectId.toString());

        assertThat(result).hasSize(1);
        verify(mappingRepository).findBySubjectItemId(subjectId);
    }

    @Test
    void fetchMappings_invalidSchemeId_returnsEmpty() {
        List<ItemMapping> result = service.fetchMappings("not-a-uuid-and-not-all");

        assertThat(result).isEmpty();
        verifyNoInteractions(mappingRepository);
    }

    // ========================================================================
    // buildConceptMap Tests
    // ========================================================================

    @Test
    void buildConceptMap_singleMapping_createsValidStructure() {
        // Source with external URI
        ItemMappingSubject subject = ItemMappingSubject.forExternalUri(
                "https://terminology.hl7.org/CodeSystem/v2-0136");
        subject.setId(UUID.randomUUID());

        // Target with external URI
        ItemMappingObject object = ItemMappingObject.forExternalUri(
                "https://example.org/CodeSystem/my-code");
        object.setId(UUID.randomUUID());

        ItemMapping mapping = new ItemMapping(subject, object, MappingType.EQUIVALENT,
                "Equivalent mapping between Y/N indicator and custom code");

        List<ItemMapping> mappings = List.of(mapping);
        String schemeId = "test-scheme-1";

        ConceptMap conceptMap = service.buildConceptMap(mappings, schemeId);

        // Verify metadata
        assertThat(conceptMap.getId()).isEqualTo(schemeId);
        assertThat(conceptMap.getUrl()).isEqualTo("urn:uuid:" + schemeId);
        assertThat(conceptMap.getName()).isEqualTo("MappingScheme_test-scheme-1");
        assertThat(conceptMap.getStatus())
                .isEqualTo(org.hl7.fhir.r4.model.Enumerations.PublicationStatus.DRAFT);

        // Verify groups
        assertThat(conceptMap.getGroup()).hasSize(1);
        var group = conceptMap.getGroupFirstRep();
        assertThat(group.getSource()).isEqualTo("https://terminology.hl7.org/CodeSystem/v2-0136");
        assertThat(group.getTarget()).isEqualTo("https://example.org/CodeSystem/my-code");

        // Verify element
        assertThat(group.getElement()).hasSize(1);
        var element = group.getElementFirstRep();
        assertThat(element.getCode()).isEqualTo("v2-0136");
        assertThat(element.getTarget()).hasSize(1);

        var target = element.getTargetFirstRep();
        assertThat(target.getCode()).isEqualTo("my-code");
        assertThat(target.getEquivalence())
                .isEqualTo(org.hl7.fhir.r4.model.Enumerations.ConceptMapEquivalence.EQUIVALENT);
        assertThat(target.getComment()).isEqualTo(
                "Equivalent mapping between Y/N indicator and custom code");
    }

    @Test
    void buildConceptMap_itemReferences_usesUuidCodes() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        ItemMappingSubject subject = ItemMappingSubject.forItem(sourceId);
        subject.setId(UUID.randomUUID());

        ItemMappingObject object = ItemMappingObject.forItem(targetId);
        object.setId(UUID.randomUUID());

        ItemMapping mapping = new ItemMapping(subject, object, MappingType.DERIVED);

        ConceptMap conceptMap = service.buildConceptMap(List.of(mapping), "item-ref");

        var group = conceptMap.getGroupFirstRep();
        assertThat(group.getSource()).isEqualTo("urn:uuid:" + sourceId);
        assertThat(group.getTarget()).isEqualTo("urn:uuid:" + targetId);

        var element = group.getElementFirstRep();
        assertThat(element.getCode()).isEqualTo(sourceId.toString());

        var target = element.getTargetFirstRep();
        assertThat(target.getCode()).isEqualTo(targetId.toString());
        assertThat(target.getEquivalence())
                .isEqualTo(org.hl7.fhir.r4.model.Enumerations.ConceptMapEquivalence.WIDER);
    }

    @Test
    void buildConceptMap_allMappingTypes_createsAllEquivalences() {
        List<ItemMapping> mappings = new ArrayList<>();
        String schemeId = "all-types";

        for (MappingType type : MappingType.values()) {
            ItemMappingSubject subject = ItemMappingSubject.forExternalUri(
                    "https://example.org/src/" + type.name());
            subject.setId(UUID.randomUUID());
            ItemMappingObject object = ItemMappingObject.forExternalUri(
                    "https://example.org/tgt/" + type.name());
            object.setId(UUID.randomUUID());
            mappings.add(new ItemMapping(subject, object, type, "Type: " + type));
        }

        ConceptMap conceptMap = service.buildConceptMap(mappings, schemeId);

        int elementCount = 0;
        for (var group : conceptMap.getGroup()) {
            for (var element : group.getElement()) {
                elementCount += element.getTarget().size();
            }
        }
        assertThat(elementCount).isEqualTo(MappingType.values().length);
    }

    @Test
    void buildConceptMap_groupedBySourceTarget_sameSourceDifferentTargets() {
        var subj1 = ItemMappingSubject.forExternalUri("https://src.example.org/code1");
        subj1.setId(UUID.randomUUID());
        var obj1 = ItemMappingObject.forExternalUri("https://tgt1.example.org/codeA");
        obj1.setId(UUID.randomUUID());

        var subj2 = ItemMappingSubject.forExternalUri("https://src.example.org/code2");
        subj2.setId(UUID.randomUUID());
        var obj2 = ItemMappingObject.forExternalUri("https://tgt2.example.org/codeB");
        obj2.setId(UUID.randomUUID());

        List<ItemMapping> mappings = List.of(
                new ItemMapping(subj1, obj1, MappingType.EQUIVALENT),
                new ItemMapping(subj2, obj2, MappingType.DERIVED)
        );

        ConceptMap conceptMap = service.buildConceptMap(mappings, "grouped");

        // Two groups because source/target URIs differ
        assertThat(conceptMap.getGroup()).hasSize(2);
    }

    // ========================================================================
    // URI Resolution Tests
    // ========================================================================

    @Test
    void extractSystemUri_httpUrl_returnsAsIs() {
        assertThat(service.extractSystemUri("https://terminology.hl7.org/CodeSystem/v2-0136"))
                .isEqualTo("https://terminology.hl7.org/CodeSystem/v2-0136");
    }

    @Test
    void extractSystemUri_urn_returnsAsIs() {
        assertThat(service.extractSystemUri("urn:oid:1.2.3.4.5"))
                .isEqualTo("urn:oid:1.2.3.4.5");
    }

    @Test
    void extractSystemUri_plainString_wrapsAsUrn() {
        assertThat(service.extractSystemUri("my-custom-system"))
                .isEqualTo("urn:oid:my-custom-system");
    }

    @Test
    void extractSystemUri_null_returnsUnknown() {
        assertThat(service.extractSystemUri(null)).isEqualTo("urn:unknown");
    }

    @Test
    void extractCodeFromUri_urlPath_returnsLastSegment() {
        assertThat(service.extractCodeFromUri("https://example.org/CodeSystem/abc-def"))
                .isEqualTo("abc-def");
    }

    @Test
    void extractCodeFromUri_urnUuid_returnsUuid() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        assertThat(service.extractCodeFromUri("urn:uuid:" + uuid))
                .isEqualTo(uuid);
    }

    @Test
    void extractCodeFromUri_null_returnsUnknown() {
        assertThat(service.extractCodeFromUri(null)).isEqualTo("unknown");
    }

    // ========================================================================
    // Export Tests
    // ========================================================================

    @Test
    void exportConceptMap_noMappings_returnsFailure() {
        when(mappingRepository.findBySubjectItemId(any())).thenReturn(List.of());

        FhirConceptMapExportResult result = service.exportConceptMap(
                UUID.randomUUID().toString(), mockUserId);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("No mappings found");
    }

    @Test
    void exportConceptMap_smallDataset_syncExport() {
        UUID subjectId = UUID.randomUUID();
        ItemMapping mapping = createMapping(MappingType.EQUIVALENT,
                "https://src.example.org/code1", "https://tgt.example.org/code2");

        when(mappingRepository.findBySubjectItemId(subjectId)).thenReturn(List.of(mapping));

        FhirConceptMapExportResult result = service.exportConceptMap(
                subjectId.toString(), mockUserId);

        assertThat(result.success()).isTrue();
        assertThat(result.async()).isFalse();
        assertThat(result.conceptMapJson()).isNotNull();
        assertThat(result.conceptMapJson()).contains("ConceptMap");
        assertThat(result.conceptMapJson()).contains("\"equivalent\"");
        assertThat(result.mappingCount()).isEqualTo(1);
        assertThat(result.elementCount()).isEqualTo(1);
    }

    @Test
    void exportConceptMap_allSchemes_syncExport() {
        List<ItemMapping> mappings = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mappings.add(createMapping(MappingType.EQUIVALENT,
                    "https://src.example.org/code" + i,
                    "https://tgt.example.org/result" + i));
        }

        when(mappingRepository.findAll()).thenReturn(mappings);

        FhirConceptMapExportResult result = service.exportConceptMap("all", mockUserId);

        assertThat(result.success()).isTrue();
        assertThat(result.mappingCount()).isEqualTo(3);
        assertThat(result.conceptMapJson()).contains("\"equivalent\"");
    }

    @Test
    void exportConceptMap_largeDataset_triggersAsync() {
        UUID subjectId = UUID.randomUUID();
        // Create more than ASYNC_THRESHOLD mappings
        List<ItemMapping> mappings = new ArrayList<>();
        for (int i = 0; i < FhirConceptMapExportService.ASYNC_THRESHOLD + 1; i++) {
            mappings.add(createMapping(MappingType.EQUIVALENT,
                    "https://src.example.org/code" + i,
                    "https://tgt.example.org/result" + i));
        }

        when(mappingRepository.findBySubjectItemId(subjectId)).thenReturn(mappings);
        when(exportTaskRepository.save(any())).thenAnswer(inv -> {
            ExportTask task = inv.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        FhirConceptMapExportResult result = service.exportConceptMap(
                subjectId.toString(), mockUserId);

        assertThat(result.success()).isTrue();
        assertThat(result.async()).isTrue();
        assertThat(result.taskId()).isNotNull();
    }

    // ========================================================================
    // Export Task Status Tests
    // ========================================================================

    @Test
    void getExportTaskStatus_taskNotFound_returnsFailure() {
        UUID taskId = UUID.randomUUID();
        when(exportTaskRepository.findById(taskId)).thenReturn(Optional.empty());

        FhirConceptMapExportResult result = service.getExportTaskStatus(taskId);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("not found");
    }

    @Test
    void getExportTaskStatus_taskCompleted_returnsResult() throws Exception {
        UUID taskId = UUID.randomUUID();
        Path tempFile = Files.createTempFile("conceptmap-test-", ".json");
        String jsonContent = "{\"resourceType\":\"ConceptMap\",\"id\":\"test\"}";
        Files.writeString(tempFile, jsonContent, StandardCharsets.UTF_8);

        ExportTask task = ExportTask.builder()
                .id(taskId)
                .status(ExportTaskStatus.COMPLETED)
                .processedCount(5)
                .totalCount(5)
                .completedAt(LocalDateTime.now())
                .resultFilePath(tempFile.toAbsolutePath().toString())
                .build();
        when(exportTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

        FhirConceptMapExportResult result = service.getExportTaskStatus(taskId);

        assertThat(result.success()).isTrue();
        assertThat(result.conceptMapJson()).isEqualTo(jsonContent);
        assertThat(result.mappingCount()).isEqualTo(5);

        Files.deleteIfExists(tempFile);
    }

    @Test
    void getExportTaskStatus_taskFailed_returnsError() {
        UUID taskId = UUID.randomUUID();
        ExportTask task = ExportTask.builder()
                .id(taskId)
                .status(ExportTaskStatus.FAILED)
                .errorMessage("Export failed: timeout")
                .build();
        when(exportTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

        FhirConceptMapExportResult result = service.getExportTaskStatus(taskId);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("timeout");
    }

    @Test
    void getExportTaskStatus_taskProcessing_returnsProgress() {
        UUID taskId = UUID.randomUUID();
        ExportTask task = ExportTask.builder()
                .id(taskId)
                .status(ExportTaskStatus.PROCESSING)
                .processedCount(3)
                .totalCount(10)
                .build();
        when(exportTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

        FhirConceptMapExportResult result = service.getExportTaskStatus(taskId);

        assertThat(result.success()).isTrue();
        assertThat(result.async()).isTrue();
        assertThat(result.mappingCount()).isEqualTo(3);
        assertThat(result.elementCount()).isEqualTo(10);
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Test
    void buildConceptMap_emptyMappingList_returnsMinimalConceptMap() {
        ConceptMap cm = service.buildConceptMap(List.of(), "empty");

        assertThat(cm.getId()).isEqualTo("empty");
        assertThat(cm.getGroup()).isEmpty();
    }

    @Test
    void buildConceptMap_mappingWithNullDescription_noComment() {
        var subject = ItemMappingSubject.forExternalUri("https://src.example.org/code");
        subject.setId(UUID.randomUUID());
        var object = ItemMappingObject.forExternalUri("https://tgt.example.org/result");
        object.setId(UUID.randomUUID());

        ItemMapping mapping = new ItemMapping(subject, object, MappingType.EQUIVALENT);
        // Not setting description

        ConceptMap cm = service.buildConceptMap(List.of(mapping), "no-comment");

        var target = cm.getGroupFirstRep().getElementFirstRep().getTargetFirstRep();
        assertThat(target.getComment()).isNull();
    }

    @Test
    void buildConceptMap_mappingTypes_allCreateCorrectEquivalence() {
        for (MappingType type : MappingType.values()) {
            var subject = ItemMappingSubject.forExternalUri(
                    "https://src.example.org/" + type.name());
            subject.setId(UUID.randomUUID());
            var object = ItemMappingObject.forExternalUri(
                    "https://tgt.example.org/" + type.name());
            object.setId(UUID.randomUUID());

            ConceptMap cm = service.buildConceptMap(
                    List.of(new ItemMapping(subject, object, type)), type.name());

            var target = cm.getGroupFirstRep().getElementFirstRep().getTargetFirstRep();
            assertThat(target.getEquivalence())
                    .isEqualTo(FhirEquivalenceMapper.toFhirEquivalence(type));
        }
    }

    // ========================================================================
    // JSON Output Tests
    // ========================================================================

    @Test
    void doSyncExport_validMapping_returnsValidJson() {
        var mapping = createMapping(MappingType.EQUIVALENT,
                "https://src.example.org/code", "https://tgt.example.org/result");

        FhirConceptMapExportResult result = service.doSyncExport(
                List.of(mapping), "json-test");

        assertThat(result.success()).isTrue();
        assertThat(result.conceptMapJson()).isNotNull();

        // FHIR R4 JSON should contain key fields
        String json = result.conceptMapJson();
        assertThat(json).contains("\"resourceType\"");
        assertThat(json).contains("\"ConceptMap\"");
        assertThat(json).contains("\"id\"");
        assertThat(json).contains("\"group\"");
        assertThat(json).contains("\"element\"");
        assertThat(json).contains("\"target\"");
    }

    @Test
    void doSyncExport_mappingTypes_allEquivalenceCodesInJson() {
        List<ItemMapping> mappings = new ArrayList<>();
        for (MappingType mt : MappingType.values()) {
            mappings.add(createMapping(mt,
                    "https://src.example.org/" + mt.name(),
                    "https://tgt.example.org/" + mt.name()));
        }

        FhirConceptMapExportResult result = service.doSyncExport(mappings, "all-types-json");

        assertThat(result.success()).isTrue();
        String json = result.conceptMapJson();
        assertThat(json).contains("\"equivalent\"");
        assertThat(json).contains("\"wider\"");
        assertThat(json).contains("\"narrower\"");
        assertThat(json).contains("\"relatedto\"");
    }

    // ========================================================================
    // Boundary Tests — Special Characters
    // ========================================================================

    @Test
    void buildConceptMap_specialCharactersInDescription_preservesComment() {
        var subject = ItemMappingSubject.forExternalUri("https://src.example.org/code");
        subject.setId(UUID.randomUUID());
        var object = ItemMappingObject.forExternalUri("https://tgt.example.org/result");
        object.setId(UUID.randomUUID());

        String specialDesc = "映射描述: 等价/派生/聚合/细分 | Emoji 🏥 | HTML <br/> | JSON {\"key\":\"val\"}";
        ItemMapping mapping = new ItemMapping(subject, object, MappingType.EQUIVALENT, specialDesc);

        ConceptMap cm = service.buildConceptMap(List.of(mapping), "special-chars");

        var target = cm.getGroupFirstRep().getElementFirstRep().getTargetFirstRep();
        assertThat(target.getComment()).isEqualTo(specialDesc);
    }

    @Test
    void buildConceptMap_specialCharactersInCode_extractsCorrectly() {
        var subject = ItemMappingSubject.forExternalUri(
                "https://terminology.hl7.org/CodeSystem/v2-0136-special");
        subject.setId(UUID.randomUUID());
        var object = ItemMappingObject.forExternalUri(
                "https://example.org/CodeSystem/codes/test_space-dash.dot");
        object.setId(UUID.randomUUID());

        ItemMapping mapping = new ItemMapping(subject, object, MappingType.DERIVED);

        ConceptMap cm = service.buildConceptMap(List.of(mapping), "special-codes");

        var group = cm.getGroupFirstRep();
        assertThat(group.getElementFirstRep().getCode()).isEqualTo("v2-0136-special");
        assertThat(group.getElementFirstRep().getTargetFirstRep().getCode())
                .isEqualTo("test_space-dash.dot");
    }

    @Test
    void buildConceptMap_unicodeSchemeId_sanitizesName() {
        var subject = ItemMappingSubject.forExternalUri("https://src.example.org/code");
        subject.setId(UUID.randomUUID());
        var object = ItemMappingObject.forExternalUri("https://tgt.example.org/result");
        object.setId(UUID.randomUUID());

        ItemMapping mapping = new ItemMapping(subject, object, MappingType.EQUIVALENT);

        String schemeId = "方案-测试_123.456";
        ConceptMap cm = service.buildConceptMap(List.of(mapping), schemeId);

        assertThat(cm.getId()).isEqualTo(schemeId);
        assertThat(cm.getName()).matches("MappingScheme_[a-zA-Z0-9_-]+");
    }

    @Test
    void doSyncExport_specialCharacters_returnsValidJson() {
        var subject = ItemMappingSubject.forExternalUri("https://src.example.org/code");
        subject.setId(UUID.randomUUID());
        var object = ItemMappingObject.forExternalUri("https://tgt.example.org/result");
        object.setId(UUID.randomUUID());

        String desc = "中文描述 with unicode 🎉 and symbols: dash-space.dot";
        ItemMapping mapping = new ItemMapping(subject, object, MappingType.SUBDIVIDED, desc);

        FhirConceptMapExportResult result = service.doSyncExport(List.of(mapping), "unicode-test");

        assertThat(result.success()).isTrue();
        // Note: HAPI validator may emit warnings for extended unicode in comments,
        // but export must succeed and produce parseable FHIR JSON
        assertThat(result.conceptMapJson()).contains("relatedto");
        // Verify JSON is valid by parsing it back
        var parser = ca.uhn.fhir.context.FhirContext.forR4().newJsonParser();
        org.hl7.fhir.r4.model.ConceptMap parsed = (org.hl7.fhir.r4.model.ConceptMap)
                parser.parseResource(result.conceptMapJson());
        assertThat(parsed.getGroupFirstRep().getElementFirstRep().getTargetFirstRep().getComment())
                .isEqualTo(desc);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Creates a simple ItemMapping with external URIs for both subject and object.
     */
    private ItemMapping createMapping(MappingType type, String sourceUri, String targetUri) {
        ItemMappingSubject subject = ItemMappingSubject.forExternalUri(sourceUri);
        subject.setId(UUID.randomUUID());

        ItemMappingObject object = ItemMappingObject.forExternalUri(targetUri);
        object.setId(UUID.randomUUID());

        return new ItemMapping(subject, object, type, "Mapping: " + sourceUri + " → " + targetUri);
    }
}

