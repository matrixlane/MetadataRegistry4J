package io.github.matrixlane.metadataregistry.web.bootstrap;

/**
 * 启动相位。
 *
 * <p>{@link #WIZARD}：数据源配置未被确认（无配置、未确认、指纹失配、既有部署数据库
 * 不可达）——启动与运行零数据库行为；{@link #FULL}：数据源配置已确认——执行数据库
 * 初始化引导。引导失败不形成新的相位，由 {@code DatabaseBootstrapService} 的运行
 * 时结论表达。
 */
public enum BootstrapPhase {
    WIZARD,
    FULL
}
