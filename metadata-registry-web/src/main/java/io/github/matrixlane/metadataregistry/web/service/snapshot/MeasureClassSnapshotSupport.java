package io.github.matrixlane.metadataregistry.web.service.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.dto.measureclass.MeasureClassRequest;
import io.github.matrixlane.metadataregistry.web.dto.measureclass.MeasureClassResponse;
import io.github.matrixlane.metadataregistry.web.service.MeasureClassService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MeasureClassSnapshotSupport {

    private final MeasureClassService service;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void restore(UUID itemId, JsonNode itemJson) {
        MeasureClassResponse response = objectMapper.treeToValue(itemJson, MeasureClassResponse.class);
        MeasureClassRequest request = new MeasureClassRequest(
                response.sourceUri(),
                SnapshotMappingSupport.mapDesignations(response.designations()),
                SnapshotMappingSupport.mapDefinitions(response.definitions()),
                null,
                response.dimensionalities() == null
                        ? null
                        : response.dimensionalities().stream().map(d -> d.id()).toList()
        );
        service.update(itemId, request);
    }
}
