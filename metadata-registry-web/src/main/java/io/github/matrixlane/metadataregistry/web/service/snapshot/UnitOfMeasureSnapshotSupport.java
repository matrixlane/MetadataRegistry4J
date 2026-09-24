package io.github.matrixlane.metadataregistry.web.service.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.dto.unitofmeasure.UnitOfMeasureRequest;
import io.github.matrixlane.metadataregistry.web.dto.unitofmeasure.UnitOfMeasureResponse;
import io.github.matrixlane.metadataregistry.web.service.UnitOfMeasureService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UnitOfMeasureSnapshotSupport {

    private final UnitOfMeasureService service;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void restore(UUID itemId, JsonNode itemJson) {
        UnitOfMeasureResponse response = objectMapper.treeToValue(itemJson, UnitOfMeasureResponse.class);
        UnitOfMeasureRequest request = new UnitOfMeasureRequest(
                response.sourceUri(),
                SnapshotMappingSupport.mapDesignations(response.designations()),
                SnapshotMappingSupport.mapDefinitions(response.definitions()),
                null,
                response.measureClasses() == null
                        ? null
                        : response.measureClasses().stream().map(mc -> mc.id()).toList()
        );
        service.update(itemId, request);
    }
}
