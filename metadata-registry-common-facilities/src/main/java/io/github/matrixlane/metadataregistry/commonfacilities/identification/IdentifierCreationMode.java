package io.github.matrixlane.metadataregistry.commonfacilities.identification;

/**
 * 创建时用户选择的标识符 / 版本生成方式。
 *
 * <p>与命名空间级 {@link IdentifierInputMode}（AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY，
 * 表达命名空间对创建路径的输入模式约束）解耦：本枚举只记录"用户在创建该 SI 时实际选择
 * 了自动生成还是手动录入"，用于编辑态的可编辑性决策与 Item 创建选择元数据的回溯。
 *
 * <p>持久化为 {@code identifier_creation_mode} / {@code version_creation_mode} 列。
 * NULL 表示迁移前既存记录或外部命名空间下的 SI，按 MANUAL 路径等价处理。
 */
public enum IdentifierCreationMode {

    /** 创建时按命名空间策略自动生成。编辑态输入框 MUST 锁定。 */
    AUTO,

    /** 创建时由用户手动录入。编辑态输入框 MUST 可编辑。 */
    MANUAL
}