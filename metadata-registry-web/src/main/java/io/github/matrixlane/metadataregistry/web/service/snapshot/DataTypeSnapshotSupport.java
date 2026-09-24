package io.github.matrixlane.metadataregistry.web.service.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.dto.datatype.DataTypeRequest;
import io.github.matrixlane.metadataregistry.web.dto.datatype.DataTypeResponse;
import io.github.matrixlane.metadataregistry.web.service.DataTypeService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataTypeSnapshotSupport {

    private final DataTypeService service;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void restore(UUID itemId, JsonNode itemJson) {
        DataTypeResponse response = objectMapper.treeToValue(itemJson, DataTypeResponse.class);
        DataTypeRequest request = new DataTypeRequest(
                response.dataTypeSchemeId(),
                response.annotation(),
                SnapshotMappingSupport.mapDesignations(response.designations()),
                SnapshotMappingSupport.mapDefinitions(response.definitions()),
                null
        );
        service.update(itemId, request);
    }
}
