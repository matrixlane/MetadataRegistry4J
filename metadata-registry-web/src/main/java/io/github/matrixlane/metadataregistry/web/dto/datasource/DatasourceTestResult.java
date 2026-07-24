package io.github.matrixlane.metadataregistry.web.dto.datasource;

/**
 * 数据源连接测试结果。
 */
public record DatasourceTestResult(
        boolean success,
        String databaseProductName,
        String databaseVersion,
        String message
) {}
