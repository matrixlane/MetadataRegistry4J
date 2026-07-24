package io.github.matrixlane.metadataregistry.web.federation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.web.entity.VersionHistory;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.DefinitionRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import io.github.matrixlane.metadataregistry.web.repository.VersionHistoryRepository;
import io.github.matrixlane.metadataregistry.web.service.MetadataExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 合并包构建与增量计算（federation bundle v1，单文件 JSON）。
 *
 * <p>增量来源：sync_watermark 的 lastMergedAt —— 首次合并为全部候选条目；
 * 之后仅包含该时间以来有版本历史的条目。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FederationBundleService {

    private final RegistryNodeService registryNodeService;
    private final SyncWatermarkRepository watermarkRepository;
    private final MergeRequestRepository mergeRequestRepository;
    private final AdministeredItemRepository administeredItemRepository;
    private final VersionHistoryRepository versionHistoryRepository;
    private final DesignationRepository designationRepository;
    private final DefinitionRepository definitionRepository;
    private final MetadataExportService exportService;
    private final ObjectMapper objectMapper;

    /**
     * 计算增量并生成合并包文件，同时登记 DRAFT 状态的合并请求。
     */
    @Transactional
    public MergeRequestView createBundle(UUID nodeId, UUID targetNamespaceId) {
        RegistryNode node = registryNodeService.getEnabled(nodeId);
        SyncWatermark watermark = watermarkRepository.findByNodeId(nodeId).orElse(null);
        LocalDateTime since = watermark == null ? null : watermark.getLastMergedAt();

        List<AdministeredItem> items = collectItems(since);
        Map<UUID, Integer> versions = maxVersionPerItem();

        List<BundleItemSnapshot> snapshots = new ArrayList<>();
        for (AdministeredItem ai : items) {
            BundleItemSnapshot snapshot = toSnapshot(ai, node, versions);
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        if (snapshots.isEmpty()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    since == null ? "没有可提交的条目" : "自上次合并以来没有新的变更");
        }

        Path out = writeBundle(node, targetNamespaceId, watermark, snapshots);

        MergeRequest mr = new MergeRequest();
        mr.setSourceNode(node);
        mr.setTargetNamespaceId(targetNamespaceId);
        mr.setStatus(MergeRequestStatus.DRAFT);
        mr.setBundleRef(out.toAbsolutePath().toString());
        mr = mergeRequestRepository.save(mr);
        log.info("federation bundle created: {} items -> {} (mr={})", snapshots.size(), out, mr.getId());
        return MergeRequestView.of(mr);
    }

    private List<AdministeredItem> collectItems(LocalDateTime since) {
        if (since == null) {
            return administeredItemRepository.findAll();
        }
        // 增量以 AdministeredItem.lastChangeDateTime 为准（不依赖 version_history 的覆盖度）
        return administeredItemRepository.findChangedAfter(since);
    }

    private BundleItemSnapshot toSnapshot(AdministeredItem ai, RegistryNode node, Map<UUID, Integer> versions) {
        if (ai.getScopedIdentifiers() == null || ai.getScopedIdentifiers().isEmpty()) {
            // 无来源标识符的条目无法表达全局逻辑身份，跳过
            return null;
        }
        ScopedIdentifier si = ai.getScopedIdentifiers().get(0);
        String remoteItemKey = node.getRai() + ":" + si.getIdentifier();
        UUID itemUuid = ai.getItem() != null ? ai.getItem().getId() : null;

        List<LangValue> designations = new ArrayList<>();
        List<LangValue> definitions = new ArrayList<>();
        if (itemUuid != null) {
            for (Designation d : designationRepository.findByItem_Id(itemUuid)) {
                designations.add(new LangValue(
                        d.getLanguage() != null ? d.getLanguage().getLanguageIdentifier() : null,
                        d.getSign() != null ? d.getSign().getValue() : null));
            }
            for (Definition d : definitionRepository.findByItem_Id(itemUuid)) {
                definitions.add(new LangValue(
                        d.getLanguage() != null ? d.getLanguage().getLanguageIdentifier() : null,
                        d.getText() != null ? d.getText().getValue() : null));
            }
        }

        return new BundleItemSnapshot(
                remoteItemKey,
                ai.getId(),
                node.getRai(),
                si.getIdentifier(),
                si.getVersion(),
                versions.getOrDefault(ai.getId(), 0),
                exportService.exportItem(ai),
                designations,
                definitions
        );
    }

    private Path writeBundle(RegistryNode node, UUID namespaceId,
                             SyncWatermark watermark, List<BundleItemSnapshot> snapshots) {
        try {
            Path dir = Paths.get("data", "federation");
            Files.createDirectories(dir);
            Path out = dir.resolve("bundle-" + node.getRai() + "-" + System.currentTimeMillis() + ".json");

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("format", BundleManifest.FORMAT_V1);
            manifest.put("createdAt", Instant.now().toString());
            manifest.put("sourceNodeId", node.getId().toString());
            manifest.put("sourceRai", node.getRai());
            manifest.put("targetNamespaceId", namespaceId.toString());
            manifest.put("watermarkVersion", watermark == null ? null : watermark.getLastMergedVersion());
            manifest.put("receiptCallback", "file");
            manifest.put("items", snapshots);

            ObjectMapper mapper = objectMapper.copy()
                    .setFilterProvider(new SimpleFilterProvider()
                            .addFilter("exportFilter", SimpleBeanPropertyFilter.serializeAll()));
            try (Writer writer = Files.newBufferedWriter(out)) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(writer, manifest);
            }
            return out;
        } catch (IOException e) {
            throw new BusinessException("合并包写入失败: " + e.getMessage());
        }
    }

    private Map<UUID, Integer> maxVersionPerItem() {
        Map<UUID, Integer> result = new HashMap<>();
        for (VersionHistory v : versionHistoryRepository.findAll()) {
            result.merge(v.getItemId(), v.getVersionNumber(), Math::max);
        }
        return result;
    }
}
