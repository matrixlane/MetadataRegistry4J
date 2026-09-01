package io.github.matrixlane.metadataregistry.web.federation;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.ext.identification.Versions;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.web.entity.VersionHistory;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierVersionHistoryRepository;
import io.github.matrixlane.metadataregistry.web.repository.VersionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 合并基与本地编辑判定（registry-merge-strategy D4/D5）。
 *
 * <p>合并基 = 目的方上来源命名空间标识的 version（上次迁移的来源版本），
 * 命名空间副本经全局命名空间共享 UUID 定位。目的方本地编辑判定 = 目的方命名
 * 空间标识版本相较上次迁移（MERGE 标记的标识版本历史）后递增；旧数据无
 * MERGE 标记时回退到条目级版本历史（version_history）佐证。
 */
@Component
@RequiredArgsConstructor
public class MergeVersionSupport {

    /** MERGE 标记前缀：合并执行器在目的方命名空间标识的版本历史上以此记录迁移点。 */
    public static final String MERGE_MARKER = "MERGE";

    private final AdministeredItemRepository administeredItemRepository;
    private final IdentifierVersionHistoryRepository identifierVersionHistoryRepository;
    private final VersionHistoryRepository versionHistoryRepository;
    private final GlobalNamespaceService globalNamespaceService;

    /**
     * 合并基：目的方上来源命名空间标识的版本。优先经全局 UUID 定位来源命名
     * 空间副本；缺失时回退按来源标识符值匹配（排除目的方命名空间）。
     */
    @Transactional(readOnly = true)
    public String mergeBaseVersion(UUID localItemId, String sourceNamespaceGlobalId,
                                   String sourceIdentifier, UUID targetNamespaceId) {
        AdministeredItem ai = administeredItemRepository.findByItem_Id(localItemId).orElse(null);
        if (ai == null || ai.getScopedIdentifiers() == null) {
            return null;
        }
        if (sourceNamespaceGlobalId != null && !sourceNamespaceGlobalId.isBlank()) {
            Optional<Namespace> copy = globalNamespaceService.findNamespaceByGlobalId(sourceNamespaceGlobalId);
            if (copy.isPresent()) {
                Optional<String> version = ai.getScopedIdentifiers().stream()
                        .filter(si -> si.getNamespace() != null
                                && copy.get().getId().equals(si.getNamespace().getId()))
                        .map(ScopedIdentifier::getVersion)
                        .findFirst();
                if (version.isPresent()) {
                    return version.get();
                }
            }
        }
        return ai.getScopedIdentifiers().stream()
                .filter(si -> sourceIdentifier != null && sourceIdentifier.equals(si.getIdentifier()))
                .filter(si -> si.getNamespace() != null
                        && !targetNamespaceId.equals(si.getNamespace().getId()))
                .map(ScopedIdentifier::getVersion)
                .findFirst()
                .orElse(null);
    }

    /**
     * 目的方条目是否在上次迁移后被本地更新：目的方命名空间标识版本与最近一次
     * MERGE 标记的历史版本不一致；无 MERGE 标记（旧数据）时以条目级版本历史
     * 佐证（最近 MERGE 快照之后仍有本地快照即视为本地更新）。
     */
    @Transactional(readOnly = true)
    public boolean locallyEdited(UUID localItemId, UUID targetNamespaceId) {
        AdministeredItem ai = administeredItemRepository.findByItem_Id(localItemId).orElse(null);
        if (ai == null) {
            return false;
        }
        ScopedIdentifier targetSi = targetIdentifier(ai, targetNamespaceId).orElse(null);
        if (targetSi != null) {
            var mergeMarker = identifierVersionHistoryRepository
                    .findByScopedIdentifierIdOrderByTimestampDesc(targetSi.getId()).stream()
                    .filter(h -> h.getChangeSummary() != null && h.getChangeSummary().startsWith(MERGE_MARKER))
                    .findFirst();
            if (mergeMarker.isPresent()) {
                return !Objects.equals(targetSi.getVersion(), mergeMarker.get().getVersion());
            }
        }
        List<VersionHistory> history = versionHistoryRepository
                .findByItemIdOrderByVersionNumberDesc(localItemId);
        if (history.isEmpty()) {
            return false;
        }
        // 最新快照为 MERGE 则之后无本地编辑；否则视为上次迁移后有本地更新
        return !MERGE_MARKER.equals(history.get(0).getChangeType());
    }

    /**
     * 目的方命名空间下该条目的当前标识版本（评审视图展示用）。
     */
    @Transactional(readOnly = true)
    public String targetVersion(UUID localItemId, UUID targetNamespaceId) {
        AdministeredItem ai = administeredItemRepository.findByItem_Id(localItemId).orElse(null);
        if (ai == null) {
            return null;
        }
        return targetIdentifier(ai, targetNamespaceId)
                .map(ScopedIdentifier::getVersion)
                .orElse(null);
    }

    private Optional<ScopedIdentifier> targetIdentifier(AdministeredItem ai, UUID targetNamespaceId) {
        if (ai.getScopedIdentifiers() == null || targetNamespaceId == null) {
            return Optional.empty();
        }
        return ai.getScopedIdentifiers().stream()
                .filter(si -> si.getNamespace() != null
                        && targetNamespaceId.equals(si.getNamespace().getId()))
                .findFirst();
    }
}
