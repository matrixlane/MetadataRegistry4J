package io.github.matrixlane.metadataregistry.web.service.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.dto.dec.DataElementConceptRequest;
import io.github.matrixlane.metadataregistry.web.dto.dec.DataElementConceptResponse;
import io.github.matrixlane.metadataregistry.web.service.DataElementConceptService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataElementConceptSnapshotSupport {

    private final DataElementConceptService service;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void restore(UUID itemId, JsonNode itemJson) {
        DataElementConceptResponse response = objectMapper.treeToValue(itemJson, DataElementConceptResponse.class);
        DataElementConceptRequest request = new DataElementConceptRequest(
                response.objectClassId(),
                response.propertyId(),
                response.sourceUri(),
                response.conceptualDomainIds(),
                SnapshotMappingSupport.mapDesignations(response.designations()),
                SnapshotMappingSupport.mapDefinitions(response.definitions()),
                null
        );
        service.update(itemId, request);
    }
}
