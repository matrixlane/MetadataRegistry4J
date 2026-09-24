package io.github.matrixlane.metadataregistry.web.service.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.dto.concept.ConceptRequest;
import io.github.matrixlane.metadataregistry.web.dto.concept.ConceptResponse;
import io.github.matrixlane.metadataregistry.web.service.PropertyService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PropertySnapshotSupport {

    private final PropertyService service;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void restore(UUID itemId, JsonNode itemJson) {
        ConceptResponse response = objectMapper.treeToValue(itemJson, ConceptResponse.class);
        ConceptRequest request = new ConceptRequest(
                response.sourceUri(),
                SnapshotMappingSupport.mapDesignations(response.designations()),
                SnapshotMappingSupport.mapDefinitions(response.definitions()),
                null
        );
        service.update(itemId, request);
    }
}
