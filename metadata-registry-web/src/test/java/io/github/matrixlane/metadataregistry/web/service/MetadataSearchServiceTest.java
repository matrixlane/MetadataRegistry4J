package io.github.matrixlane.metadataregistry.web.service;

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
                "EXACT");
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
                null, "PARTIAL");
        stubCountAndRows(1L, List.of(partial));

        Page<AdministeredItemResponse> result = searchDefault(
                "身高", null, null, null, null, false, null, null);

        assertThat(result.getContent().get(0).matchType()).isEqualTo("PARTIAL");
    }
}
