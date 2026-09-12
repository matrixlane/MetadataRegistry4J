package io.github.matrixlane.metadataregistry.web.inference.provider;

import java.util.UUID;

/**
 * 元数据推断提供方 SPI。内置实现：本地 Coding Agent（ACP 协议常驻进程）。
 */
public interface MetadataInferenceProvider {

    /** 提供方标识，即 Agent id（如 claude）。 */
    String id();

    /** 当前可用性（探测结果随实现缓存）。 */
    ProviderStatus status();

    /** 强制重新探测（绕过缓存）；默认实现等同 {@link #status()}。 */
    default ProviderStatus probe() {
        return status();
    }

    /** 执行一次推断，返回原始文本输出。 */
    InferenceResult infer(InferenceRequest request);

    /**
     * 取消正在执行的推断（杀进程等）。不支持取消的实现保留默认行为。
     *
     * @param jobId 作业标识
     * @return 是否命中并终止了执行中的任务
     */
    default boolean cancel(UUID jobId) {
        return false;
    }

    /** 释放提供方持有的资源（常驻进程等）；应用关闭时由注册表统一调用。 */
    default void destroy() {
    }
}
