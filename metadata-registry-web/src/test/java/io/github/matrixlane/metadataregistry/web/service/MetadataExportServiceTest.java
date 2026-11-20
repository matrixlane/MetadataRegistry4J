package io.github.matrixlane.metadataregistry.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.dto.export.AdministeredItemExport;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportRequest;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportResult;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MetadataExportService} 单元测试。
 *
 * <p>批量导出走原生 SQL 标量投影（不经 JPA Item 实体图，规避 JOINED 根多态 JOIN
 * 在 SQLite 下超 64 表上限的问题）。本测试覆盖：
 *
 * <ul>
 *   <li>导出 SQL 组装：过滤条件、类型 EXISTS 下推、未知类型 1=0；</li>
 *   <li>行映射：administrative_status ORDINAL 转换、联系人名称优先级；</li>
 *   <li>标识符 / 提交组织批量副查询回填；</li>
 *   <li>同步导出字段白名单、超大结果集拦截、CSV / 流式 JSON 结构；</li>
 *   <li>异步阈值判断（repository.count 口径不变）。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MetadataExportServiceTest {

    @Mock
    private AdministeredItemRepository repository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private ResultSet resultSet;

    private final DatabaseEnvironment env = new DatabaseEnvironment("", "jdbc:sqlite::memory:", "", "");
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MetadataExportService exportService;

    private static final UUID ITEM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        exportService = new MetadataExportService(repository, jdbcTemplate, env, objectMapper);
    }

    // =========================================================================
    // 1. 导出 SQL 组装
    // =========================================================================

    @Test
    @DisplayName("SQL 组装：无过滤条件时参数为空且含全量类型推导 CASE")
    void buildExportQuery_noFilters() {
        MetadataExportService.ExportQuery query = exportService.buildExportQuery(baseRequest());

        assertThat(query.params()).isEmpty();
        assertThat(query.sql()).contains("FROM \"administered_item\" ai");
        assertThat(query.sql()).contains("JOIN \"registered_item\" ri ON ri.id = ai.id");
        assertThat(query.sql()).contains("'ValueDomain'");
        assertThat(query.sql()).contains("ORDER BY ai.creation_date_time");
    }

    @Test
    @DisplayName("SQL 组装：注册状态过滤")
    void buildExportQuery_statusFilter() {
        MetadataExportRequest request = new MetadataExportRequest(
                RegistrationStatus.STANDARD, null, null,
                null, null, null, null, null, null);

        MetadataExportService.ExportQuery query = exportService.buildExportQuery(request);

        assertThat(query.sql()).contains("AND rs.registration_status = ?");
        assertThat(query.params()).containsExactly("STANDARD");
    }

    @Test
    @DisplayName("SQL 组装：类型过滤下推为 EXISTS 且 SELECT 用常量类型")
    void buildExportQuery_typeFilter_pushesExists() {
        MetadataExportRequest request = new MetadataExportRequest(
                null, null, null, null, null, null, null, null,
                List.of("ValueDomain"));

        MetadataExportService.ExportQuery query = exportService.buildExportQuery(request);

        assertThat(query.sql()).contains(
                "EXISTS (SELECT 1 FROM \"value_domain\" sub WHERE sub.id = i.id)");
        assertThat(query.sql()).contains("'ValueDomain' AS item_type");
        assertThat(query.sql()).doesNotContain("WHEN EXISTS (SELECT 1 FROM \"namespace\"");
    }

    @Test
    @DisplayName("SQL 组装：类型过滤大小写不敏感")
    void buildExportQuery_typeFilter_caseInsensitive() {
        MetadataExportRequest request = new MetadataExportRequest(
                null, null, null, null, null, null, null, null,
                List.of("valuedomain", "DATAELEMENT"));

        MetadataExportService.ExportQuery query = exportService.buildExportQuery(request);

        assertThat(query.sql()).contains("\"value_domain\" sub");
        assertThat(query.sql()).contains("\"data_element\" sub");
    }

    @Test
    @DisplayName("SQL 组装：全部类型名未知时 1=0 无命中")
    void buildExportQuery_unknownTypeOnly_noMatch() {
        MetadataExportRequest request = new MetadataExportRequest(
                null, null, null, null, null, null, null, null,
                List.of("NonExistentType"));

        MetadataExportService.ExportQuery query = exportService.buildExportQuery(request);

        assertThat(query.sql()).contains("AND 1=0");
        assertThat(query.sql()).doesNotContain("\"value_domain\" sub");
    }

    @Test
    @DisplayName("SQL 组装：已知与未知类型混合时仅保留已知")
    void buildExportQuery_mixedTypes_keepsKnownOnly() {
        MetadataExportRequest request = new MetadataExportRequest(
                null, null, null, null, null, null, null, null,
                List.of("ValueDomain", "NonExistentType"));

        MetadataExportService.ExportQuery query = exportService.buildExportQuery(request);

        assertThat(query.sql()).contains("\"value_domain\" sub");
        assertThat(query.sql()).doesNotContain("AND 1=0");
    }

    @Test
    @DisplayName("SQL 组装：UUID 过滤参数按方言转换（SQLite 为 byte[16]）")
    void buildExportQuery_uuidFilters() {
        UUID raId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        MetadataExportRequest request = new MetadataExportRequest(
                null, raId, orgId, null, null, null, null, null, null);

        MetadataExportService.ExportQuery query = exportService.buildExportQuery(request);

        assertThat(query.sql()).contains("AND ai.registration_authority_id = ?");
        assertThat(query.sql()).contains("AND sr.organization_id = ?");
        assertThat(query.params()).hasSize(2);
        assertThat(query.params().get(0)).isInstanceOf(byte[].class);
        assertThat(query.params().get(1)).isInstanceOf(byte[].class);
    }

    @Test
    @DisplayName("SQL 组装：时间范围过滤参数为 Timestamp")
    void buildExportQuery_timeRangeFilters() {
        LocalDateTime after = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime before = LocalDateTime.of(2025, 12, 31, 23, 59);
        MetadataExportRequest request = new MetadataExportRequest(
                null, null, null, after, before, after, before, null, null);

        MetadataExportService.ExportQuery query = exportService.buildExportQuery(request);

        assertThat(query.sql()).contains("AND ai.creation_date_time >= ?");
        assertThat(query.sql()).contains("AND ai.creation_date_time <= ?");
        assertThat(query.sql()).contains("AND ai.last_change_date_time >= ?");
        assertThat(query.sql()).contains("AND ai.last_change_date_time <= ?");
        assertThat(query.params()).allMatch(p -> p instanceof Timestamp);
    }

    // =========================================================================
    // 2. 行映射与副查询回填
    // =========================================================================

    @Test
    @DisplayName("行映射：全字段映射 + administrative_status 标准值文本透传")
    void queryExports_mapsRowAndAssembles() throws Exception {
        stubMainQueryViaCapturedMapper();
        stubIdentifierQuery(ITEM_ID, "EXP-001", "1.0", UUID.randomUUID(), "测试命名空间");
        stubSubmissionQuery(ITEM_ID, "提交机构甲");
        stubResultSetDefaults("联系人张三", "initial");

        List<AdministeredItemExport> items = exportService.queryExports(baseRequest());

        assertThat(items).hasSize(1);
        AdministeredItemExport export = items.get(0);
        assertThat(export.id()).isEqualTo(ITEM_ID);
        assertThat(export.itemType()).isEqualTo("ValueDomain");
        assertThat(export.itemLabel()).isEqualTo("导出测试值域");
        assertThat(export.itemDefinition()).isEqualTo("定义文本");
        assertThat(export.registrationState().status()).isEqualTo(RegistrationStatus.RECORDED);
        // administrative_status 列存标准值文本（AdministrativeStatusConverter），直接透传
        assertThat(export.registrationState().administrativeStatus()).isEqualTo("initial");
        assertThat(export.registrationAuthority().name()).isEqualTo("测试注册机构");
        assertThat(export.stewardship().organizationName()).isEqualTo("监管机构甲");
        // 联系人名称优先取 individual.name
        assertThat(export.stewardship().contactName()).isEqualTo("联系人张三");
        assertThat(export.identifiers()).hasSize(1);
        assertThat(export.identifiers().get(0).identifier()).isEqualTo("EXP-001");
        assertThat(export.identifiers().get(0).namespaceLabel()).isEqualTo("测试命名空间");
        assertThat(export.submissionOrganizations()).containsExactly("提交机构甲");
        assertThat(export.origin()).isEqualTo("测试来源");
    }

    @Test
    @DisplayName("行映射：联系人无 individual 时回退机构名；administrative_status 为空时导出 null")
    void queryExports_contactFallbackAndNullAdminStatus() throws Exception {
        stubMainQueryViaCapturedMapper();
        stubResultSetDefaults(null, null);

        List<AdministeredItemExport> items = exportService.queryExports(baseRequest());

        assertThat(items.get(0).stewardship().contactName()).isEqualTo("联系人所属机构");
        assertThat(items.get(0).registrationState().administrativeStatus()).isNull();
    }

    @Test
    @DisplayName("空结果：不触发标识符/提交组织副查询")
    void queryExports_emptyResult_skipsSubQueries() {
        stubMainQuery(List.of());

        List<AdministeredItemExport> items = exportService.queryExports(baseRequest());

        assertThat(items).isEmpty();
        verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class), any(Object[].class));
        verify(jdbcTemplate, never()).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
    }

    // =========================================================================
    // 3. 同步导出行为
    // =========================================================================

    @Test
    @DisplayName("同步导出：返回全部条目与导出元信息")
    void export_shouldReturnAllItems() {
        stubMainQuery(List.of(sampleExportRow()));
        stubNoIdentifierQuery();
        stubNoSubmissionQuery();

        MetadataExportResult result = exportService.export(baseRequest());

        assertThat(result.items()).hasSize(1);
        assertThat(result.exportMetadata().totalCount()).isEqualTo(1);
        assertThat(result.exportMetadata().schemaVersion()).isEqualTo("1.0.0");
        assertThat(result.exportMetadata().exportedAt()).isNotNull();
    }

    @Test
    @DisplayName("同步导出：字段筛选后只包含指定字段 + id")
    void export_withFieldFilter_shouldReturnOnlySpecifiedFields() throws Exception {
        stubMainQuery(List.of(sampleExportRow()));
        stubNoIdentifierQuery();
        stubNoSubmissionQuery();

        MetadataExportRequest filteredRequest = new MetadataExportRequest(
                null, null, null, null, null, null, null,
                List.of("itemType", "itemLabel"), null);

        String json = exportService.exportToJson(filteredRequest);

        assertThat(json).contains("\"itemType\"");
        assertThat(json).contains("\"itemLabel\"");
        assertThat(json).contains("\"id\"");
        assertThat(json).doesNotContain("\"itemDefinition\"");
        assertThat(json).doesNotContain("\"identifiers\"");
    }

    @Test
    @DisplayName("同步导出：超大结果集应抛出异常")
    void export_tooManyResults_shouldThrow() {
        List<MetadataExportService.ExportRow> largeList = new ArrayList<>();
        for (int i = 0; i < 10_001; i++) {
            largeList.add(sampleExportRow());
        }
        stubMainQuery(largeList);

        assertThatThrownBy(() -> exportService.export(baseRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超过最大限制");
    }

    @Test
    @DisplayName("阈值判断：小数据量不需要异步")
    void estimateCount_smallData_shouldNotRequireAsync() {
        when(repository.count(any(Specification.class))).thenReturn(100L);

        assertThat(exportService.shouldUseAsyncExport(baseRequest())).isFalse();
    }

    @Test
    @DisplayName("阈值判断：大数据量需要异步")
    void estimateCount_largeData_shouldRequireAsync() {
        when(repository.count(any(Specification.class))).thenReturn(20_000L);

        assertThat(exportService.shouldUseAsyncExport(baseRequest())).isTrue();
    }

    // =========================================================================
    // 4. CSV / 流式 JSON
    // =========================================================================

    @Test
    @DisplayName("CSV 导出：BOM + 表头 + 数据行")
    void exportCsvToStream_shouldProduceValidCsv() throws Exception {
        stubMainQuery(List.of(sampleExportRow()));
        stubIdentifierQuery(ITEM_ID, "EXP-001", "1.0", UUID.randomUUID(), "ns");
        stubNoSubmissionQuery();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportCsvToStream(baseRequest(), baos);

        String csv = baos.toString(StandardCharsets.UTF_8);
        assertThat(csv.charAt(0)).isEqualTo('\ufeff');
        String[] lines = csv.substring(1).split("\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).contains("ID,ItemType,ItemLabel");
        assertThat(lines[1]).contains("ValueDomain");
        assertThat(lines[1]).contains("EXP-001");
        assertThat(lines[1]).contains("RECORDED");
    }

    @Test
    @DisplayName("CSV 导出：空结果仅包含表头")
    void exportCsvToStream_emptyResult_shouldHaveOnlyHeader() throws Exception {
        stubMainQuery(List.of());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportCsvToStream(baseRequest(), baos);

        String[] lines = baos.toString(StandardCharsets.UTF_8).substring(1).split("\n");
        assertThat(lines).hasSize(1);
    }

    @Test
    @DisplayName("流式导出：应输出合法的 JSON 结构")
    void exportJsonToStream_shouldProduceValidJson() throws Exception {
        stubMainQuery(List.of(sampleExportRow()));
        stubIdentifierQuery(ITEM_ID, "EXP-001", "1.0", UUID.randomUUID(), "ns");
        stubNoSubmissionQuery();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.exportJsonToStream(baseRequest(), baos);

        String json = baos.toString(StandardCharsets.UTF_8);
        assertThat(json).contains("\"exportMetadata\"");
        assertThat(json).contains("\"items\"");
        assertThat(json).contains("\"schemaVersion\":\"1.0.0\"");
        assertThat(json).contains("导出测试值域");
    }

    // =========================================================================
    // 辅助方法
    // =========================================================================

    private static MetadataExportRequest baseRequest() {
        return new MetadataExportRequest(null, null, null, null, null, null, null, null, null);
    }

    /** 主查询打桩：直接返回给定导出行。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubMainQuery(List<MetadataExportService.ExportRow> rows) {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(rows);
    }

    /**
     * 主查询打桩：捕获 RowMapper 并将其应用到桩 ResultSet，
     * 从而驱动私有 mapRow 的真实列映射逻辑。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubMainQueryViaCapturedMapper() {
        ArgumentCaptor<RowMapper> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), captor.capture(), any(Object[].class)))
                .thenAnswer(inv -> List.of(captor.getValue().mapRow(resultSet, 1)));
    }

    /** 标识符副查询打桩：为给定条目产出一行标识符。 */
    private void stubIdentifierQuery(UUID ownerId, String identifier, String version,
                                     UUID namespaceId, String namespaceLabel) {
        doAnswer(inv -> {
            RowCallbackHandler handler = inv.getArgument(1);
            when(resultSet.getObject("identified_item_id")).thenReturn(ownerId);
            when(resultSet.getString("identifier")).thenReturn(identifier);
            when(resultSet.getString("version")).thenReturn(version);
            when(resultSet.getObject("namespace_id")).thenReturn(namespaceId);
            when(resultSet.getString("ns_label")).thenReturn(namespaceLabel);
            handler.processRow(resultSet);
            return null;
        }).when(jdbcTemplate).query(contains("scoped_identifier"),
                any(RowCallbackHandler.class), any(Object[].class));
    }

    private void stubNoIdentifierQuery() {
        doAnswer(inv -> null).when(jdbcTemplate)
                .query(contains("scoped_identifier"), any(RowCallbackHandler.class), any(Object[].class));
    }

    /** 提交组织副查询打桩：为给定条目产出一行提交机构名。 */
    private void stubSubmissionQuery(UUID ownerId, String orgName) {
        doAnswer(inv -> {
            RowCallbackHandler handler = inv.getArgument(1);
            when(resultSet.getObject("registered_item_id")).thenReturn(ownerId);
            when(resultSet.getString("org_name")).thenReturn(orgName);
            handler.processRow(resultSet);
            return null;
        }).when(jdbcTemplate).query(contains("submission_record"),
                any(RowCallbackHandler.class), any(Object[].class));
    }

    private void stubNoSubmissionQuery() {
        doAnswer(inv -> null).when(jdbcTemplate)
                .query(contains("submission_record"), any(RowCallbackHandler.class), any(Object[].class));
    }

    /** 桩 ResultSet 默认值（对应私有 mapRow 的全部列）。 */
    private void stubResultSetDefaults(String contactIndName, String adminStatus) throws Exception {
        when(resultSet.getObject("id")).thenReturn(ITEM_ID);
        when(resultSet.getString("item_type")).thenReturn("ValueDomain");
        when(resultSet.getString("item_label")).thenReturn("导出测试值域");
        when(resultSet.getString("item_definition")).thenReturn("定义文本");
        when(resultSet.getString("registration_status")).thenReturn("RECORDED");
        when(resultSet.getTimestamp("effective_date"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0)));
        when(resultSet.getTimestamp("until_date")).thenReturn(null);
        when(resultSet.getString("administrative_note")).thenReturn("note");
        when(resultSet.getString("unresolved_issue")).thenReturn(null);
        when(resultSet.getString("administrative_status")).thenReturn(adminStatus);
        when(resultSet.getObject("ra_id")).thenReturn(UUID.randomUUID());
        when(resultSet.getString("ra_name")).thenReturn("测试注册机构");
        when(resultSet.getObject("st_org_id")).thenReturn(UUID.randomUUID());
        when(resultSet.getString("st_org_name")).thenReturn("监管机构甲");
        when(resultSet.getObject("st_contact_id")).thenReturn(UUID.randomUUID());
        when(resultSet.getString("st_contact_ind_name")).thenReturn(contactIndName);
        when(resultSet.getString("st_contact_org_name")).thenReturn("联系人所属机构");
        when(resultSet.getTimestamp("creation_date_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 1, 1, 0, 0)));
        when(resultSet.getTimestamp("last_change_date_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 2, 1, 0, 0)));
        when(resultSet.getString("change_description")).thenReturn("变更说明");
        when(resultSet.getString("explanatory_comment")).thenReturn("解释注释");
        when(resultSet.getString("origin")).thenReturn("测试来源");
    }

    /** 不经 ResultSet 的样例导出行（组装/上限类用例用）。 */
    private static MetadataExportService.ExportRow sampleExportRow() {
        return new MetadataExportService.ExportRow(
                ITEM_ID,
                "ValueDomain",
                "导出测试值域",
                "定义文本",
                RegistrationStatus.RECORDED,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                null,
                "note",
                null,
                "initial",
                UUID.randomUUID(),
                "测试注册机构",
                UUID.randomUUID(),
                "监管机构甲",
                UUID.randomUUID(),
                "联系人张三",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 2, 1, 0, 0),
                "变更说明",
                "解释注释",
                "测试来源");
    }
}
