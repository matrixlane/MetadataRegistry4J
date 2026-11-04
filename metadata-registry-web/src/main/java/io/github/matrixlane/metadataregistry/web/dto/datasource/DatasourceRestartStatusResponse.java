package io.github.matrixlane.metadataregistry.web.dto.datasource;

public record DatasourceRestartStatusResponse(
        String restartId,
        String state,
        String verification,
        String reasonCode
) {}
