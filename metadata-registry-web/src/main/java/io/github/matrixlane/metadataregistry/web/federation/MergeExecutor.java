package io.github.matrixlane.metadataregistry.web.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfigService;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierVersionHistory;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.ext.identification.Versions;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierVersionHistoryRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.service.ItemAdministrationService;
import io.github.matrixlane.metadataregistry.web.service.LanguageIdentifierResolver;
import io.github.matrixlane.metadataregistry.web.service.VersionHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 合并执行器：在单事务内完成合并请求的事务性并入。
 *
 * <p>逐条裁决结果应用（ADD 创建、UPDATE/LINK_EXISTING 更新、CONFLICT 按裁决），
 * 写入 MERGE 版本历史，建立/更新条目链接，更新同步水位线，生成合并回执。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MergeExecutor {

    private final MergeRequestRepository mergeRequestRepository;
    private final MergeRequestItemRepository mergeRequestItemRepository;
    private final ItemLinkRepository itemLinkRepository;
    private final SyncWatermarkRepository watermarkRepository;
    private final ItemRepository itemRepository;
    private final NamespaceRepository namespaceRepository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final ScopedIdentifierRepository scopedIdentifierRepository;
    private final io.github.matrixlane.metadataregistry.web.service.ScopedIdentifierLifecycleService lifecycleService;
    private final io.github.matrixlane.metadataregistry.web.service.NamespaceIdentifierSettingsService settingsService;
    private final LanguageIdentificationRepository languageRepository;
    private final LanguageIdentifierResolver languageIdentifierResolver;
    private final ItemAdministrationService itemAdministrationService;
    private final VersionHistoryService versionHistoryService;
    private final GenericItemCreator itemCreator;
    private final ObjectMapper objectMapper;
    private final MergeVersionSupport mergeVersionSupport;
    private final MergeRegistrarGate mergeRegistrarGate;
    private final GlobalNamespaceService globalNamespaceService;
    private final IdentifierVersionHistoryRepository identifierVersionHistoryRepository;
    private final IdentifierStrategyConfigService identifierStrategyConfigService;

    /** 回执输出目录（测试可重定向到临时目录）。 */
    private Path receiptDir = Paths.get("data", "federation");

    void setReceiptDir(Path receiptDir) {
        this.receiptDir = receiptDir;
    }

    @Transactional
    public Map<String, Object> execute(UUID mrId, String actor) {
        MergeRequest mr = mergeRequestRepository.findById(mrId)
                .orElseThrow(() -> new ResourceNotFoundException("MergeRequest", mrId));
        if (mr.getStatus() != MergeRequestStatus.SUBMITTED && mr.getStatus() != MergeRequestStatus.UNDER_REVIEW) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "合并请求状态不允许并入: " + mr.getStatus());
        }
        // 注册表合并的批准必须由目的方注册机构的活动 Registrar 执行（D5）
        mergeRegistrarGate.assertTargetRegistrar(mr);
        List<MergeRequestItem> items = mergeRequestItemRepository.findByMergeRequestId(mrId);
        RegistryNode node = mr.getSourceNode();

        int merged = 0;
        int skipped = 0;
        List<Map<String, Object>> receiptItems = new ArrayList<>();
        for (MergeRequestItem item : items) {
            if (item.getResolution() == MergeItemResolution.REJECT) {
                skipped++;
                continue;
            }
            if (item.getResolution() == MergeItemResolution.PENDING) {
                if (item.getChangeType() == MergeItemChangeType.ADD
                        || item.getChangeType() == MergeItemChangeType.UPDATE) {
                    item.setResolution(MergeItemResolution.ACCEPT);
                } else {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "存在未裁决的条目: " + item.getRemoteItemKey());
                }
            }

            BundleItemSnapshot snapshot = parseSnapshot(item.getPayloadJson());
            AdministeredItem local;
            if (item.getChangeType() == MergeItemChangeType.ADD) {
                local = itemCreator.create(snapshot, mr.getTargetNamespaceId(), node);
            } else {
                local = applyUpdate(item, snapshot, node);
                if (local == null) {
                    // 幂等跳过：来源版本与合并基一致，条目不产生任何变更
                    skipped++;
                    mergeRequestItemRepository.save(item);
                    continue;
                }
            }

            upsertLink(node, local.getId(), snapshot.remoteItemKey(), snapshot.remoteItemUuid(), mrId);
            versionHistoryService.recordSnapshot(local.getItem(), "MERGE");
            mergeRequestItemRepository.save(item);

            Map<String, Object> receiptItem = new LinkedHashMap<>();
            receiptItem.put("remoteItemKey", snapshot.remoteItemKey());
            receiptItem.put("remoteItemUuid", snapshot.remoteItemUuid() == null ? null : snapshot.remoteItemUuid().toString());
            receiptItem.put("localItemId", local.getId().toString());
            receiptItem.put("changeType", item.getChangeType().name());
            receiptItems.add(receiptItem);
            merged++;
        }

        upsertWatermark(node, mr);
        Path receipt = writeReceipt(mr, receiptItems);

        mr.setStatus(MergeRequestStatus.MERGED);
        mr.setReviewedBy(actor);
        mr.setReviewedAt(LocalDateTime.now());
        mr.setMergedAt(LocalDateTime.now());
        mergeRequestRepository.save(mr);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("merged", merged);
        result.put("skipped", skipped);
        result.put("receiptFile", receipt.toAbsolutePath().toString());
        return result;
    }

    /**
     * 应用传入更新；来源版本与合并基一致时返回 null（幂等跳过）。
     */
    private AdministeredItem applyUpdate(MergeRequestItem item, BundleItemSnapshot snapshot, RegistryNode node) {
        ItemLink link = null;
        if (item.getLinkedLocalItemId() != null) {
            // MATCH_CANDIDATE 经评审确认链接到指定本地条目
            link = new ItemLink();
            link.setLocalItemId(item.getLinkedLocalItemId());
            link.setRemoteNode(node);
            link.setRemoteItemKey(snapshot.remoteItemKey());
            link.setRemoteItemUuid(snapshot.remoteItemUuid());
            link.setEstablishedByMrId(item.getMergeRequest().getId());
            link = itemLinkRepository.save(link);
        } else if (item.getChangeType() == MergeItemChangeType.MATCH_CANDIDATE) {
            // 未指定本地条目的匹配候选：按标识符自动匹配本地既有条目
            UUID autoMatched = findLocalItemByIdentifier(snapshot.identifier());
            if (autoMatched == null) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "匹配候选未确认且本地无标识符匹配: " + snapshot.remoteItemKey());
            }
            link = new ItemLink();
            link.setLocalItemId(autoMatched);
            link.setRemoteNode(node);
            link.setRemoteItemKey(snapshot.remoteItemKey());
            link.setRemoteItemUuid(snapshot.remoteItemUuid());
            link.setEstablishedByMrId(item.getMergeRequest().getId());
            link = itemLinkRepository.save(link);
        } else {
            link = itemLinkRepository.findByNodeAndRemoteKey(node.getId(), snapshot.remoteItemKey())
                    .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "条目链接不存在，无法更新: " + snapshot.remoteItemKey()));
        }

        final ItemLink resolvedLink = link;
        Item localItem = itemRepository.findById(resolvedLink.getLocalItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Item", resolvedLink.getLocalItemId()));

        AdministeredItem ai = localItem.getIdentifiedItem() instanceof AdministeredItem existing
                ? existing
                : null;
        if (ai == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "本地条目缺少 AdministeredItem 结构: " + snapshot.remoteItemKey());
        }

        // 合并基判定（D4）：来源版本与目的方上来源命名空间标识版本一致 → 未变更，跳过
        if (item.getChangeType() == MergeItemChangeType.UPDATE && snapshot.version() != null) {
            String base = mergeVersionSupport.mergeBaseVersion(localItem.getId(),
                    snapshot.sourceNamespaceGlobalId(), snapshot.identifier(),
                    item.getMergeRequest().getTargetNamespaceId());
            if (base != null && Versions.compare(snapshot.version(), base) == 0) {
                return null;
            }
        }

        itemAdministrationService.applyDesignations(localItem, toTextLines(snapshot.designations()));
        itemAdministrationService.applyDefinitions(localItem, toTextLines(snapshot.definitions()));

        advanceVersions(ai, snapshot, node, item.getMergeRequest());
        ai.setLastChangeDateTime(LocalDateTime.now());
        itemRepository.save(localItem);
        return ai;
    }

    /**
     * 干净更新/冲突裁决接受后的版本推进（D2/D4）：
     * 来源命名空间标识版本更新为来源版本（推进合并基，自愈补建副本与标识）；
     * 目的方命名空间标识版本按策略递增，并写入 MERGE 标记历史供本地编辑检测。
     */
    private void advanceVersions(AdministeredItem ai, BundleItemSnapshot snapshot,
                                 RegistryNode node, MergeRequest mr) {
        RegistrationAuthority ra = registrationAuthorityRepository.findByRai(node.getRai()).orElse(null);
        Organization namingAuthority = globalNamespaceService.resolveNamingAuthority(
                snapshot.sourceNamespaceNamingAuthorityName(), ra);
        Namespace sourceCopy = globalNamespaceService.ensureNamespaceCopy(
                snapshot.sourceNamespaceGlobalId(), snapshot.sourceNamespaceName(), namingAuthority);

        ScopedIdentifier sourceSi = ai.getScopedIdentifiers().stream()
                .filter(si -> si.getNamespace() != null && sourceCopy.getId().equals(si.getNamespace().getId()))
                .findFirst()
                .orElseGet(() -> {
                    ScopedIdentifier created = new ScopedIdentifier(snapshot.identifier(), sourceCopy, ai);
                    ai.getScopedIdentifiers().add(created);
                    io.github.matrixlane.metadataregistry.ext.identification.ScopedIdentifierLifecycle lc =
                            new io.github.matrixlane.metadataregistry.ext.identification.ScopedIdentifierLifecycle();
                    lc.setCreatedAt(LocalDateTime.now());
                    lifecycleService.stageNew(created, lc);
                    return created;
                });
        sourceSi.setVersion(snapshot.version());

        Namespace targetNs = namespaceRepository.findById(mr.getTargetNamespaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Namespace", mr.getTargetNamespaceId()));
        ScopedIdentifier targetSi = ai.getScopedIdentifiers().stream()
                .filter(si -> si.getNamespace() != null && targetNs.getId().equals(si.getNamespace().getId()))
                .findFirst()
                .orElseGet(() -> {
                    // 自愈：旧数据缺目的方标识时补建（"0" 经递增后即为初始版本 "1"）
                    ScopedIdentifier created = new ScopedIdentifier(
                            identifierStrategyConfigService.generateIdentifier(targetNs, null), targetNs, ai);
                    created.setVersion("0");
                    ai.getScopedIdentifiers().add(created);
                    io.github.matrixlane.metadataregistry.ext.identification.ScopedIdentifierLifecycle lc =
                            new io.github.matrixlane.metadataregistry.ext.identification.ScopedIdentifierLifecycle();
                    lc.setCreatedAt(LocalDateTime.now());
                    lifecycleService.stageNew(created, lc);
                    return created;
                });
        targetSi.setVersion(Versions.bump(targetSi.getVersion(),
                settingsService.getOrDefault(targetNs.getId()).getVersioningPolicy()));
        identifierVersionHistoryRepository.save(new IdentifierVersionHistory(
                targetSi, targetSi.getVersion(),
                targetSi.getId() != null
                        ? lifecycleService.getOrDefault(targetSi.getId()).getVersionStatus()
                        : io.github.matrixlane.metadataregistry.ext.identification.VersionStatus.DRAFT,
                "system", MergeVersionSupport.MERGE_MARKER + " mr=" + mr.getId()));
    }

    private void upsertLink(RegistryNode node, UUID localItemId, String remoteKey, UUID remoteUuid, UUID mrId) {
        ItemLink link = itemLinkRepository.findByNodeAndRemoteKey(node.getId(), remoteKey)
                .orElseGet(ItemLink::new);
        link.setLocalItemId(localItemId);
        link.setRemoteNode(node);
        link.setRemoteItemKey(remoteKey);
        link.setRemoteItemUuid(remoteUuid);
        if (link.getEstablishedByMrId() == null) {
            link.setEstablishedByMrId(mrId);
        }
        itemLinkRepository.save(link);
    }

    private void upsertWatermark(RegistryNode node, MergeRequest mr) {
        SyncWatermark wm = watermarkRepository.findByNodeId(node.getId())
                .orElseGet(SyncWatermark::new);
        wm.setNode(node);
        wm.setLastMergedMrId(mr.getId());
        wm.setLastMergedAt(LocalDateTime.now());
        watermarkRepository.save(wm);
    }

    private Path writeReceipt(MergeRequest mr, List<Map<String, Object>> receiptItems) {
        try {
            Files.createDirectories(receiptDir);
            Path out = receiptDir.resolve("receipt-" + mr.getId() + ".json");
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("format", "mdr-federation-receipt-v1");
            receipt.put("mergeRequestId", mr.getId().toString());
            receipt.put("mergedAt", mr.getMergedAt() == null ? LocalDateTime.now().toString() : mr.getMergedAt().toString());
            receipt.put("sourceNodeId", mr.getSourceNode().getId().toString());
            receipt.put("sourceRai", mr.getSourceNode().getRai());
            receipt.put("items", receiptItems);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), receipt);
            return out;
        } catch (IOException e) {
            throw new BusinessException("合并回执写入失败: " + e.getMessage());
        }
    }

    private BundleItemSnapshot parseSnapshot(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, BundleItemSnapshot.class);
        } catch (IOException e) {
            throw new BusinessException("合并条目快照解析失败: " + e.getMessage());
        }
    }

    private List<TextLine> toTextLines(List<LangValue> values) {
        if (values == null) {
            return null;
        }
        List<TextLine> lines = new ArrayList<>();
        for (LangValue v : values) {
            UUID languageId = resolveLanguageId(v.languageIdentifier());
            if (languageId == null || v.value() == null) {
                continue;
            }
            lines.add(new TextLine(languageId, v.value(), null, null));
        }
        return lines;
    }

    private UUID resolveLanguageId(String identifier) {
        if (identifier == null) {
            return null;
        }
        return languageIdentifierResolver.resolve(identifier)
                .map(LanguageIdentification::getId)
                .orElse(null);
    }

    private UUID findLocalItemByIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        var matches = scopedIdentifierRepository.findByIdentifier(identifier);
        if (matches.isEmpty()) {
            return null;
        }
        return matches.get(0).getIdentifiedItem() != null
                && matches.get(0).getIdentifiedItem().getItem() != null
                ? matches.get(0).getIdentifiedItem().getItem().getId()
                : null;
    }
}
