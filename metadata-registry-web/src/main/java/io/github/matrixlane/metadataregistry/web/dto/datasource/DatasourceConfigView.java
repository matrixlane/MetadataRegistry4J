package io.github.matrixlane.metadataregistry.web.dto.datasource;

/**
 * 当前数据源的只读视图（口令等敏感信息不回传）。
 */
public record DatasourceConfigView(
        String vendor,
        String jdbcUrl,
        String schema,
        String driverClassName
) {}
