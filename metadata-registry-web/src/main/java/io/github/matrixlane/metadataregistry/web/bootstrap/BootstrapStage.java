package io.github.matrixlane.metadataregistry.web.bootstrap;

/**
 * 数据库初始化引导的步骤。步骤顺序即声明顺序；任一步失败即短路后续步骤。
 */
public enum BootstrapStage {
    /** 连接校验：受控超时内取连接并验证可用性。 */
    CONNECTION,
    /** 结构创建：仅全新库——由实体元数据导出 DDL 并执行。 */
    SCHEMA,
    /** 迁移/基线化：程序化 Flyway；SQL Server 附带历史表搬迁。 */
    MIGRATION,
    /** 旧库修复：遗留漂移 SQLite 库的 language_identification 修复。 */
    REPAIR,
    /** 静态预置：通用基准脚本与静态预置集合。 */
    PRESET,
    /** 标识符策略装载：注册表装载与序列提供方绑定。 */
    STRATEGY,
    /** 治理数据回填：Registry Context 补建。 */
    BACKFILL;

    /** 面向用户的步骤描述，用于失败信息。 */
    public String describe() {
        return switch (this) {
            case CONNECTION -> "数据库连接校验";
            case SCHEMA -> "数据库结构创建";
            case MIGRATION -> "数据库迁移";
            case REPAIR -> "遗留数据修复";
            case PRESET -> "静态数据预置";
            case STRATEGY -> "标识符策略装载";
            case BACKFILL -> "治理数据回填";
        };
    }
}
