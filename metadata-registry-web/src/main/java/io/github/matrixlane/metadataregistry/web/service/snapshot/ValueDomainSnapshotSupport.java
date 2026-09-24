package io.github.matrixlane.metadataregistry.web.service.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.ValueDomainRequest;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.ValueDomainResponse;
import io.github.matrixlane.metadataregistry.web.service.ValueDomainService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ValueDomainSnapshotSupport {

    private final ValueDomainService service;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void restore(UUID itemId, JsonNode itemJson) {
        ValueDomainResponse response = objectMapper.treeToValue(itemJson, ValueDomainResponse.class);
        ValueDomainRequest request = new ValueDomainRequest(
                response.type(),
                response.dataTypeId(),
                response.unitOfMeasureId(),
                response.maximumCharacterQuantity(),
                response.format(),
                response.validPeriodStart(),
                response.validPeriodEnd(),
                response.describedConceptualDomainId(),
                response.localEnumeratedConceptualDomainId(),
                response.permittedValuesFieldname(),
                response.referencedConceptualDomainId(),
                response.permissibleValues() == null
                        ? null
                        : response.permissibleValues().stream()
                                .map(SnapshotMappingSupport::mapPermissibleValue)
                                .toList(),
                SnapshotMappingSupport.mapDesignations(response.designations()),
                SnapshotMappingSupport.mapDefinitions(response.definitions()),
                null
        );
        service.update(itemId, request);
    }
}
