package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.AdministeredItemResponse;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MetadataSearchService}'s JdbcTemplate-based native SQL
 * search. The service was rewritten from a repository/Specification design to
 * native SQL to bypass SQL Server's 4096-column limit on polymorphic joins;
 * these tests mock {@link JdbcTemplate} accordingly.
 *
 * <p>smart-item-picker：覆盖精确 identifier 匹配、namespaceId 限定、type 多选
 * 过滤、itemType CASE 推导、matchType 字段填充等新行为。
 */
@ExtendWith(MockitoExtension.class)
class MetadataSearchServiceTest {

    private static final Pattern SQL_GLUE =
            Pattern.compile("[A-Za-z0-9_](LEFT|RIGHT|INNER|JOIN|WHERE|ORDER)\\b");

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DatabaseEnvironment env;

    private MetadataSearchService searchService;
    private AdministeredItemResponse sampleResponse;

    @BeforeEach
    void setUp() {
        lenient().when(env.vendor()).thenReturn(DatabaseVendor.SQLSERVER);
        lenient().when(env.qualify(anyString())).thenAnswer(inv -> "mdr." + inv.getArgument(0));
        lenient().when(env.qualifyAuto(anyString())).thenAnswer(inv -> "mdr." + inv.getArgument(0));
        searchService = new MetadataSearchService(jdbcTemplate, env);
        sampleResponse = new AdministeredItemResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "DataElement",
                "患者姓名",
                "DE-PATIENT-NAME",
                "1.0",
                UUID.randomUUID(),
                "默认命名空间",
                UUID.randomUUID(),
                "测试注册机构",
                RegistrationStatus.STANDARD,
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                "EXACT",
                null);
    }

    @SuppressWarnings("unchecked")
    private void stubCountAndRows(long total, List<AdministeredItemResponse> rows) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(total);
        lenient().when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(rows);
    }

    private Page<AdministeredItemResponse> searchDefault(String keyword, String type,
                                                       RegistrationStatus status,
                                                       UUID contextId,
                                                       UUID namespaceId,
                                                       boolean exactMatch,
                                                       String identifier,
                                                       String namespaceShorthand) {
        return search(keyword, type, status, contextId, namespaceId, exactMatch, identifier, namespaceShorthand,
                PageRequest.of(0, 20));
    }

    private Page<AdministeredItemResponse> search(String keyword, String type,
                                                RegistrationStatus status,
                                                UUID contextId,
                                                UUID namespaceId,
                                                boolean exactMatch,
                                                String identifier,
                                                String namespaceShorthand,
                                                org.springframework.data.domain.Pageable pageable) {
        return searchService.search(keyword, type, status, contextId, null,
                namespaceId, exactMatch, identifier, namespaceShorthand, pageable);
    }
    @Test
    void search_countAndSelectSql_joinFragmentsHaveSpaceBoundaries() {
        // 回归：count SQL 曾因 "rs.id" + "LEFT JOIN" 缺少空格拼接出 rs.idLEFT 导致
        // SQLite/SQL Server 全部搜索请求 500（no such column: rs.idLEFT）。
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchDefault(
                null, "ValueDomain", null, null, null, true, "VD.00001", "MD");

        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(countSql.capture(), eq(Long.class), any(Object[].class));
        String count = countSql.getValue();
        assertThat(count).contains("rs.id LEFT JOIN");
        assertThat(count).doesNotContain("rs.idLEFT");

        ArgumentCaptor<String> selectSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(selectSql.capture(), any(RowMapper.class), any(Object[].class));
        String select = selectSql.getValue();
        // 所有字符串片段拼接边界：标识符/关键字与 SQL 关键字之间必须有空格
        assertThat(SQL_GLUE.matcher(select).find()).isFalse();
        assertThat(SQL_GLUE.matcher(count).find()).isFalse();
    }

    @Test
    void search_withKeywordAndType_placeholderCountMatchesParamCount() {
        // 回归：type 筛选的 EXISTS 子查询不含占位符，此前却向参数列表多加了占位参数，
        // 超出 PreparedStatement 参数上限，sqlite-jdbc 抛 ArrayIndexOutOfBoundsException，
        // 模糊搜索（如 q=测试用&type=ValueDomain）全部 500。
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchDefault(
                "测试用", "ValueDomain", null, null, null, false, null, null);

        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(countSql.capture(), eq(Long.class), countArgs.capture());
        assertPlaceholderParamMatch(countSql.getValue(), countArgs.getValue());
        assertKnownTables(countSql.getValue());

        ArgumentCaptor<String> selectSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> selectArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(selectSql.capture(), any(RowMapper.class), selectArgs.capture());
        assertPlaceholderParamMatch(selectSql.getValue(), selectArgs.getValue());
        assertKnownTables(selectSql.getValue());
    }

    /** 已知物理表名白名单：回归错拼的表名（如 registration_specification）导致的 no such table。 */
    private static final java.util.Set<String> KNOWN_TABLES = java.util.Set.of(
            "administered_item", "registered_item", "item", "scoped_identifier", "namespace",
            "registration_authority", "registration_state", "designation", "definition",
            "designation_context", "definition_context", "organization_name", "constraint_set",
            "reference_document", "data_element", "data_element_concept",
            "conceptual_domain", "value_domain", "object_class", "property", "data_type",
            "data_type_scheme", "unit_of_measure", "measure_class", "dimensionality",
            "classification_scheme", "context", "organization", "individual", "contact", "document_type");

    private static final Pattern TABLE_REF = Pattern.compile("mdr\\.(\\w+)");

    private static void assertKnownTables(String sql) {
        java.util.regex.Matcher m = TABLE_REF.matcher(sql);
        while (m.find()) {
            assertThat(KNOWN_TABLES).as("SQL 引用了未知表: %s", m.group(1)).contains(m.group(1));
        }
    }

    /** SQL 中 "?" 占位符数量必须与绑定参数数量一致（SQLite 方言含 LIMIT/OFFSET 占位符）。 */
    private static void assertPlaceholderParamMatch(String sql, Object[] params) {
        long placeholders = sql.chars().filter(c -> c == '?').count();
        assertThat(placeholders)
                .as("SQL 占位符数与参数数不一致：%d 个占位符 vs %d 个参数%n%s", placeholders, params.length, sql)
                .isEqualTo(params.length);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_withAcceptabilityList_placeholderCountMatchesParamCountAndLowercaseBinding() {
        // mdr-standard-compliance-cleanup：acceptability 过滤改走 designation_context/definition_context，
        // IN 占位符 ×2 分支、参数落库为小写（AcceptabilityConverter）。占位符配平与大小写回归保护。
        stubCountAndRows(1L, List.of(sampleResponse));

        searchService.search(null, "ValueDomain", null, null,
                List.of(Acceptability.PREFERRED, Acceptability.ADMITTED),
                null, true, "VD.00001", null, PageRequest.of(0, 20));

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countParams = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(countSql.capture(), eq(Long.class), countParams.capture());
        assertPlaceholderParamMatch(countSql.getValue(), countParams.getValue());
        assertThat(countSql.getValue())
                .contains("LOWER(dc_acc.acceptability) IN")
                .contains("LOWER(dfc_acc.acceptability) IN");
        assertThat(java.util.Arrays.asList(countParams.getValue()))
                .contains("preferred", "admitted");

        ArgumentCaptor<String> selectSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> selectParams = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(selectSql.capture(), any(RowMapper.class), selectParams.capture());
        assertPlaceholderParamMatch(selectSql.getValue(), selectParams.getValue());
        assertThat(selectSql.getValue()).contains("LOWER(dc_pref.acceptability) = 'preferred'");
    }

    @Test
    void search_withSingleType_skipsFullCaseProbe() {
        // 优化：带单值类型筛选时，结果集类型已被 WHERE 的 EXISTS 锁定，
        // item_type 直接返回常量，不再对每行执行 21 个全表探测 EXISTS。
        stubCountAndRows(1L, List.of(sampleResponse));

        searchDefault("测试", "ValueDomain", null, null, null, false, null, null);

        ArgumentCaptor<String> selectSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(selectSql.capture(), any(RowMapper.class), any(Object[].class));
        String select = selectSql.getValue();
        assertThat(select).contains("'ValueDomain' AS item_type");
        // has_preferred 的 CASE WHEN EXISTS(d_pref 别名)是 SQL Server 方言兼容写法,不算 item_type 探测;
        // 这里只断言 item_type 未退回 21 表全量探测(探测表达式的特征是对 item 子类表做 EXISTS)
        assertThat(select).doesNotContain("CASE WHEN EXISTS (SELECT 1 FROM mdr.namespace");
    }

    @Test
    void search_withMultipleTypes_probesOnlyRequestedSubset() {
        // 多值类型筛选：CASE 只探测传入的类型子集，而非全部 21 张表
        stubCountAndRows(1L, List.of(sampleResponse));

        searchDefault("测试", "DataElement,ValueDomain", null, null, null, false, null, null);

        ArgumentCaptor<String> selectSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(selectSql.capture(), any(RowMapper.class), any(Object[].class));
        String select = selectSql.getValue();
        assertThat(select).contains("CASE WHEN EXISTS");
        assertThat(select).contains("THEN 'DataElement'");
        assertThat(select).contains("THEN 'ValueDomain'");
        assertThat(select).doesNotContain("THEN 'Namespace'");
        assertThat(select).doesNotContain("THEN 'ConceptualDomain'");
    }

    @Test
    void search_withoutType_keepsFullCaseProbe() {
        // 无类型筛选：结果可来自任意实体类型，保留全量 CASE 探测
        stubCountAndRows(1L, List.of(sampleResponse));

        searchDefault("测试", null, null, null, null, false, null, null);

        ArgumentCaptor<String> selectSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(selectSql.capture(), any(RowMapper.class), any(Object[].class));
        String select = selectSql.getValue();
        assertThat(select).contains("CASE WHEN EXISTS");
        assertThat(select).contains("THEN 'Namespace'");
        assertThat(select).contains("THEN 'DocumentType'");
    }

    @Test
    void search_withKeyword_shouldReturnResults() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchDefault("患者", null, null, null, null, false, null, null);

        assertThat(result.getContent()).hasSize(1);
        AdministeredItemResponse res = result.getContent().get(0);
        assertThat(res.itemType()).isEqualTo("DataElement");
        assertThat(res.matchType()).isEqualTo("EXACT");
        assertThat(res.registrationStatus()).isEqualTo(RegistrationStatus.STANDARD);

        // keyword must be pushed into the SQL parameters as a lowercase LIKE pattern
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), params.capture());
        assertThat(params.getValue()).contains("%患者%");
    }

    @Test
    void search_withTypeFilter_shouldInjectItemTypeExistsClause() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchDefault(
                null, "DataElement", null, null, null, false, null, null);

        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        // 包含 EXISTS 子查询到 data_element 表
        assertThat(sql.getValue()).contains("EXISTS").contains("data_element");
    }

    @Test
    void search_withTypeMultiValue_shouldInjectOrClause() {
        stubCountAndRows(2L, List.of(sampleResponse, sampleResponse));

        Page<AdministeredItemResponse> result = searchDefault(
                null, "DataElement,ConceptualDomain", null, null, null, false, null, null);

        assertThat(result.getContent()).hasSize(2);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertThat(sql.getValue()).contains(" OR ");
        assertThat(sql.getValue()).contains("data_element").contains("conceptual_domain");
    }

    @Test
    void search_withUnsupportedType_shouldIgnoreTypeAndStillQuery() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchDefault(
                null, "NotARealType", null, null, null, false, null, null);

        assertThat(result.getContent()).hasSize(1);
        // 1=0 谓词注入 SQL；调用方预期结果为 0 但 stub 返回 1 行 —— 此处只验证不抛异常
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test
    void search_exactMatch_withIdentifierAndShorthand_shouldInjectEquals() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchDefault(
                null, null, null, null, null, true, "MD.VD.00001", "MD");

        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), params.capture());
        assertThat(params.getValue()).contains("MD.VD.00001", "MD");
    }

    @Test
    void search_exactMatch_noMatch_shouldReturnEmpty() {
        stubCountAndRows(0L, List.of());

        Page<AdministeredItemResponse> result = searchDefault(
                null, null, null, null, null, true, "MD.VD.99999", "MD");

        assertThat(result.getContent()).isEmpty();
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void search_withNamespaceId_shouldInjectNamespaceFilter() {
        UUID nsId = UUID.randomUUID();
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchDefault(
                null, null, null, null, nsId, false, null, null);

        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), params.capture());
        assertThat(params.getValue()).contains(nsId);
    }

    @Test
    void search_withStatusFilter_shouldPassStatusParameter() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchDefault(
                null, null, RegistrationStatus.STANDARD, null, null, false, null, null);

        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), params.capture());
        assertThat(params.getValue()).contains("STANDARD");
    }

    @Test
    void search_withCombinedFilters_shouldReturnFilteredResults() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchDefault(
                "patient", "DataElement", RegistrationStatus.STANDARD, null, null, false, null, null);

        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), params.capture());
        assertThat(params.getValue()).contains("%patient%", "STANDARD");
    }

    @Test
    void search_withKeywordNoMatch_shouldReturnEmpty() {
        stubCountAndRows(0L, List.of());

        Page<AdministeredItemResponse> result = searchDefault(
                "ZZZ_NONEXISTENT", null, null, null, null, false, null, null);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void search_withNullKeyword_shouldReturnAll() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchDefault(
                null, null, null, null, null, false, null, null);

        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), params.capture());
        assertThat(params.getValue()).isEmpty();
    }

    @Test
    void search_withBlankKeyword_shouldReturnAll() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchDefault(
                "   ", null, null, null, null, false, null, null);

        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), params.capture());
        assertThat(params.getValue()).isEmpty();
    }

    @Test
    void search_withUuidKeyword_addsItemIdEqualityForPickerBackfill() {
        // smart-item-picker 回填（fetchById）以 item UUID 为关键字搜索：
        // designation/definition/scoped_identifier 均不含 item 主键值，
        // 必须追加 i.id 等值匹配，否则已选关联项反查不到、选中值无法回显
        when(env.toJdbcUuid(any(UUID.class))).thenAnswer(inv -> inv.getArgument(0).toString());
        stubCountAndRows(1L, List.of(sampleResponse));
        UUID itemId = UUID.randomUUID();

        Page<AdministeredItemResponse> result = searchDefault(
                itemId.toString(), null, null, null, null, false, null, null);

        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(countSql.capture(), eq(Long.class), countArgs.capture());
        assertThat(countSql.getValue()).contains("i.id = ?");
        assertPlaceholderParamMatch(countSql.getValue(), countArgs.getValue());
        assertThat(countArgs.getValue()).contains(itemId.toString());

        ArgumentCaptor<String> selectSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> selectArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(selectSql.capture(), any(RowMapper.class), selectArgs.capture());
        assertThat(selectSql.getValue()).contains("i.id = ?");
        assertPlaceholderParamMatch(selectSql.getValue(), selectArgs.getValue());
    }

    @Test
    void search_withNonUuidKeyword_doesNotAddItemIdEquality() {
        stubCountAndRows(1L, List.of(sampleResponse));

        searchDefault("患者", null, null, null, null, false, null, null);

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(countSql.capture(), eq(Long.class), any(Object[].class));
        assertThat(countSql.getValue()).doesNotContain("i.id = ?");
    }

    @Test
    void search_withPagination_shouldRespectPageSize() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = search(
                "患者", null, null, null, null, false, null, null,
                PageRequest.of(0, 10));

        assertThat(result.getContent()).isNotEmpty();

        // pagination bounds are appended to the select query parameters
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), params.capture());
        Object[] selectParams = params.getValue();
        // SQL Server: offset=0, pageSize=10
        assertThat(selectParams[selectParams.length - 2]).isEqualTo(0L);
        assertThat(selectParams[selectParams.length - 1]).isEqualTo(10);
    }

    @Test
    void search_returnsExactMatchType_whenExactMatchTrue() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchDefault(
                null, null, null, null, null, true, "MD.VD.00001", "MD");

        assertThat(result.getContent().get(0).matchType()).isEqualTo("EXACT");
    }

    @Test
    void search_returnsPartialMatchType_whenExactMatchFalse() {
        AdministeredItemResponse partial = new AdministeredItemResponse(
                UUID.randomUUID(), UUID.randomUUID(), "ValueDomain", "label",
                "id", "1", UUID.randomUUID(), "ns", UUID.randomUUID(), "ra",
                RegistrationStatus.STANDARD, LocalDateTime.now(), null,
                null, null, null, null, LocalDateTime.now(), LocalDateTime.now(),
                null, "PARTIAL", null);
        stubCountAndRows(1L, List.of(partial));

        Page<AdministeredItemResponse> result = searchDefault(
                "身高", null, null, null, null, false, null, null);

        assertThat(result.getContent().get(0).matchType()).isEqualTo("PARTIAL");
    }
}
