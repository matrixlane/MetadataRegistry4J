package io.github.matrixlane.metadataregistry.web.dto.datasource;

/**
 * 当前数据源的只读视图（口令等敏感信息不回传）。
 *
 * <p>除脱敏后的 JDBC URL 外，同时回传结构化连接参数（主机/端口/库名/文件路径），
 * 供初始化向导把"配置新的数据源"表单预填为当前配置；凭据一律不回传，
 * 仅以 {@code hasCredentials} 标记当前数据源是否使用用户名口令认证。
 */
public record DatasourceConfigView(
        String vendor,
        String jdbcUrl,
        String schema,
        String driverClassName,
        String host,
        Integer port,
        String database,
        String filePath,
        boolean hasCredentials
) {}
