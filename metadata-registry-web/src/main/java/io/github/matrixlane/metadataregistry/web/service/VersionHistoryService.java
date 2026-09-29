package io.github.matrixlane.metadataregistry.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DefinitionContext;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationContext;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.dto.version.FieldDiffEntry;
import io.github.matrixlane.metadataregistry.web.dto.version.SnapshotKind;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionDiffResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionHistoryEntryResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionSnapshotResponse;
import io.github.matrixlane.metadataregistry.web.entity.VersionHistory;
import io.github.matrixlane.metadataregistry.web.repository.VersionHistoryRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VersionHistoryService {

    private final VersionHistoryRepository repository;
    private final ObjectMapper objectMapper;
    private final ItemAdministrationService itemAdministrationService;

    private static final int MAX_SUMMARY_LENGTH = 500;
    private static final List<String> AUDITED_ITEM_TYPES = List.of(
            "DataElement", "ValueDomain", "DataElementConcept",
            "ObjectClass", "Property", "ConceptualDomain",
            "DataType", "UnitOfMeasure", "MeasureClass"
    );

    public boolean isAuditedType(String itemType) {
        return itemType != null && AUDITED_ITEM_TYPES.contains(itemType);
    }

    /**
     * 记录条目快照（随业务事务同步落库）。
     *
     * <p>便捷重载：由调用方传入完整 Response DTO，内部序列化为 JSON 后嵌入
     * {@code {"meta": {...}, "item": <dto>}} 结构。该方式避开 JPA 实体图双向关联
     * 导致的无限递归，同时扩展为全量 DTO 快照。</p>
     *
     * <p>仅「快照构建」阶段的异常被吞掉（跳过快照、不影响业务写入）；
     * 查询与落库阶段的异常向上传播，由业务事务回滚——响亮失败，
     * 避免此前 catch 全吞导致的业务数据部分提交。</p>
     */
    @Transactional
    public void recordSnapshot(Item item, String changeType) {
        recordSnapshot(item, changeType, null);
    }

    /**
     * 记录条目快照（全量 DTO 模式）。
     *
     * <p>{@code responseDto} 为 null 时回退到手工构建的轻量快照（仅指称/定义），
     * 保留旧调用点的兼容性；传入 DTO 时写入完整 Response 结构。</p>
     */
    @Transactional
    public void recordSnapshot(Item item, String changeType, Object responseDto) {
        SnapshotKind kind = deriveKind(changeType, item);
        recordSnapshot(item, changeType, responseDto, kind);
    }

    private void recordSnapshot(Item item, String changeType, Object responseDto, SnapshotKind kind) {
        if (item == null || item.getId() == null) {
            return;
        }
        String itemType = item.getClass().getSimpleName();
        if (!isAuditedType(itemType)) {
            return;
        }
        // ISO/IEC 11179-6:2023 4.3.3.1.2: Incomplete 不入版本控制
        RegistrationState regState = getRegistrationState(item);
        if (regState != null && regState.getRegistrationStatus() == RegistrationStatus.INCOMPLETE) {
            return;
        }

        String snapshotJson;
        String summary;
        try {
            JsonNode itemNode = responseDto != null
                    ? objectMapper.valueToTree(responseDto)
                    : null;
            snapshotJson = buildSnapshotJson(item, itemType, itemNode, kind);
            summary = buildChangeSummary(item, changeType);
        } catch (Exception e) {
            log.warn("Failed to build version snapshot for item {}: {}", item.getId(), e.getMessage());
            return;
        }

        int nextVersion = repository.findTopByItemIdOrderByVersionNumberDesc(item.getId())
                .map(vh -> vh.getVersionNumber() + 1)
                .orElse(1);
        VersionHistory vh = VersionHistory.of(
                item.getId(),
                itemType,
                nextVersion,
                changeType,
                currentUser(),
                LocalDateTime.now(),
                snapshotJson,
                summary
        );
        repository.save(vh);
    }

    /**
     * 当管理项从非草稿（且非拒绝）状态首次进入草稿编辑时，捕获编辑前一刻的
     * 完整状态作为 {@link SnapshotKind#BASELINE} 基线快照。
     *
     * <p>调用点须位于应用请求字段之前，使用编辑前实体构建的 Response DTO。</p>
     */
    @Transactional
    public void captureBaselineIfEnteringDraft(Item item, Object responseDto) {
        if (item == null || item.getId() == null) {
            return;
        }
        String itemType = item.getClass().getSimpleName();
        if (!isAuditedType(itemType)) {
            return;
        }
        // ISO/IEC 11179-6:2023 4.3.3.1.2: Incomplete 不入版本控制
        RegistrationState regState = getRegistrationState(item);
        if (regState != null && regState.getRegistrationStatus() == RegistrationStatus.INCOMPLETE) {
            return;
        }
        AdministrativeStatus status = getAdministrativeStatus(item);
        if (status == AdministrativeStatus.DRAFT || status == AdministrativeStatus.REJECTED) {
            return;
        }
        recordSnapshot(item, "BASELINE", responseDto, SnapshotKind.BASELINE);
    }

    /**
     * 查找某 Item 最近一条基线快照。
     */
    @Transactional(readOnly = true)
    public Optional<VersionHistory> findLatestBaseline(UUID itemId) {
        return repository.findByItemIdOrderByVersionNumberDesc(itemId).stream()
                .filter(vh -> SnapshotKind.BASELINE == readKind(vh.getSnapshotJson()))
                .findFirst();
    }

    private SnapshotKind readKind(String snapshotJson) {
        JsonNode snapshot = parseJson(snapshotJson);
        JsonNode meta = snapshot.get("meta");
        if (meta == null) {
            return null;
        }
        JsonNode kindNode = meta.get("kind");
        if (kindNode == null) {
            return null;
        }
        try {
            return SnapshotKind.valueOf(kindNode.asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private SnapshotKind deriveKind(String changeType, Item item) {
        if (itemAdministrationService.isRestoreInProgress()) {
            return SnapshotKind.ROLLED_BACK;
        }
        if ("BASELINE".equals(changeType)) {
            return SnapshotKind.BASELINE;
        }
        if ("CREATE".equals(changeType)) {
            return SnapshotKind.SUBMITTED;
        }
        AdministrativeStatus status = getAdministrativeStatus(item);
        if ("UPDATE".equals(changeType)) {
            return status == AdministrativeStatus.DRAFT ? SnapshotKind.DRAFT_EDIT : SnapshotKind.SUBMITTED;
        }
        return SnapshotKind.SUBMITTED;
    }

    private AdministrativeStatus getAdministrativeStatus(Item item) {
        RegistrationState state = getRegistrationState(item);
        return state == null ? null : state.getAdministrativeStatus();
    }

    private RegistrationState getRegistrationState(Item item) {
        if (item == null || !(item.getIdentifiedItem() instanceof AdministeredItem ai)) {
            return null;
        }
        return ai.getRegistrationState();
    }

    /** 构建含 meta + item 的快照 JSON。itemNode 为 null 时回退到手工轻量结构。 */
    private String buildSnapshotJson(Item item, String itemType, JsonNode itemNode, SnapshotKind kind)
            throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode meta = root.putObject("meta");
        meta.put("itemType", itemType);
        RegistrationState state = getRegistrationState(item);
        meta.put("registrationStatus",
                state == null || state.getRegistrationStatus() == null
                        ? null : state.getRegistrationStatus().name());
        meta.put("administrativeStatus",
                state == null || state.getAdministrativeStatus() == null
                        ? null : state.getAdministrativeStatus().standardValue());
        meta.put("kind", kind.name());
        if (itemNode != null) {
            root.set("item", itemNode);
        } else {
            root.set("item", buildLegacyItemSnapshot(item, itemType));
        }
        return objectMapper.writeValueAsString(root);
    }

    /** 手工构建无循环引用的轻量快照 JSON（仅标量与设计/指称行的展示字段）。 */
    private JsonNode buildLegacyItemSnapshot(Item item, String itemType) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", item.getId().toString());
        root.put("itemType", itemType);

        ArrayNode designations = root.putArray("designations");
        if (item.getDesignations() != null) {
            for (Designation d : item.getDesignations()) {
                ObjectNode node = designations.addObject();
                node.put("id", d.getId() == null ? null : d.getId().toString());
                node.put("value", d.getSign() == null ? null : d.getSign().getValue());
                node.put("language", d.getLanguage() == null ? null : d.getLanguage().getLanguageIdentifier());
                node.put("acceptability", d.getAcceptability() == null ? null : d.getAcceptability().name());
                ArrayNode contexts = node.putArray("contexts");
                if (d.getDesignationContexts() != null) {
                    for (DesignationContext dc : d.getDesignationContexts()) {
                        ObjectNode c = contexts.addObject();
                        c.put("context", dc.getContext() == null
                                ? null : ItemNamingHelper.preferredDesignation(dc.getContext()));
                        c.put("acceptability", dc.getAcceptability() == null ? null : dc.getAcceptability().name());
                    }
                }
            }
        }

        ArrayNode definitions = root.putArray("definitions");
        if (item.getDefinitions() != null) {
            for (Definition d : item.getDefinitions()) {
                ObjectNode node = definitions.addObject();
                node.put("id", d.getId() == null ? null : d.getId().toString());
                node.put("value", d.getText() == null ? null : d.getText().getValue());
                node.put("language", d.getLanguage() == null ? null : d.getLanguage().getLanguageIdentifier());
                node.put("acceptability", d.getAcceptability() == null ? null : d.getAcceptability().name());
                ArrayNode contexts = node.putArray("contexts");
                if (d.getDefinitionContexts() != null) {
                    for (DefinitionContext dc : d.getDefinitionContexts()) {
                        ObjectNode c = contexts.addObject();
                        c.put("context", dc.getContext() == null
                                ? null : ItemNamingHelper.preferredDesignation(dc.getContext()));
                        c.put("acceptability", dc.getAcceptability() == null ? null : dc.getAcceptability().name());
                    }
                }
            }
        }

        return root;
    }

    @Transactional(readOnly = true)
    public List<VersionHistoryEntryResponse> listVersions(UUID itemId) {
        return repository.findByItemIdOrderByVersionNumberDesc(itemId).stream()
                .map(vh -> new VersionHistoryEntryResponse(
                        vh.getId(),
                        vh.getVersionNumber(),
                        vh.getChangeType(),
                        vh.getChangedBy(),
                        vh.getChangedAt(),
                        vh.getChangeSummary()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public VersionDiffResponse diff(UUID itemId, Integer baseVersion, Integer compareVersion) {
        VersionHistory base = repository.findByItemIdAndVersionNumber(itemId, baseVersion)
                .orElseThrow(() -> new io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException(
                        "VersionHistory", itemId + ":" + baseVersion));
        VersionHistory compare = repository.findByItemIdAndVersionNumber(itemId, compareVersion)
                .orElseThrow(() -> new io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException(
                        "VersionHistory", itemId + ":" + compareVersion));

        List<FieldDiffEntry> diffs = computeDiff(base.getSnapshotJson(), compare.getSnapshotJson());
        boolean hasDifferences = diffs.stream().anyMatch(d -> d.type() != FieldDiffEntry.DiffType.UNCHANGED);

        return new VersionDiffResponse(
                itemId.toString(),
                base.getItemType(),
                baseVersion,
                compareVersion,
                base.getChangedAt(),
                compare.getChangedAt(),
                base.getChangedBy(),
                compare.getChangedBy(),
                diffs,
                hasDifferences
        );
    }

    @Transactional(readOnly = true)
    public VersionSnapshotResponse getSnapshot(UUID itemId, Integer versionNumber) {
        VersionHistory vh = repository.findByItemIdAndVersionNumber(itemId, versionNumber)
                .orElseThrow(() -> new io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException(
                        "VersionHistory", itemId + ":" + versionNumber));

        // 为兼容前端版本历史 UI，返回内层 item 视图（不含 meta 包装）
        JsonNode snapshot = parseJson(vh.getSnapshotJson());
        snapshot = extractItemNode(snapshot);
        return new VersionSnapshotResponse(
                vh.getId(),
                vh.getVersionNumber(),
                vh.getChangeType(),
                vh.getChangedBy(),
                vh.getChangedAt(),
                vh.getChangeSummary(),
                snapshot
        );
    }

    private JsonNode extractItemNode(JsonNode snapshot) {
        if (snapshot == null) {
            return objectMapper.createObjectNode();
        }
        JsonNode item = snapshot.get("item");
        return item != null ? item : snapshot;
    }

    @SneakyThrows
    List<FieldDiffEntry> computeDiff(String baseJson, String compareJson) {
        JsonNode base = extractItemNode(parseJson(baseJson));
        JsonNode compare = extractItemNode(parseJson(compareJson));

        Map<String, FieldDiffEntry> result = new TreeMap<>();
        diffNodes("", base, compare, result);

        return result.values().stream()
                .sorted(Comparator.comparing(FieldDiffEntry::fieldName))
                .toList();
    }

    private void diffNodes(String path, JsonNode base, JsonNode compare, Map<String, FieldDiffEntry> result) {
        if (base.isObject() && compare.isObject()) {
            ObjectNode baseObj = (ObjectNode) base;
            ObjectNode compareObj = (ObjectNode) compare;

            Iterator<String> baseFields = baseObj.fieldNames();
            while (baseFields.hasNext()) {
                String field = baseFields.next();
                String fieldPath = path.isEmpty() ? field : path + "." + field;
                JsonNode baseVal = baseObj.get(field);
                JsonNode compareVal = compareObj.get(field);

                if (compareVal == null) {
                    result.put(fieldPath, new FieldDiffEntry(
                            fieldPath, field, FieldDiffEntry.DiffType.REMOVED,
                            jsonToValue(baseVal), null));
                } else if (isScalar(baseVal) && isScalar(compareVal)) {
                    if (!baseVal.equals(compareVal)) {
                        result.put(fieldPath, new FieldDiffEntry(
                                fieldPath, field, FieldDiffEntry.DiffType.MODIFIED,
                                jsonToValue(baseVal), jsonToValue(compareVal)));
                    } else {
                        result.put(fieldPath, new FieldDiffEntry(
                                fieldPath, field, FieldDiffEntry.DiffType.UNCHANGED,
                                jsonToValue(baseVal), jsonToValue(compareVal)));
                    }
                } else if (baseVal.isArray() && compareVal.isArray()) {
                    if (!baseVal.equals(compareVal)) {
                        result.put(fieldPath, new FieldDiffEntry(
                                fieldPath, field, FieldDiffEntry.DiffType.MODIFIED,
                                jsonToValue(baseVal), jsonToValue(compareVal)));
                    } else {
                        result.put(fieldPath, new FieldDiffEntry(
                                fieldPath, field, FieldDiffEntry.DiffType.UNCHANGED,
                                jsonToValue(baseVal), jsonToValue(compareVal)));
                    }
                } else {
                    diffNodes(fieldPath, baseVal, compareVal, result);
                }
            }

            Iterator<String> compareFields = compareObj.fieldNames();
            while (compareFields.hasNext()) {
                String field = compareFields.next();
                String fieldPath = path.isEmpty() ? field : path + "." + field;
                if (!baseObj.has(field)) {
                    result.put(fieldPath, new FieldDiffEntry(
                            fieldPath, field, FieldDiffEntry.DiffType.ADDED,
                            null, jsonToValue(compareObj.get(field))));
                }
            }
        } else if (!base.equals(compare)) {
            String fieldName = path.isEmpty() ? "root" : path;
            result.put(fieldName, new FieldDiffEntry(
                    fieldName, fieldName, FieldDiffEntry.DiffType.MODIFIED,
                    jsonToValue(base), jsonToValue(compare)));
        }
    }

    private boolean isScalar(JsonNode node) {
        return node.isTextual() || node.isNumber() || node.isBoolean() || node.isNull();
    }

    private Object jsonToValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode child : node) {
                list.add(jsonToValue(child));
            }
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                map.put(entry.getKey(), jsonToValue(entry.getValue()));
            }
            return map;
        }
        return node.toString();
    }

    @SneakyThrows
    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(json);
    }

    private String buildChangeSummary(Item item, String changeType) {
        StringBuilder sb = new StringBuilder();
        sb.append(changeType).append(" ");
        String name = item.toString();
        if (name != null && name.length() > 100) {
            name = name.substring(0, 97) + "...";
        }
        sb.append(name);
        String summary = sb.toString();
        return summary.length() > MAX_SUMMARY_LENGTH
                ? summary.substring(0, MAX_SUMMARY_LENGTH - 3) + "..."
                : summary;
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "system";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser user) {
            return user.username();
        }
        return auth.getName();
    }
}
