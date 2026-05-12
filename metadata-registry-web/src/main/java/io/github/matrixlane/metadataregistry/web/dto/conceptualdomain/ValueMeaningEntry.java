package io.github.matrixlane.metadataregistry.web.dto.conceptualdomain;

import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;
import java.util.List;

public record ValueMeaningEntry(
        @NotEmpty(message = "designations cannot be empty") @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        LocalDateTime validPeriodStart,
        LocalDateTime validPeriodEnd) {
}
