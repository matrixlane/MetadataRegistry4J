package io.github.matrixlane.metadataregistry.web.dto.export.datadictionary;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 数据字典单项导出内容
 */
public record DataDictionaryItemExport(
        UUID id,
        String itemType,
        String label,
        String definition,
        String identifier,
        String version,
        RegistrationStatus registrationStatus,
        UUID dataElementConceptId,
        String dataElementConceptLabel,
        UUID valueDomainId,
        String valueDomainLabel,
        String dataTypeLabel,
        String unitOfMeasureLabel,
        Integer maximumCharacterQuantity,
        String format,
        List<String> designations,
        List<String> definitions,
        List<PermissibleValueExport> permissibleValues,
        LocalDateTime creationDateTime,
        LocalDateTime lastChangeDateTime,
        String changeDescription,
        String explanatoryComment,
        String origin
) {

    public record PermissibleValueExport(
            String value,
            String meaning,
            String description
    ) {}
}
