package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.AdministeredItemResponse;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataSearchServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DatabaseEnvironment env;

    @InjectMocks
    private MetadataSearchService searchService;

    private UUID aiId;
    private AdministeredItemResponse expectedResponse;

    @BeforeEach
    void setUp() {
        lenient().when(env.vendor()).thenReturn(DatabaseVendor.SQLSERVER);
        lenient().when(env.qualify(anyString())).thenAnswer(inv -> "mdr." + inv.getArgument(0));
        aiId = UUID.randomUUID();
        expectedResponse = new AdministeredItemResponse(
                aiId,
                UUID.randomUUID(),
                "DataElement",
                "患者姓名",
                "DE-PATIENT-NAME",
                "1.0",
                UUID.randomUUID(),
                "sys",
                UUID.randomUUID(),
                "ra",
                RegistrationStatus.STANDARD,
                LocalDateTime.now(),
                "note",
                LocalDateTime.now(),
                LocalDateTime.now(),
                "description"
        );
    }

    @Test
    void search_withKeyword_shouldReturnResults() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(expectedResponse));

        Page<AdministeredItemResponse> result = searchService.search(
                "患者", null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        AdministeredItemResponse res = result.getContent().get(0);
        assertThat(res.itemId()).isNotNull();
        assertThat(res.registrationStatus()).isEqualTo(RegistrationStatus.STANDARD);
    }

    @Test
    void search_withTypeFilter_shouldCallRepositoryWithSpec() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(expectedResponse));

        Page<AdministeredItemResponse> result = searchService.search(
                null, "DataElement", null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void search_withStatusFilter_shouldCallRepositoryWithSpec() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(expectedResponse));

        Page<AdministeredItemResponse> result = searchService.search(
                null, null, RegistrationStatus.STANDARD, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        AdministeredItemResponse res = result.getContent().get(0);
        assertThat(res.registrationStatus()).isEqualTo(RegistrationStatus.STANDARD);
    }

    @Test
    void search_withCombinedFilters_shouldReturnFilteredResults() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(expectedResponse));

        Page<AdministeredItemResponse> result = searchService.search(
                "patient", "DataElement", RegistrationStatus.STANDARD, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void search_withKeywordNoMatch_shouldReturnEmpty() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);

        Page<AdministeredItemResponse> result = searchService.search(
                "ZZZ_NONEXISTENT", null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void search_withNullKeyword_shouldReturnAll() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(expectedResponse));

        Page<AdministeredItemResponse> result = searchService.search(
                null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void search_withBlankKeyword_shouldReturnAll() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(expectedResponse));

        Page<AdministeredItemResponse> result = searchService.search(
                "   ", null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void search_withPagination_shouldRespectPageSize() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(expectedResponse));

        Page<AdministeredItemResponse> result = searchService.search(
                "患者", null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).isNotEmpty();
    }
}
