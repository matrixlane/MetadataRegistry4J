package io.github.matrixlane.metadataregistry.web.federation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Versions;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 合并请求生命周期与合并包导入（文件通道）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MergeRequestService {

    private final MergeRequestRepository mergeRequestRepository;
    private final MergeRequestItemRepository mergeRequestItemRepository;
    private final RegistryNodeRepository registryNodeRepository;
    private final ItemRepository itemRepository;
    private final ItemLinkRepository itemLinkRepository;
    private final ScopedIdentifierRepository scopedIdentifierRepository;
    private final SyncWatermarkRepository watermarkRepository;
    private final ObjectMapper objectMapper;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final MergeVersionSupport mergeVersionSupport;
    private final MergeRegistrarGate mergeRegistrarGate;

    @Transactional(readOnly = true)
    public List<MergeRequestView> list(MergeRequestStatus status) {
        if (status != null) {
            return mergeRequestRepository.findByStatusOrderByCreatedAtDesc(status).stream()
                    .map(MergeRequestView::of).toList();
        }
        return mergeRequestRepository.findAll().stream().map(MergeRequestView::of).toList();
    }

    @Transactional(readOnly = true)
    public MergeRequest get(UUID id) {
        return mergeRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MergeRequest", id));
    }

    @Transactional(readOnly = true)
    public MergeRequestView getView(UUID id) {
        return MergeRequestView.of(get(id));
    }

    @Transactional(readOnly = true)
    public List<MergeRequestItemView> getItems(UUID mrId) {
        MergeRequest mr = get(mrId);
        return mergeRequestItemRepository.findByMergeRequestId(mrId).stream()
                .map(item -> toItemView(item, mr))
                .toList();
    }

    /**
     * 评审视图：冲突条目补充版本三方信息（来源版本 / 合并基 / 目的方本地版本），
     * 供目的方 Registrar 裁决。
     */
    private MergeRequestItemView toItemView(MergeRequestItem item, MergeRequest mr) {
        MergeRequestItemView view = MergeRequestItemView.of(item);
        if (item.getChangeType() != MergeItemChangeType.CONFLICT || mr.getSourceNode() == null) {
            return view;
        }
        String sourceVersion = null;
        String sourceNamespaceGlobalId = null;
        String sourceIdentifier = null;
        try {
            JsonNode payload = objectMapper.readTree(item.getPayloadJson());
            sourceVersion = payload.path("version").asText(null);
            sourceNamespaceGlobalId = payload.path("sourceNamespaceGlobalId").asText(null);
            sourceIdentifier = payload.path("identifier").asText(null);
        } catch (Exception ignored) {
        }
        UUID localItemId = itemLinkRepository
                .findByNodeAndRemoteKey(mr.getSourceNode().getId(), item.getRemoteItemKey())
                .map(ItemLink::getLocalItemId)
                .orElse(null);
        if (localItemId == null) {
            return view.withVersions(sourceVersion, null, null);
        }
        String baseVersion = mergeVersionSupport.mergeBaseVersion(localItemId,
                sourceNamespaceGlobalId, sourceIdentifier, mr.getTargetNamespaceId());
        String localVersion = mergeVersionSupport.targetVersion(localItemId, mr.getTargetNamespaceId());
        return view.withVersions(sourceVersion, baseVersion, localVersion);
    }

    @Transactional
    public MergeRequestView submit(UUID mrId, String actor) {
        MergeRequest mr = get(mrId);
        if (mr.getStatus() != MergeRequestStatus.DRAFT) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "仅 DRAFT 状态可提交");
        }
        mr.setStatus(MergeRequestStatus.SUBMITTED);
        mr.setSubmittedBy(actor);
        mr.setSubmittedAt(LocalDateTime.now());
        return MergeRequestView.of(mergeRequestRepository.save(mr));
    }

    @Transactional
    public MergeRequestView startReview(UUID mrId) {
        MergeRequest mr = get(mrId);
        if (mr.getStatus() != MergeRequestStatus.SUBMITTED) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "仅 SUBMITTED 状态可进入评审");
        }
        mr.setStatus(MergeRequestStatus.UNDER_REVIEW);
        return MergeRequestView.of(mergeRequestRepository.save(mr));
    }

    @Transactional
    public MergeRequestView reject(UUID mrId, String actor) {
        MergeRequest mr = get(mrId);
        mr.setStatus(MergeRequestStatus.REJECTED);
        mr.setReviewedBy(actor);
        mr.setReviewedAt(LocalDateTime.now());
        return MergeRequestView.of(mergeRequestRepository.save(mr));
    }

    @Transactional
    public MergeRequestItemView decide(UUID mrId, UUID itemId, MergeItemResolution resolution, UUID linkedLocalItemId) {
        MergeRequest mr = get(mrId);
        if (mr.getStatus() != MergeRequestStatus.UNDER_REVIEW) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "仅评审中状态可裁决条目");
        }
        MergeRequestItem item = mergeRequestItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("MergeRequestItem", itemId));
        if (!item.getMergeRequest().getId().equals(mrId)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "条目不属于该合并请求");
        }
        // 迁移冲突必须由目的方注册机构的活动 Registrar 裁决（D5）
        if (item.getChangeType() == MergeItemChangeType.CONFLICT) {
            mergeRegistrarGate.assertTargetRegistrar(mr);
        }
        item.setResolution(resolution);
        if (linkedLocalItemId != null) {
            item.setLinkedLocalItemId(linkedLocalItemId);
        }
        return MergeRequestItemView.of(mergeRequestItemRepository.save(item));
    }

    /**
     * 文件通道：导入合并包 JSON，生成本地（目标端）合并请求与条目分类。
     */
    @Transactional
    public MergeRequestView importBundle(Path bundlePath, String actor) {
        JsonNode manifest = readManifest(bundlePath);
        String sourceRai = manifest.path("sourceRai").asText(null);
        UUID targetNamespaceId = UUID.fromString(manifest.path("targetNamespaceId").asText());
        // RAI 一致性前置（registry-merge-strategy D1，接收侧）：来源 RAI 必须能
        // 在本实例匹配到注册机构，否则视为不同注册机构，拒绝注册表合并。
        registrationAuthorityRepository.findByRai(sourceRai)
                .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "RAI 不一致：来源 RAI=" + sourceRai + " 与本实例注册机构不匹配，"
                                + "拒绝注册表合并（RAI 不同的协作请使用条目级合并请求）"));
        RegistryNode sourceNode = registryNodeRepository.findAll().stream()
                .filter(n -> n.getRai().equals(sourceRai))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "来源节点未登记（RAI=" + sourceRai + "），请先在联邦节点中登记对端"));

        MergeRequest mr = new MergeRequest();
        mr.setSourceNode(sourceNode);
        mr.setTargetNamespaceId(targetNamespaceId);
        mr.setStatus(MergeRequestStatus.SUBMITTED);
        mr.setBundleRef(bundlePath.toAbsolutePath().toString());
        mr.setSubmittedBy(actor);
        mr.setSubmittedAt(LocalDateTime.now());
        mr = mergeRequestRepository.save(mr);

        SyncWatermark watermark = watermarkRepository.findByNodeId(sourceNode.getId()).orElse(null);
        int[] counts = {0, 0, 0, 0};
        for (JsonNode itemNode : manifest.path("items")) {
            MergeItemChangeType changeType = classify(sourceNode, itemNode, watermark, targetNamespaceId);
            counts[changeType.ordinal()]++;

            MergeRequestItem item = new MergeRequestItem();
            item.setMergeRequest(mr);
            item.setRemoteItemKey(itemNode.path("remoteItemKey").asText());
            String remoteUuid = itemNode.path("remoteItemUuid").asText(null);
            item.setRemoteItemUuid(remoteUuid == null ? null : UUID.fromString(remoteUuid));
            item.setChangeType(changeType);
            item.setResolution(MergeItemResolution.PENDING);
            item.setPayloadJson(itemNode.toString());
            mergeRequestItemRepository.save(item);
        }
        log.info("bundle imported: mr={}, ADD={}, UPDATE={}, CONFLICT={}, MATCH={}",
                mr.getId(), counts[MergeItemChangeType.ADD.ordinal()],
                counts[MergeItemChangeType.UPDATE.ordinal()],
                counts[MergeItemChangeType.CONFLICT.ordinal()],
                counts[MergeItemChangeType.MATCH_CANDIDATE.ordinal()]);
        return MergeRequestView.of(mr);
    }

    /**
     * 条目分类：已链接 → 版本比对（来源未变更 UPDATE 执行期跳过；来源单侧更新
     * UPDATE；双侧更新 CONFLICT）；标识符匹配 → MATCH_CANDIDATE；否则 ADD。
     */
    private MergeItemChangeType classify(RegistryNode sourceNode, JsonNode itemNode,
                                         SyncWatermark watermark, UUID targetNamespaceId) {
        String remoteKey = itemNode.path("remoteItemKey").asText();
        Optional<ItemLink> link = itemLinkRepository.findByNodeAndRemoteKey(sourceNode.getId(), remoteKey);
        if (link.isPresent()) {
            // 链接目标已被删除（如种子重建导致）→ 清理失效链接，按身份匹配重新处理
            if (itemRepository.findById(link.get().getLocalItemId()).isEmpty()) {
                log.warn("dangling item_link detected for {}, falling back to identity matching", remoteKey);
                itemLinkRepository.delete(link.get());
                return MergeItemChangeType.MATCH_CANDIDATE;
            }
            UUID localItemId = link.get().getLocalItemId();
            String sourceVersion = itemNode.path("version").asText(null);
            String base = mergeVersionSupport.mergeBaseVersion(localItemId,
                    itemNode.path("sourceNamespaceGlobalId").asText(null),
                    itemNode.path("identifier").asText(null),
                    targetNamespaceId);
            if (base != null && sourceVersion != null && Versions.compare(sourceVersion, base) <= 0) {
                // 来源版本未较合并基递增 → 未变更（执行期幂等跳过）
                return MergeItemChangeType.UPDATE;
            }
            // 来源已更新（或旧数据缺合并基）：目的方本地也更新过则构成冲突（D5）
            return mergeVersionSupport.locallyEdited(localItemId, targetNamespaceId)
                    ? MergeItemChangeType.CONFLICT
                    : MergeItemChangeType.UPDATE;
        }
        String identifier = itemNode.path("identifier").asText(null);
        if (identifier != null && !scopedIdentifierRepository.findByIdentifier(identifier).isEmpty()) {
            return MergeItemChangeType.MATCH_CANDIDATE;
        }
        return MergeItemChangeType.ADD;
    }

    private JsonNode readManifest(Path bundlePath) {
        try {
            JsonNode root = objectMapper.readTree(Files.readString(bundlePath));
            if (!BundleManifest.FORMAT_V1.equals(root.path("format").asText())) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED, "不支持的合并包格式");
            }
            return root;
        } catch (IOException e) {
            throw new BusinessException("合并包读取失败: " + e.getMessage());
        }
    }

    /**
     * 源节点应用合并回执：更新水位线与反向链接（凭回执建立）。
     * 回执丢失重放时按条目幂等跳过。
     */
    @Transactional
    public Map<String, Object> applyReceipt(UUID nodeId, String receiptJson) {
        try {
            JsonNode receipt = objectMapper.readTree(receiptJson);
            if (!"mdr-federation-receipt-v1".equals(receipt.path("format").asText())) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED, "不支持的回执格式");
            }
            RegistryNode node = registryNodeRepository.findById(nodeId)
                    .orElseThrow(() -> new ResourceNotFoundException("RegistryNode", nodeId));

            int linked = 0;
            for (JsonNode item : receipt.path("items")) {
                String remoteKey = item.path("remoteItemKey").asText();
                String remoteUuid = item.path("remoteItemUuid").asText(null);
                if (itemLinkRepository.findByNodeAndRemoteKey(nodeId, remoteKey).isPresent()) {
                    continue; // 幂等：链接已存在
                }
                ItemLink link = new ItemLink();
                link.setLocalItemId(UUID.fromString(item.path("localItemId").asText()));
                link.setRemoteNode(node);
                link.setRemoteItemKey(remoteKey);
                link.setRemoteItemUuid(remoteUuid == null ? null : UUID.fromString(remoteUuid));
                link.setEstablishedByMrId(UUID.fromString(receipt.path("mergeRequestId").asText()));
                itemLinkRepository.save(link);
                linked++;
            }

            SyncWatermark wm = watermarkRepository.findByNodeId(nodeId).orElseGet(SyncWatermark::new);
            wm.setNode(node);
            wm.setLastMergedMrId(UUID.fromString(receipt.path("mergeRequestId").asText()));
            wm.setLastMergedAt(LocalDateTime.now());
            watermarkRepository.save(wm);

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("linksEstablished", linked);
            result.put("watermarkUpdated", true);
            return result;
        } catch (IOException e) {
            throw new BusinessException("回执解析失败: " + e.getMessage());
        }
    }
}
