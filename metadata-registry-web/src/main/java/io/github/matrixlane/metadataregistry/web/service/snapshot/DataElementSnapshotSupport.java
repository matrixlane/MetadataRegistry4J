package io.github.matrixlane.metadataregistry.web.service.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementResponse;
import io.github.matrixlane.metadataregistry.web.service.DataElementService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataElementSnapshotSupport {

    private final DataElementService service;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void restore(UUID itemId, JsonNode itemJson) {
        DataElementResponse response = objectMapper.treeToValue(itemJson, DataElementResponse.class);
        DataElementRequest request = new DataElementRequest(
                response.dataElementConceptId(),
                response.valueDomainId(),
                response.precision(),
                SnapshotMappingSupport.mapDesignations(response.designations()),
                SnapshotMappingSupport.mapDefinitions(response.definitions()),
                null
        );
        service.update(itemId, request);
    }
}
