package io.github.matrixlane.metadataregistry.web.service.snapshot;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineContextEntry;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineContextResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ValueMeaningEntry;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ValueMeaningResponse;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.PermissibleValueEntry;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.PermissibleValueResponse;

import java.util.List;

/**
 * 快照 Response DTO → Update Request 的公共字段映射工具。
 *
 * <p>Response 中未保留的字段（如 namingConventionIds、isolatedException）回退时按
 * null 处理；前端如需保留可在后续迭代扩展 Response。</p>
 */
public final class SnapshotMappingSupport {

    private SnapshotMappingSupport() {
    }

    public static List<TextLine> mapDesignations(List<TextLineResponse> responses) {
        if (responses == null) {
            return List.of();
        }
        return responses.stream().map(SnapshotMappingSupport::mapTextLine).toList();
    }

    public static List<TextLine> mapDefinitions(List<TextLineResponse> responses) {
        if (responses == null) {
            return List.of();
        }
        return responses.stream().map(SnapshotMappingSupport::mapTextLine).toList();
    }

    public static TextLine mapTextLine(TextLineResponse response) {
        if (response == null) {
            return null;
        }
        return new TextLine(
                response.id(),
                response.languageId(),
                response.value(),
                response.sourceId(),
                mapContexts(response.contexts()),
                null,
                null
        );
    }

    public static List<TextLineContextEntry> mapContexts(List<TextLineContextResponse> responses) {
        if (responses == null) {
            return null;
        }
        return responses.stream()
                .map(c -> new TextLineContextEntry(c.contextId(), parseAcceptability(c.acceptability())))
                .toList();
    }

    public static Acceptability parseAcceptability(String value) {
        if (value == null) {
            return null;
        }
        return Acceptability.valueOf(value);
    }

    public static PermissibleValueEntry mapPermissibleValue(PermissibleValueResponse response) {
        if (response == null) {
            return null;
        }
        return new PermissibleValueEntry(
                response.permittedValue(),
                mapDesignations(response.designations()),
                mapDefinitions(response.definitions()),
                response.validPeriodStart(),
                response.validPeriodEnd()
        );
    }

    public static ValueMeaningEntry mapValueMeaning(ValueMeaningResponse response) {
        if (response == null) {
            return null;
        }
        return new ValueMeaningEntry(
                mapDesignations(response.designations()),
                mapDefinitions(response.definitions()),
                response.validPeriodStart(),
                response.validPeriodEnd()
        );
    }
}
