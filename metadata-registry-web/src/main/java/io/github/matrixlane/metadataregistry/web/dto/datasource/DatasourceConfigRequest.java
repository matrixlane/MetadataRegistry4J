package io.github.matrixlane.metadataregistry.web.dto.datasource;

import jakarta.validation.constraints.NotBlank;

/**
 * 数据源配置请求。
 *
 * <p>vendor 取值：sqlite / sqlserver / dm。SQLite 仅需 filePath；
 * SQL Server 与达梦需要 host/port/database，username/password 可空
 * （SQL Server 空凭据表示 Windows 集成认证）。
 *
 * <p>edition 仅在初始化向导场景传递（此时 system.edition 尚未落库）；
 * 系统设置场景由后端读取已持久化的版本。
 */
public record DatasourceConfigRequest(
        @NotBlank(message = "数据库方言不能为空")
        String vendor,

        String host,
        Integer port,
        String database,
        String filePath,
        String username,
        String password,
        String schema,
        String edition
) {}
