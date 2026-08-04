package io.github.matrixlane.metadataregistry.web.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 合并请求生命周期（registry-merge-strategy）：RAI 前置、条目版本分类、
 * 冲突裁决的 Registrar 门控与评审视图版本三方信息。
 */
@ExtendWith(MockitoExtension.class)
class MergeRequestServiceStrategyTest {

    @Mock private MergeRequestRepository mergeRequestRepository;
    @Mock private MergeRequestItemRepository mergeRequestItemRepository;
    @Mock private RegistryNodeRepository registryNodeRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private ItemLinkRepository itemLinkRepository;
    @Mock private ScopedIdentifierRepository scopedIdentifierRepository;
    @Mock private SyncWatermarkRepository watermarkRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private RegistrationAuthorityRepository registrationAuthorityRepository;
    @Mock private MergeVersionSupport mergeVersionSupport;
    @Mock private MergeRegistrarGate mergeRegistrarGate;

    @InjectMocks
    private MergeRequestService service;

    @TempDir
    Path tempDir;

    private static final String RAI = "RAI-1";
    private UUID nodeId;
    private UUID targetNamespaceId;
    private RegistryNode node;

    @BeforeEach
    void setUp() {
        nodeId = UUID.randomUUID();
        targetNamespaceId = UUID.randomUUID();
        node = new RegistryNode();
        node.setId(nodeId);
        node.setRai(RAI);
    }

    // ===== importBundle：RAI 一致性前置（D1，接收侧）=====

    @Test
    void importBundle_rejectsWhenSourceRaiUnmatched() throws IOException {
        Path bundle = writeBundle(RAI, List.of());
        when(registrationAuthorityRepository.findByRai(RAI)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importBundle(bundle, "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RAI 不一致");

        verify(mergeRequestRepository, never()).save(any());
    }

    @Test
    void importBundle_rejectsWhenSourceNodeNotRegistered() throws IOException {
        Path bundle = writeBundle(RAI, List.of());
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        when(registrationAuthorityRepository.findByRai(RAI)).thenReturn(Optional.of(ra));
        when(registryNodeRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.importBundle(bundle, "tester"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源节点未登记");
    }

    // ===== importBundle：条目分类（D4/D5 版本逻辑）=====

    @Test
    void importBundle_linkedItem_sourceUnchanged_classifiedUpdate() throws IOException {
        // 来源版本与合并基一致 → UPDATE（执行期幂等跳过），无需本地编辑判定
        UUID localItemId = UUID.randomUUID();
        stubSuccessfulImportPrereqs(localItemId, "11");

        service.importBundle(writeBundle(RAI, List.of(bundleItem("11"))), "tester");

        assertThat(capturedItem().getChangeType()).isEqualTo(MergeItemChangeType.UPDATE);
        verify(mergeVersionSupport, never()).locallyEdited(any(), any());
    }

    @Test
    void importBundle_linkedItem_sourceBumpedOnly_classifiedUpdate() throws IOException {
        // 来源单侧更新（来源版本 > 合并基，目的方未本地编辑）→ UPDATE
        UUID localItemId = UUID.randomUUID();
        stubSuccessfulImportPrereqs(localItemId, "10");
        when(mergeVersionSupport.locallyEdited(localItemId, targetNamespaceId)).thenReturn(false);

        service.importBundle(writeBundle(RAI, List.of(bundleItem("11"))), "tester");

        assertThat(capturedItem().getChangeType()).isEqualTo(MergeItemChangeType.UPDATE);
    }

    @Test
    void importBundle_linkedItem_bothSidesUpdated_classifiedConflict() throws IOException {
        // 双侧更新（来源版本 > 合并基，且目的方本地也更新过）→ CONFLICT
        UUID localItemId = UUID.randomUUID();
        stubSuccessfulImportPrereqs(localItemId, "10");
        when(mergeVersionSupport.locallyEdited(localItemId, targetNamespaceId)).thenReturn(true);

        service.importBundle(writeBundle(RAI, List.of(bundleItem("11"))), "tester");

        assertThat(capturedItem().getChangeType()).isEqualTo(MergeItemChangeType.CONFLICT);
    }

    @Test
    void importBundle_unlinked_identifierMatch_classifiedMatchCandidate() throws IOException {
        stubNodeAndAuthority();
        when(itemLinkRepository.findByNodeAndRemoteKey(nodeId, RAI + ":X-1")).thenReturn(Optional.empty());
        when(scopedIdentifierRepository.findByIdentifier("X-1")).thenReturn(List.of(new ScopedIdentifier()));

        service.importBundle(writeBundle(RAI, List.of(bundleItem("11"))), "tester");

        assertThat(capturedItem().getChangeType()).isEqualTo(MergeItemChangeType.MATCH_CANDIDATE);
    }

    @Test
    void importBundle_unlinked_noIdentifierMatch_classifiedAdd() throws IOException {
        stubNodeAndAuthority();
        when(itemLinkRepository.findByNodeAndRemoteKey(nodeId, RAI + ":X-1")).thenReturn(Optional.empty());
        when(scopedIdentifierRepository.findByIdentifier("X-1")).thenReturn(List.of());

        service.importBundle(writeBundle(RAI, List.of(bundleItem("11"))), "tester");

        assertThat(capturedItem().getChangeType()).isEqualTo(MergeItemChangeType.ADD);
    }

    // ===== decide：冲突裁决的 Registrar 门控（D5）=====

    @Test
    void decide_conflict_requiresTargetRegistrar() {
        UUID mrId = UUID.randomUUID();
        MergeRequest mr = underReviewMr(mrId);
        MergeRequestItem item = conflictItem(mr);
        when(mergeRequestRepository.findById(mrId)).thenReturn(Optional.of(mr));
        when(mergeRequestItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        doThrow(new AccessDeniedException("仅目的方注册机构的活动 Registrar 可裁决"))
                .when(mergeRegistrarGate).assertTargetRegistrar(mr);

        assertThatThrownBy(() -> service.decide(mrId, item.getId(), MergeItemResolution.ACCEPT, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(mergeRequestItemRepository, never()).save(any());
    }

    @Test
    void decide_nonConflict_doesNotInvokeRegistrarGate() {
        UUID mrId = UUID.randomUUID();
        MergeRequest mr = underReviewMr(mrId);
        MergeRequestItem item = conflictItem(mr);
        item.setChangeType(MergeItemChangeType.UPDATE);
        when(mergeRequestRepository.findById(mrId)).thenReturn(Optional.of(mr));
        when(mergeRequestItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(mergeRequestItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MergeRequestItemView view = service.decide(mrId, item.getId(), MergeItemResolution.REJECT, null);

        assertThat(view.resolution()).isEqualTo(MergeItemResolution.REJECT);
        verify(mergeRegistrarGate, never()).assertTargetRegistrar(any());
    }

    // ===== getItems：冲突条目的版本三方信息 =====

    @Test
    void getItems_conflictItem_enrichedWithVersionTriplet() {
        UUID mrId = UUID.randomUUID();
        UUID localItemId = UUID.randomUUID();
        MergeRequest mr = underReviewMr(mrId);
        mr.setSourceNode(node);
        mr.setTargetNamespaceId(targetNamespaceId);
        MergeRequestItem item = conflictItem(mr);
        item.setPayloadJson("{\"version\":\"11\",\"sourceNamespaceGlobalId\":\"uuid-1\",\"identifier\":\"X-1\"}");
        when(mergeRequestRepository.findById(mrId)).thenReturn(Optional.of(mr));
        when(mergeRequestItemRepository.findByMergeRequestId(mrId)).thenReturn(List.of(item));
        ItemLink link = new ItemLink();
        link.setLocalItemId(localItemId);
        when(itemLinkRepository.findByNodeAndRemoteKey(nodeId, item.getRemoteItemKey()))
                .thenReturn(Optional.of(link));
        when(mergeVersionSupport.mergeBaseVersion(localItemId, "uuid-1", "X-1", targetNamespaceId))
                .thenReturn("10");
        when(mergeVersionSupport.targetVersion(localItemId, targetNamespaceId)).thenReturn("2");

        MergeRequestItemView view = service.getItems(mrId).get(0);

        assertThat(view.sourceVersion()).isEqualTo("11");
        assertThat(view.baseVersion()).isEqualTo("10");
        assertThat(view.localVersion()).isEqualTo("2");
    }

    // ===== 辅助 =====

    private void stubNodeAndAuthority() {
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        when(registrationAuthorityRepository.findByRai(RAI)).thenReturn(Optional.of(ra));
        when(registryNodeRepository.findAll()).thenReturn(List.of(node));
        when(mergeRequestRepository.save(any())).thenAnswer(inv -> {
            MergeRequest mr = inv.getArgument(0);
            mr.setId(UUID.randomUUID());
            return mr;
        });
        when(watermarkRepository.findByNodeId(nodeId)).thenReturn(Optional.empty());
        when(mergeRequestItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubSuccessfulImportPrereqs(UUID localItemId, String baseVersion) {
        stubNodeAndAuthority();
        ItemLink link = new ItemLink();
        link.setLocalItemId(localItemId);
        when(itemLinkRepository.findByNodeAndRemoteKey(nodeId, RAI + ":X-1"))
                .thenReturn(Optional.of(link));
        when(itemRepository.findById(localItemId)).thenReturn(Optional.of(mock(Item.class)));
        when(mergeVersionSupport.mergeBaseVersion(localItemId, "uuid-1", "X-1", targetNamespaceId))
                .thenReturn(baseVersion);
    }

    private MergeRequestItem capturedItem() {
        ArgumentCaptor<MergeRequestItem> captor = ArgumentCaptor.forClass(MergeRequestItem.class);
        verify(mergeRequestItemRepository).save(captor.capture());
        return captor.getValue();
    }

    private MergeRequest underReviewMr(UUID mrId) {
        MergeRequest mr = new MergeRequest();
        mr.setId(mrId);
        mr.setStatus(MergeRequestStatus.UNDER_REVIEW);
        return mr;
    }

    private MergeRequestItem conflictItem(MergeRequest mr) {
        MergeRequestItem item = new MergeRequestItem();
        item.setId(UUID.randomUUID());
        item.setMergeRequest(mr);
        item.setRemoteItemKey(RAI + ":X-1");
        item.setChangeType(MergeItemChangeType.CONFLICT);
        return item;
    }

    private Map<String, Object> bundleItem(String version) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("remoteItemKey", RAI + ":X-1");
        item.put("identifier", "X-1");
        item.put("version", version);
        item.put("sourceNamespaceGlobalId", "uuid-1");
        return item;
    }

    private Path writeBundle(String sourceRai, List<Map<String, Object>> items) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", BundleManifest.FORMAT_V1);
        manifest.put("sourceRai", sourceRai);
        manifest.put("targetNamespaceId", targetNamespaceId.toString());
        manifest.put("items", items);
        Path file = tempDir.resolve("bundle-" + UUID.randomUUID() + ".json");
        new ObjectMapper().writeValue(file.toFile(), manifest);
        return file;
    }
}
