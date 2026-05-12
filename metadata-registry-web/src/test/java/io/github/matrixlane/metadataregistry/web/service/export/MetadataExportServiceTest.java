package io.github.matrixlane.metadataregistry.web.service.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.StewardshipRecord;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.SubmissionRecord;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportRequest;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportResult;
import io.github.matrixlane.metadataregistry.web.entity.ExportTask;
import io.github.matrixlane.metadataregistry.web.entity.ExportTaskStatus;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ExportTaskRepository;
import io.github.matrixlane.metadataregistry.web.service.MetadataExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link MetadataExportService} 单元测试。
 *
 * <p>覆盖目标：</p>
 * <ul>
 *   <li>同步 JSON 导出（全部字段/筛选字段）</li>
 *   <li>过滤条件组合（状态/机构/时间范围/Item类型）</li>
 *   <li>空结果集导出</li>
 *   <li>超大结果集拦截</li>
 *   <li>自动切换异步阈值</li>
 *   <li>异步任务创建与执行</li>
 *   <li>CSV 导出</li>
 *   <li>流式 JSON 导出</li>
 *   <li>分片读取结果</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MetadataExportServiceTest {

    @Mock
    private AdministeredItemRepository repository;

    @Mock
    private ExportTaskRepository exportTaskRepository;

    private MetadataExportService exportService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private List<AdministeredItem> testItems;
    private MetadataExportRequest request;

    @BeforeEach
    void setUp() {
        exportService = new MetadataExportService(repository, exportTaskRepository, objectMapper);

        testItems = new ArrayList<>();

        // Create 3 test AdministeredItems with different states
        testItems.add(createTestItem("DataElement", RegistrationStatus.STANDARD,
                "TestElement1", "A test data element 1",
                "DE-001", "1.0", LocalDateTime.of(2025, 1, 15, 10, 0)));
        testItems.add(createTestItem("ObjectClass", RegistrationStatus.QUALIFIED,
                "TestObjectClass", "A test object class",
                "OC-001", "1.0", LocalDateTime.of(2025, 2, 20, 10, 0)));
        testItems.add(createTestItem("DataElementConcept", RegistrationStatus.CANDIDATE,
                "TestConcept", "A test data element concept",
                "DEC-001", "0.5", LocalDateTime.of(2025, 3, 25, 10, 0)));

        request = new MetadataExportRequest(
                null, null, null,
                null, null, null, null,
                null, null
        );
    }

    // =========================================================================
    // 1. 同步 JSON 导出
    // =========================================================================

    @Test
    @DisplayName("同步导出：返回全部字段的 JSON 结果")
    void export_shouldReturnAllItems() {
        when(repository.findAll(any(Specification.class))).thenReturn(testItems);

        MetadataExportResult result = exportService.export(request);

        assertThat(result).isNotNull();
        assertThat(result.items()).hasSize(3);
        assertThat(result.exportMetadata()).isNotNull();
        assertThat(result.exportMetadata().totalCount()).isEqualTo(3);
        assertThat(result.exportMetadata().schemaVersion()).isEqualTo("1.0.0");
        assertThat(result.exportMetadata().exportedAt()).isNotNull();
    }

    @Test
    @DisplayName("同步导出：字段筛选后只包含指定字段 + id")
    void export_withFieldFilter_shouldReturnOnlySpecifiedFields() throws Exception {
        when(repository.findAll(any(Specification.class))).thenReturn(testItems.subList(0, 1));

        MetadataExportRequest filteredRequest = new MetadataExportRequest(
                null, null, null, null, null, null, null,
                List.of("itemType", "itemLabel"), null
        );

        String json = exportService.exportToJson(filteredRequest);

        // Verify only itemType, itemLabel, and id are present
        assertThat(json).contains("\"itemType\"");
        assertThat(json).contains("\"itemLabel\"");
        assertThat(json).contains("\"id\"");
        assertThat(json).doesNotContain("\"itemDefinition\"");
        assertThat(json).doesNotContain("\"identifiers\"");
    }

    @Test
    @DisplayName("同步导出：空结果集返回合法的空数组")
    void export_emptyResult_shouldReturnEmptyArray() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());

        MetadataExportResult result = exportService.export(request);

        assertThat(result.items()).isEmpty();
        assertThat(result.exportMetadata().totalCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("同步导出：超大结果集应抛出异常")
    void export_tooManyResults_shouldThrow() {
        List<AdministeredItem> largeList = new ArrayList<>();
        for (int i = 0; i < 15000; i++) {
            largeList.add(createTestItem("DataElement", RegistrationStatus.STANDARD,
                    "Item" + i, "desc", "ID-" + i, "1.0", LocalDateTime.now()));
        }

        when(repository.findAll(any(Specification.class))).thenReturn(largeList);

        assertThatThrownBy(() -> exportService.export(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超过最大限制");
    }

    @Test
    @DisplayName("同步导出：返回结果包含正确的 Item 类型")
    void export_itemTypesAreCorrect() {
        when(repository.findAll(any(Specification.class))).thenReturn(testItems);

        MetadataExportResult result = exportService.export(request);

        assertThat(result.items()).hasSize(3);
        assertThat(result.items().get(0).itemType()).isEqualTo("Context");
    }

    @Test
    @DisplayName("同步导出：导出结果 JSON 包含导出元信息")
    void export_metadataIsPresentInJson() throws Exception {
        when(repository.findAll(any(Specification.class))).thenReturn(testItems);

        String json = exportService.exportToJson(request);

        assertThat(json).contains("\"exportMetadata\"");
        assertThat(json).contains("\"schemaVersion\"");
        assertThat(json).contains("\"exportedAt\"");
        assertThat(json).contains("\"totalCount\"");
        assertThat(json).contains("\"appliedFilters\"");
    }

    // =========================================================================
    // 2. 过滤条件
    // =========================================================================

    @Test
    @DisplayName("过滤条件：按注册状态过滤")
    void export_filterByStatus_shouldReturnMatchingItems() {
        when(repository.findAll(any(Specification.class))).thenReturn(testItems.subList(0, 1));

        MetadataExportRequest statusRequest = new MetadataExportRequest(
                RegistrationStatus.STANDARD, null, null,
                null, null, null, null, null, null
        );

        MetadataExportResult result = exportService.export(statusRequest);

        assertThat(result.items()).hasSize(1);
        // Item type is "Context" since we use Context as the concrete Item
        assertThat(result.items().get(0).registrationState().status()).isEqualTo(RegistrationStatus.STANDARD);
    }

    @Test
    @DisplayName("过滤条件：按 Item 类型过滤（匹配）")
    void export_filterByItemType_shouldReturnMatchingTypes() {
        when(repository.findAll(any(Specification.class))).thenReturn(testItems);

        // All items use Context as concrete class, so filtering for "Context" should return all 3
        MetadataExportRequest typeRequest = new MetadataExportRequest(
                null, null, null, null, null, null, null,
                null, List.of("Context")
        );

        MetadataExportResult result = exportService.export(typeRequest);

        assertThat(result.items()).hasSize(3);
        result.items().forEach(item -> assertThat(item.itemType()).isEqualTo("Context"));
    }

    @Test
    @DisplayName("过滤条件：按 Item 类型过滤（不匹配）")
    void export_filterByItemType_noMatch_shouldReturnEmpty() {
        when(repository.findAll(any(Specification.class))).thenReturn(testItems);

        MetadataExportRequest typeRequest = new MetadataExportRequest(
                null, null, null, null, null, null, null,
                null, List.of("NonExistentType")
        );

        MetadataExportResult result = exportService.export(typeRequest);

        assertThat(result.items()).isEmpty();
    }

    @Test
    @DisplayName("过滤条件：按时间范围过滤（创建时间）")
    void export_filterByTimeRange_shouldReturnMatchingItems() {
        when(repository.findAll(any(Specification.class))).thenReturn(testItems.subList(0, 2));

        MetadataExportRequest timeRequest = new MetadataExportRequest(
                null, null, null,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 3, 1, 0, 0),
                null, null, null, null
        );

        MetadataExportResult result = exportService.export(timeRequest);

        assertThat(result.items()).hasSize(2);
    }

    @Test
    @DisplayName("过滤条件：多重条件组合（状态+类型）")
    void export_multiFilterCombination_shouldReturnIntersection() {
        // Simulate DB-level filtering by returning only items matching status STANDARD
        when(repository.findAll(any(Specification.class))).thenReturn(testItems.subList(0, 1));

        MetadataExportRequest multiRequest = new MetadataExportRequest(
                RegistrationStatus.STANDARD, null, null,
                null, null, null, null, null,
                List.of("Context")
        );

        MetadataExportResult result = exportService.export(multiRequest);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).registrationState().status()).isEqualTo(RegistrationStatus.STANDARD);
    }

    // =========================================================================
    // 3. 阈值与异步
    // =========================================================================

    @Test
    @DisplayName("阈值判断：小数据量不需要异步")
    void estimateCount_smallData_shouldNotRequireAsync() {
        when(repository.count(any(Specification.class))).thenReturn(100L);

        boolean shouldAsync = exportService.shouldUseAsyncExport(request);

        assertThat(shouldAsync).isFalse();
    }

    @Test
    @DisplayName("阈值判断：大数据量需要异步")
    void estimateCount_largeData_shouldRequireAsync() {
        when(repository.count(any(Specification.class))).thenReturn(20000L);

        boolean shouldAsync = exportService.shouldUseAsyncExport(request);

        assertThat(shouldAsync).isTrue();
    }

    @Test
    @DisplayName("异步任务创建：应返回 PENDING 任务")
    void createAsyncExportTask_shouldReturnPendingTask() {
        UUID userId = UUID.randomUUID();
        when(repository.count(any(Specification.class))).thenReturn(50L);
        when(exportTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExportTask task = exportService.createAsyncExportTask(request, userId);

        assertThat(task.getStatus()).isEqualTo(ExportTaskStatus.PENDING);
        assertThat(task.getCreatedBy()).isEqualTo(userId);
        assertThat(task.getTotalCount()).isEqualTo(50);
        assertThat(task.getCreatedAt()).isNotNull();
        assertThat(task.getExpiresAt()).isAfter(task.getCreatedAt());
    }

    // =========================================================================
    // 4. CSV 导出
    // =========================================================================

    @Test
    @DisplayName("CSV 导出：应包含正确数量的数据行")
    void exportCsvToStream_shouldProduceValidCsv() throws Exception {
        when(repository.findAll(any(Specification.class))).thenReturn(testItems);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportCsvToStream(request, baos);

        String csv = baos.toString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");

        // Header + 3 data rows
        assertThat(lines).hasSize(4);
        assertThat(lines[0]).contains("ID,ItemType,ItemLabel");
        assertThat(lines[1]).contains("Context");
        assertThat(lines[2]).contains("Context");
    }

    @Test
    @DisplayName("CSV 导出：空结果仅包含表头")
    void exportCsvToStream_emptyResult_shouldHaveOnlyHeader() throws Exception {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportCsvToStream(request, baos);

        String csv = baos.toString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");

        assertThat(lines).hasSize(1);
    }

    // =========================================================================
    // 5. 流式 JSON 导出
    // =========================================================================

    @Test
    @DisplayName("流式导出：应输出合法的 JSON 结构")
    void exportJsonToStream_shouldProduceValidJson() throws Exception {
        when(repository.findAll(any(Specification.class))).thenReturn(testItems);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportJsonToStream(request, baos);

        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"exportMetadata\"");
        assertThat(json).contains("\"items\"");
        assertThat(json).contains("\"schemaVersion\":\"1.0.0\"");
    }

    // =========================================================================
    // 6. 分片读取
    // =========================================================================

    @Test
    @DisplayName("分片读取：未完成的任务应报错")
    void readExportChunk_incompleteTask_shouldThrow() {
        ExportTask incompleteTask = ExportTask.builder()
                .id(UUID.randomUUID())
                .status(ExportTaskStatus.PROCESSING)
                .build();

        when(exportTaskRepository.findById(incompleteTask.getId()))
                .thenReturn(Optional.of(incompleteTask));

        assertThatThrownBy(() -> exportService.readExportChunk(incompleteTask.getId(), 0, 1024))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("导出任务未完成");
    }

    @Test
    @DisplayName("分片读取：任务不存在应报错")
    void readExportChunk_taskNotFound_shouldThrow() {
        UUID nonExistentId = UUID.randomUUID();
        when(exportTaskRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> exportService.readExportChunk(nonExistentId, 0, 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("导出任务不存在");
    }

    // =========================================================================
    // 辅助方法
    // =========================================================================

    private AdministeredItem createTestItem(String itemType, RegistrationStatus status,
                                             String label, String definitionText,
                                             String identifier, String version,
                                             LocalDateTime createdDate) {
        AdministeredItem ai = new AdministeredItem();
        ai.setId(UUID.randomUUID());

        // Item - use Context as a concrete Item subclass
        Context item = new Context();
        item.setId(UUID.randomUUID());

        // Designation
        Designation designation = new Designation();
        designation.setSign(createSign(label));
        item.setDesignations(List.of(designation));

        // Definition
        Definition definition = new Definition();
        definition.setText(Text.of(definitionText));
        item.setDefinitions(List.of(definition));

        ai.setItem(item);

        // RegistrationState
        RegistrationState state = new RegistrationState();
        state.setRegistrationStatus(status);
        state.setEffectiveDate(createdDate);
        state.setAdministrativeNote("Auto-generated test item");
        ai.setRegistrationState(state);

        // ScopedIdentifier
        ScopedIdentifier scopedId = new ScopedIdentifier();
        scopedId.setIdentifier(identifier);
        scopedId.setVersion(version);
        Namespace ns = new Namespace();
        ns.setId(UUID.randomUUID());
        scopedId.setNamespace(ns);
        ai.setScopedIdentifiers(List.of(scopedId));

        // RegistrationAuthority
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setName(List.of(createSign("Test Registration Authority")));
        ra.setName(List.of(createSign("Test Registration Authority")));
        ai.setRegistrationAuthority(ra);

        // StewardshipRecord
        StewardshipRecord sr = new StewardshipRecord();
        Organization stewardOrg = new Organization();
        stewardOrg.setId(UUID.randomUUID());
        stewardOrg.setName(List.of(createSign("Steward Org")));
        sr.setOrganization(stewardOrg);
        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        sr.setContact(contact);
        ai.setStewardshipRecord(sr);

        // SubmissionRecords
        SubmissionRecord subRec = new SubmissionRecord();
        Organization subOrg = new Organization();
        subOrg.setId(UUID.randomUUID());
        subOrg.setName(List.of(createSign("Submitter Org")));
        subRec.setOrganization(subOrg);
        ai.setSubmissionRecords(List.of(subRec));

        // Timestamps
        ai.setCreationDateTime(createdDate);
        ai.setLastChangeDateTime(createdDate.plusDays(30));
        ai.setChangeDescription("Initial registration");

        return ai;
    }

    private static Sign createSign(String value) {
        Sign sign = new Sign();
        sign.setValue(value);
        return sign;
    }
}
