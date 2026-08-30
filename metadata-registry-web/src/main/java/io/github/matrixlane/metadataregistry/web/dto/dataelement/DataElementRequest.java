package io.github.matrixlane.metadataregistry.web.dto.dataelement;

import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

public record DataElementRequest(
        UUID dataElementConceptId,
        UUID valueDomainId,
        /** 精度：关联数据元值允许的小数位数（ISO/IEC 11179-31:2023 §7.6.2.1.4 Table 23，0..1 Integer） */
        Integer precision,
        @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        @Valid
        ItemRegistrationRequest registration) {
}
