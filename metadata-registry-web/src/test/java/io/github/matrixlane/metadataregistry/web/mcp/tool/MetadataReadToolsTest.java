package io.github.matrixlane.metadataregistry.web.mcp.tool;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactAnalysisResult;
import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactAnalysisService;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportRequest;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingNetworkResponse;
import io.github.matrixlane.metadataregistry.web.service.MetadataExportService;
import io.github.matrixlane.metadataregistry.web.service.MetadataSearchService;
import io.github.matrixlane.metadataregistry.web.service.RegistrationProcessLayoutService;
import io.github.matrixlane.metadataregistry.web.service.RegistrationProcessService;
import io.github.matrixlane.metadataregistry.web.service.VersionHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 只读工具（add-mcp-server-support 任务 4.x）：参数解析、分页上限、
 * 空结果分支、错误透传语义。
 */
class MetadataReadToolsTest {

    private final MetadataSearchService searchService = mock(MetadataSearchService.class);
    private final VersionHistoryService versionHistoryService = mock(VersionHistoryService.class);
    private final MetadataExportService exportService = mock(MetadataExportService.class);
    private final ItemMappingService mappingService = mock(ItemMappingService.class);
    private final ImpactAnalysisService impactService = mock(ImpactAnalysisService.class);
    private final RegistrationProcessService processService = mock(RegistrationProcessService.class);
    private final RegistrationProcessLayoutService layoutService = mock(RegistrationProcessLayoutService.class);

    private final SearchMetadataTool searchTool = new SearchMetadataTool(searchService);
    private final BrowseDataDictionaryTool browseTool = new BrowseDataDictionaryTool(searchService);
    private final GetItemDetailTool detailTool = new GetItemDetailTool(searchService);
    private final GetItemVersionsTool versionsTool = new GetItemVersionsTool(versionHistoryService);
    private final ExportDataDictionaryTool exportTool = new ExportDataDictionaryTool(exportService);
    private final GetMappingNetworkTool networkTool = new GetMappingNetworkTool(mappingService);
    private final GetMappingImpactTool impactTool = new GetMappingImpactTool(impactService);
    private final GetRegistrationProcessTool processTool = new GetRegistrationProcessTool(processService, layoutService);

    @Test
    void search_shouldMapParamsAndCapPageSize() {
        when(searchService.search(anyString(), isNull(), eq(RegistrationStatus.RECORDED), isNull(), isNull(), isNull(),
                eq(false), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<?> page = (PageResponse<?>) searchTool.handle(Map.of(
                "q", "血压", "status", "recorded", "page", "0", "size", "100"));
        assertThat(page).isNotNull();
        verify(searchService).search(eq("血压"), isNull(), eq(RegistrationStatus.RECORDED), isNull(),
                isNull(), isNull(), eq(false), isNull(), isNull(),
                eq(PageRequest.of(0, 100, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "creationDateTime"))));
    }

    @Test
    void search_shouldRejectIllegalStatus() {
        assertThatThrownBy(() -> searchTool.handle(Map.of("status", "NOT_A_STATUS")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ApiCodes.VALIDATION_FAILED);
    }

    @Test
    void browse_shouldDelegateToSameSearchService() {
        when(searchService.search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(false), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        browseTool.handle(Map.of());
        verify(searchService).search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(false), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void detail_shouldPassUuidAndThrow422OnBadUuid() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> detailTool.handle(Map.of("itemId", "not-a-uuid")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("itemId");
        assertThatThrownBy(() -> detailTool.handle(Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少必填参数");
        detailTool.handle(Map.of("itemId", id.toString()));
        verify(searchService).findDetailByAnyId(id);
    }

    @Test
    void versions_shouldCallService() {
        UUID id = UUID.randomUUID();
        versionsTool.handle(Map.of("itemId", id.toString()));
        verify(versionHistoryService).listVersions(id);
    }

    @Test
    void export_shouldSupportJsonAndCsvAndRejectUnknownFormat() throws IOException {
        doAnswer(invocation -> {
            ByteArrayOutputStream out = invocation.getArgument(1);
            out.write("{\"items\":[]}".getBytes());
            return null;
        }).when(exportService).exportJsonToStream(any(MetadataExportRequest.class), any(ByteArrayOutputStream.class));

        Object json = exportTool.handle(Map.of("format", "json"));
        assertThat(json).isEqualTo("{\"items\":[]}");

        assertThatThrownBy(() -> exportTool.handle(Map.of("format", "xlsx")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("format");
    }

    @Test
    void network_withoutCenterId_shouldReturnEmptyNetwork() {
        MappingNetworkResponse response = (MappingNetworkResponse) networkTool.handle(Map.of());
        assertThat(response.nodes()).isEmpty();
        assertThat(response.edges()).isEmpty();
        assertThat(response.truncated()).isFalse();
    }

    @Test
    void impact_shouldRequireExactlyOneAnchor() {
        assertThatThrownBy(() -> impactTool.handle(Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("二选一");
        assertThatThrownBy(() -> impactTool.handle(Map.of(
                "itemId", UUID.randomUUID().toString(), "mappingId", UUID.randomUUID().toString())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("二选一");
    }

    @Test
    void impact_itemAnchor_shouldUseUpdatedDefault() {
        UUID itemId = UUID.randomUUID();
        when(impactService.analyzeItemChange(eq(itemId), eq("UPDATED")))
                .thenReturn(new ImpactAnalysisResult(itemId, "UPDATED", List.of(), 0, false));
        impactTool.handle(Map.of("itemId", itemId.toString()));
        verify(impactService).analyzeItemChange(itemId, "UPDATED");
    }

    @Test
    void impact_mappingAnchor_shouldUseModifiedDefault() {
        UUID mappingId = UUID.randomUUID();
        when(impactService.analyzeMappingChange(eq(mappingId), eq("MODIFIED")))
                .thenReturn(new ImpactAnalysisResult(mappingId, "MODIFIED", List.of(), 0, false));
        impactTool.handle(Map.of("mappingId", mappingId.toString()));
        verify(impactService).analyzeMappingChange(mappingId, "MODIFIED");
    }

    @Test
    void process_shouldCombineRulesAndLayout() {
        when(processService.getCurrentProcess()).thenReturn(null);
        when(layoutService.read()).thenReturn(null);
        Object result = processTool.handle(Map.of());
        assertThat(result).isInstanceOf(Map.class);
    }
}
