package io.github.matrixlane.metadataregistry.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.web.dto.version.FieldDiffEntry;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionDiffResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionHistoryEntryResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionSnapshotResponse;
import io.github.matrixlane.metadataregistry.web.entity.VersionHistory;
import io.github.matrixlane.metadataregistry.web.repository.VersionHistoryRepository;
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

    private static final int MAX_SUMMARY_LENGTH = 500;
    private static final List<String> AUDITED_ITEM_TYPES = List.of(
            "DataElement", "ValueDomain", "DataElementConcept",
            "ObjectClass", "Property", "ConceptualDomain",
            "DataType", "UnitOfMeasure", "MeasureClass"
    );

    public boolean isAuditedType(String itemType) {
        return itemType != null && AUDITED_ITEM_TYPES.contains(itemType);
    }

    @Transactional
    public void recordSnapshot(Item item, String changeType) {
        if (item == null || item.getId() == null) {
            return;
        }
        String itemType = item.getClass().getSimpleName();
        if (!isAuditedType(itemType)) {
            return;
        }

        try {
            int nextVersion = repository.findTopByItemIdOrderByVersionNumberDesc(item.getId())
                    .map(vh -> vh.getVersionNumber() + 1)
                    .orElse(1);

            String snapshotJson = objectMapper.writeValueAsString(item);
            String summary = buildChangeSummary(item, changeType);

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
        } catch (Exception e) {
            log.warn("Failed to record version snapshot for item {}: {}", item.getId(), e.getMessage());
        }
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

        JsonNode snapshot = parseJson(vh.getSnapshotJson());
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

    @SneakyThrows
    List<FieldDiffEntry> computeDiff(String baseJson, String compareJson) {
        JsonNode base = parseJson(baseJson);
        JsonNode compare = parseJson(compareJson);

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
