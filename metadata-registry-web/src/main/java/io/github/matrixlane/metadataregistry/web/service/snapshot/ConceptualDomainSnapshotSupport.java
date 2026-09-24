package io.github.matrixlane.metadataregistry.web.service.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainRequest;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainResponse;
import io.github.matrixlane.metadataregistry.web.service.ConceptualDomainService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ConceptualDomainSnapshotSupport {

    private final ConceptualDomainService service;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void restore(UUID itemId, JsonNode itemJson) {
        ConceptualDomainResponse response = objectMapper.treeToValue(itemJson, ConceptualDomainResponse.class);
        ConceptualDomainRequest request = new ConceptualDomainRequest(
                response.type(),
                response.sourceUri(),
                SnapshotMappingSupport.mapDesignations(response.designations()),
                SnapshotMappingSupport.mapDefinitions(response.definitions()),
                response.valueMeanings() == null
                        ? null
                        : response.valueMeanings().stream()
                                .map(SnapshotMappingSupport::mapValueMeaning)
                                .toList(),
                response.validPeriodStart(),
                response.validPeriodEnd(),
                null,
                response.dimensionalityId()
        );
        service.update(itemId, request);
    }
}
