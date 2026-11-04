package io.github.matrixlane.metadataregistry.web.dto.datasource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DatasourceApplyRequest(
        @NotNull(message = "重启标识不能为空")
        UUID restartId,

        @NotNull(message = "数据源配置不能为空")
        @Valid
        DatasourceConfigRequest config
) {}
