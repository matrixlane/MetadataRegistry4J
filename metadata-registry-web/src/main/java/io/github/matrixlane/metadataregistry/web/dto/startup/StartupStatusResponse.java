package io.github.matrixlane.metadataregistry.web.dto.startup;

import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapFailure;

/**
 * 启动就绪状态。failure 仅在数据库初始化引导失败时返回（含失败阶段、原因码与
 * 可读描述）；引导成功或未启用新架构时不出现该字段（全局 non_null 序列化）。
 */
public record StartupStatusResponse(boolean ready, BootstrapFailure failure) {

    public StartupStatusResponse(boolean ready) {
        this(ready, null);
    }
}
