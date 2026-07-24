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
                null,
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
                LocalDateTime.now(),
                LocalDateTime.now(),
                null);
    }

    @SuppressWarnings("unchecked")
    private void stubCountAndRows(long total, List<AdministeredItemResponse> rows) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(total);
        // lenient: the select query is skipped entirely when the count is zero
        lenient().when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(rows);
    }

    @Test
    void search_withKeyword_shouldReturnResults() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchService.search(
                "患者", null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        AdministeredItemResponse res = result.getContent().get(0);
        assertThat(res.itemId()).isNotNull();
        assertThat(res.registrationStatus()).isEqualTo(RegistrationStatus.STANDARD);

        // keyword must be pushed into the SQL parameters as a lowercase LIKE pattern
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), params.capture());
        assertThat(params.getValue()).contains("%患者%");
    }

    @Test
    void search_withTypeFilter_shouldStillQuery() {
        // JOINED inheritance has no dtype column; the type parameter is accepted
        // but intentionally ignored by the native SQL implementation.
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchService.search(
                null, "DataElement", null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test
    void search_withStatusFilter_shouldPassStatusParameter() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchService.search(
                null, null, RegistrationStatus.STANDARD, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).registrationStatus())
                .isEqualTo(RegistrationStatus.STANDARD);

        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), params.capture());
        assertThat(params.getValue()).contains("STANDARD");
    }

    @Test
    void search_withCombinedFilters_shouldReturnFilteredResults() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchService.search(
                "patient", "DataElement", RegistrationStatus.STANDARD, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), params.capture());
        assertThat(params.getValue()).contains("%patient%", "STANDARD");
    }

    @Test
    void search_withKeywordNoMatch_shouldReturnEmpty() {
        stubCountAndRows(0L, List.of());

        Page<AdministeredItemResponse> result = searchService.search(
                "ZZZ_NONEXISTENT", null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        // zero hits: the select query must be skipped
        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void search_withNullKeyword_shouldReturnAll() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchService.search(
                null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), params.capture());
        assertThat(params.getValue()).isEmpty();
    }

    @Test
    void search_withBlankKeyword_shouldReturnAll() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchService.search(
                "   ", null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);

        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), params.capture());
        assertThat(params.getValue()).isEmpty();
    }

    @Test
    void search_withPagination_shouldRespectPageSize() {
        stubCountAndRows(1L, List.of(sampleResponse));

        Page<AdministeredItemResponse> result = searchService.search(
                "患者", null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).isNotEmpty();

        // pagination bounds are appended to the select query parameters
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), params.capture());
        Object[] selectParams = params.getValue();
        assertThat(selectParams[selectParams.length - 2]).isEqualTo(0L);
        assertThat(selectParams[selectParams.length - 1]).isEqualTo(10);
    }
}
