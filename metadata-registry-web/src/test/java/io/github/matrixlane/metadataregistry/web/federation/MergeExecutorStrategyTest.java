package io.github.matrixlane.metadataregistry.web.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfigService;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierVersionHistory;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierVersionHistoryRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.service.ItemAdministrationService;
import io.github.matrixlane.metadataregistry.web.service.VersionHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 合并执行器（registry-merge-strategy D2/D4/D5）：Registrar 门控、基于合并基
 * 的幂等跳过、干净更新的双标识版本推进与 MERGE 标记。
 */
@ExtendWith(MockitoExtension.class)
class MergeExecutorStrategyTest {

    @Mock private MergeRequestRepository mergeRequestRepository;
    @Mock private MergeRequestItemRepository mergeRequestItemRepository;
    @Mock private ItemLinkRepository itemLinkRepository;
    @Mock private SyncWatermarkRepository watermarkRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private RegistrationAuthorityRepository registrationAuthorityRepository;
    @Mock private ScopedIdentifierRepository scopedIdentifierRepository;
    @Mock private LanguageIdentificationRepository languageRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.service.LanguageIdentifierResolver languageIdentifierResolver;
    @Mock private ItemAdministrationService itemAdministrationService;
    @Mock private VersionHistoryService versionHistoryService;
    @Mock private GenericItemCreator itemCreator;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private MergeVersionSupport mergeVersionSupport;
    @Mock private MergeRegistrarGate mergeRegistrarGate;
    @Mock private GlobalNamespaceService globalNamespaceService;
    @Mock private IdentifierVersionHistoryRepository identifierVersionHistoryRepository;
    @Mock private IdentifierStrategyConfigService identifierStrategyConfigService;
    @Mock private io.github.matrixlane.metadataregistry.web.service.ScopedIdentifierLifecycleService lifecycleService;
    @Mock private io.github.matrixlane.metadataregistry.web.service.NamespaceIdentifierSettingsService settingsService;

    @org.junit.jupiter.api.BeforeEach
    void stubSettingsDefaults() {
        org.mockito.Mockito.lenient().when(settingsService.getOrDefault(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> new io.github.matrixlane.metadataregistry.ext.identification.NamespaceIdentifierSettings());
    }

    @InjectMocks
    private MergeExecutor executor;

    @TempDir
    Path receiptDir;

    private static final String RAI = "RAI-1";
    private static final String PAYLOAD = """
            {"remoteItemKey":"RAI-1:X-1","remoteItemUuid":null,"rai":"RAI-1",
             "identifier":"X-1","version":"11","versionNumber":11,"export":null,
             "designations":[],"definitions":[],"sourceNamespaceGlobalId":"uuid-1",
             "sourceNamespaceName":"来源命名空间","sourceNamespaceNamingAuthorityName":null}""";

    private UUID mrId;
    private UUID nodeId;
    private UUID targetNamespaceId;
    private RegistryNode node;
    private MergeRequest mr;

    @BeforeEach
    void setUp() {
        executor.setReceiptDir(receiptDir);
        mrId = UUID.randomUUID();
        nodeId = UUID.randomUUID();
        targetNamespaceId = UUID.randomUUID();
        node = new RegistryNode();
        node.setId(nodeId);
        node.setRai(RAI);
        mr = new MergeRequest();
        mr.setId(mrId);
        mr.setStatus(MergeRequestStatus.UNDER_REVIEW);
        mr.setSourceNode(node);
        mr.setTargetNamespaceId(targetNamespaceId);
        when(mergeRequestRepository.findById(mrId)).thenReturn(Optional.of(mr));
        lenient().when(watermarkRepository.findByNodeId(nodeId)).thenReturn(Optional.empty());
    }

    @Test
    void execute_invokesRegistrarGateBeforeMerging() {
        when(mergeRequestItemRepository.findByMergeRequestId(mrId)).thenReturn(List.of());

        Map<String, Object> result = executor.execute(mrId, "tester");

        verify(mergeRegistrarGate).assertTargetRegistrar(mr);
        assertThat(result.get("merged")).isEqualTo(0);
        assertThat(mr.getStatus()).isEqualTo(MergeRequestStatus.MERGED);
    }

    @Test
    void execute_gateDenial_abortsMerge() {
        doThrow(new AccessDeniedException("仅目的方注册机构的活动 Registrar 可批准"))
                .when(mergeRegistrarGate).assertTargetRegistrar(mr);

        assertThatThrownBy(() -> executor.execute(mrId, "tester"))
                .isInstanceOf(AccessDeniedException.class);

        verify(mergeRequestItemRepository, never()).findByMergeRequestId(any());
        assertThat(mr.getStatus()).isEqualTo(MergeRequestStatus.UNDER_REVIEW);
    }

    @Test
    void execute_pendingConflict_throws() {
        MergeRequestItem item = item(MergeItemChangeType.CONFLICT, MergeItemResolution.PENDING);
        when(mergeRequestItemRepository.findByMergeRequestId(mrId)).thenReturn(List.of(item));

        assertThatThrownBy(() -> executor.execute(mrId, "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在未裁决的条目");
    }

    @Test
    void execute_updateWithSourceVersionEqualToMergeBase_skipsIdempotently() {
        // 来源版本与合并基一致 → 不产生任何变更，幂等跳过
        MergeRequestItem item = item(MergeItemChangeType.UPDATE, MergeItemResolution.PENDING);
        UUID localItemId = UUID.randomUUID();
        ItemLink link = linkTo(localItemId);
        DataElement localItem = localItemWithAdministeredItem(localItemId);
        when(mergeRequestItemRepository.findByMergeRequestId(mrId)).thenReturn(List.of(item));
        when(itemLinkRepository.findByNodeAndRemoteKey(nodeId, RAI + ":X-1")).thenReturn(Optional.of(link));
        when(itemRepository.findById(localItemId)).thenReturn(Optional.of(localItem));
        when(mergeVersionSupport.mergeBaseVersion(localItemId, "uuid-1", "X-1", targetNamespaceId))
                .thenReturn("11");

        Map<String, Object> result = executor.execute(mrId, "tester");

        assertThat(result.get("skipped")).isEqualTo(1);
        assertThat(result.get("merged")).isEqualTo(0);
        assertThat(item.getResolution()).isEqualTo(MergeItemResolution.ACCEPT);
        verify(itemAdministrationService, never()).applyDesignations(any(), any());
        verify(itemRepository, never()).save(any());
        verify(versionHistoryService, never()).recordSnapshot(any(), any());
        assertThat(mr.getStatus()).isEqualTo(MergeRequestStatus.MERGED);
    }

    @Test
    void execute_cleanUpdate_advancesBothIdentifierVersions() throws Exception {
        // 干净更新（来源版本 11 > 合并基 10）：来源命名空间标识推进合并基至 11，
        // 目的方命名空间标识按策略递增 1→2，并写入 MERGE 标记历史
        MergeRequestItem item = item(MergeItemChangeType.UPDATE, MergeItemResolution.PENDING);
        UUID localItemId = UUID.randomUUID();
        ItemLink link = linkTo(localItemId);
        DataElement localItem = localItemWithAdministeredItem(localItemId);
        AdministeredItem ai = (AdministeredItem) localItem.getIdentifiedItem();

        Namespace sourceCopy = new Namespace();
        sourceCopy.setId(UUID.randomUUID());
        ScopedIdentifier sourceSi = new ScopedIdentifier("X-1", sourceCopy, ai);
        sourceSi.setVersion("10");
        Namespace targetNs = new Namespace();
        targetNs.setId(targetNamespaceId);
        ScopedIdentifier targetSi = new ScopedIdentifier("T-1", targetNs, ai);
        targetSi.setVersion("1");
        ai.getScopedIdentifiers().add(sourceSi);
        ai.getScopedIdentifiers().add(targetSi);

        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());

        when(mergeRequestItemRepository.findByMergeRequestId(mrId)).thenReturn(List.of(item));
        when(itemLinkRepository.findByNodeAndRemoteKey(nodeId, RAI + ":X-1")).thenReturn(Optional.of(link));
        when(itemRepository.findById(localItemId)).thenReturn(Optional.of(localItem));
        when(mergeVersionSupport.mergeBaseVersion(localItemId, "uuid-1", "X-1", targetNamespaceId))
                .thenReturn("10");
        when(registrationAuthorityRepository.findByRai(RAI)).thenReturn(Optional.of(ra));
        when(globalNamespaceService.resolveNamingAuthority(isNull(), eq(ra))).thenReturn(ra);
        when(globalNamespaceService.ensureNamespaceCopy("uuid-1", "来源命名空间", ra)).thenReturn(sourceCopy);
        when(namespaceRepository.findById(targetNamespaceId)).thenReturn(Optional.of(targetNs));

        Map<String, Object> result = executor.execute(mrId, "tester");

        assertThat(result.get("merged")).isEqualTo(1);
        assertThat(result.get("skipped")).isEqualTo(0);
        // 来源命名空间标识推进为来源版本（合并基前移）
        assertThat(sourceSi.getVersion()).isEqualTo("11");
        // 目的方命名空间标识按版本策略递增
        assertThat(targetSi.getVersion()).isEqualTo("2");

        ArgumentCaptor<IdentifierVersionHistory> historyCaptor =
                ArgumentCaptor.forClass(IdentifierVersionHistory.class);
        verify(identifierVersionHistoryRepository).save(historyCaptor.capture());
        IdentifierVersionHistory history = historyCaptor.getValue();
        assertThat(history.getScopedIdentifier()).isSameAs(targetSi);
        assertThat(history.getVersion()).isEqualTo("2");
        assertThat(history.getChangeSummary()).startsWith(MergeVersionSupport.MERGE_MARKER);

        verify(versionHistoryService).recordSnapshot(localItem, "MERGE");
        assertThat(mr.getStatus()).isEqualTo(MergeRequestStatus.MERGED);
        // 合并回执写入回执目录
        assertThat(Files.exists(receiptDir.resolve("receipt-" + mrId + ".json"))).isTrue();
    }

    // ===== 辅助 =====

    private MergeRequestItem item(MergeItemChangeType changeType, MergeItemResolution resolution) {
        MergeRequestItem item = new MergeRequestItem();
        item.setId(UUID.randomUUID());
        item.setMergeRequest(mr);
        item.setRemoteItemKey(RAI + ":X-1");
        item.setChangeType(changeType);
        item.setResolution(resolution);
        item.setPayloadJson(PAYLOAD);
        return item;
    }

    private ItemLink linkTo(UUID localItemId) {
        ItemLink link = new ItemLink();
        link.setLocalItemId(localItemId);
        link.setRemoteNode(node);
        link.setRemoteItemKey(RAI + ":X-1");
        return link;
    }

    private DataElement localItemWithAdministeredItem(UUID localItemId) {
        DataElement localItem = new DataElement();
        localItem.setId(localItemId);
        AdministeredItem ai = new AdministeredItem(localItem);
        ai.setId(UUID.randomUUID());
        localItem.setIdentifiedItem(ai);
        return localItem;
    }
}
