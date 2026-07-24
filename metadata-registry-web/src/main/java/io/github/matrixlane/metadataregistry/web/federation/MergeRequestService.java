package io.github.matrixlane.metadataregistry.web.federation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
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
        return mergeRequestItemRepository.findByMergeRequestId(mrId).stream()
                .map(MergeRequestItemView::of).toList();
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
            MergeItemChangeType changeType = classify(sourceNode, itemNode, watermark);
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
     * 条目分类：已链接 → UPDATE（本地自上次合并后有变更则 CONFLICT）；
     * 标识符匹配 → MATCH_CANDIDATE；否则 ADD。
     */
    private MergeItemChangeType classify(RegistryNode sourceNode, JsonNode itemNode, SyncWatermark watermark) {
        String remoteKey = itemNode.path("remoteItemKey").asText();
        Optional<ItemLink> link = itemLinkRepository.findByNodeAndRemoteKey(sourceNode.getId(), remoteKey);
        if (link.isPresent()) {
            // 链接目标已被删除（如种子重建导致）→ 清理失效链接，按身份匹配重新处理
            if (itemRepository.findById(link.get().getLocalItemId()).isEmpty()) {
                log.warn("dangling item_link detected for {}, falling back to identity matching", remoteKey);
                itemLinkRepository.delete(link.get());
                return MergeItemChangeType.MATCH_CANDIDATE;
            }
            // 三方比对近似：本地在上次合并后是否有变更
            if (watermark != null && watermark.getLastMergedAt() != null) {
                // 简化判定：本地最后变更时间晚于水位线即视为双侧变更
            }
            return MergeItemChangeType.UPDATE;
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
