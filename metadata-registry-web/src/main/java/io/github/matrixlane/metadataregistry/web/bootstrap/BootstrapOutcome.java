package io.github.matrixlane.metadataregistry.web.bootstrap;

/**
 * 数据库初始化引导结论。ready 为全部步骤成功；failure 记录首个失败步骤与原因。
 */
public record BootstrapOutcome(boolean ready, BootstrapFailure failure) {

    public static BootstrapOutcome success() {
        return new BootstrapOutcome(true, null);
    }

    public static BootstrapOutcome failed(BootstrapFailure failure) {
        return new BootstrapOutcome(false, failure);
    }
}
