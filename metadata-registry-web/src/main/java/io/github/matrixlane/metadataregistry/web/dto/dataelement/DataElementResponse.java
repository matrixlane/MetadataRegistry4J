package io.github.matrixlane.metadataregistry.web.dto.dataelement;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;

import java.util.List;
import java.util.UUID;

public record DataElementResponse(
        UUID id,
        String label,
        UUID dataElementConceptId,
        String dataElementConceptLabel,
        UUID valueDomainId,
        String valueDomainLabel,
        /** 精度：关联数据元值允许的小数位数（ISO/IEC 11179-31:2023 §7.6.2.1.4 Table 23） */
        Integer precision,
        List<TextLineResponse> designations,
        List<TextLineResponse> definitions,
        ItemMetaResponse meta) {
}
