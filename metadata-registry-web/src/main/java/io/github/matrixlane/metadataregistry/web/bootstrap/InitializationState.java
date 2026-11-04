package io.github.matrixlane.metadataregistry.web.bootstrap;

import java.time.Instant;

/**
 * 本地初始化状态记录（{@code ~/.metadata-registry/initialization-state.yml}）。
 *
 * <p>系统"是否已初始化"与"数据源是否已确认"的权威来源，不依赖数据库查询。
 *
 * @param datasourceConfigured 数据源是否已被用户确认（向导保存或"保持当前数据源"）
 * @param datasourceFingerprint 确认时有效数据源配置的指纹（{@link DatasourceFingerprint}）
 * @param initialized 系统是否已完成初始化（向导 commit）
 * @param edition 部署版本记录（未初始化时可为 null）
 * @param updatedAt 最近一次写入时间
 */
public record InitializationState(
        int schemaVersion,
        boolean datasourceConfigured,
        String datasourceFingerprint,
        boolean initialized,
        String edition,
        Instant updatedAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static InitializationState fresh() {
        return new InitializationState(
                CURRENT_SCHEMA_VERSION, false, null, false, null, Instant.now());
    }

    public InitializationState withDatasourceConfirmed(String fingerprint) {
        return new InitializationState(
                schemaVersion, true, fingerprint, initialized, edition, Instant.now());
    }

    public InitializationState withInitialized(boolean value) {
        return new InitializationState(
                schemaVersion, datasourceConfigured, datasourceFingerprint, value, edition, Instant.now());
    }

    public InitializationState withEdition(String value) {
        return new InitializationState(
                schemaVersion, datasourceConfigured, datasourceFingerprint, initialized, value, Instant.now());
    }
}
