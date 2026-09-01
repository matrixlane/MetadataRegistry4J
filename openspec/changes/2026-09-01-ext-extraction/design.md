# 设计:扩展剥离(ext-extraction)

## 关键决策(均来自已批准计划 typed-imagining-barto)

1. **先代码级剥离,后物理拆分**:本变更只做模块/类/属性级的迁移与删除,不搬表、不改 schema;MDR/EXT 物理拆分由后续 `mdr-ext-schema-split` 统一做(@ExtSchema + MetadataBuilderInitializer 方言条件化 + ALTER SCHEMA TRANSFER)。理由:一次变更一个关注点,剥离阶段可用 ddl-auto=update 自动建卫星表,风险可控。
2. **卫星实体模式**:标准实体(Namespace/ScopedIdentifier/Contact/LanguageIdentification/NamingConvention)上的扩展属性,一律剥为一对一卫星实体(NamespaceIdentifierPolicy/ScopedIdentifierLifecycle/ContactAccountBinding/LanguageGovernance/NamingConventionRules),标准实体恢复"只有标准属性"。web 服务层在组装/拆解时读写两侧,**DTO 与前端字段保持不变**——这是零前端改动承诺的实现手段。
3. **标识符策略框架整体迁移**:35 类作为一个内聚单元迁 ext 模块,包名 `io.github.matrixlane.metadataregistry.ext.identification`;引用点(NamespaceService、InitWizardService、联邦包、item-mapping 的 Versions/SemVerValidator)统一改指。版本治理(VersionStatus/VersioningPolicy/Versions/SemVerValidator)随迁,因为它们是标识符生命周期的一部分。
4. **Classification 先迁后重建**:现有自造实现迁入 ext 保留功能,javadoc 声明妥协;按 11179-3 第 10 章标准模型重建另立 change,避免本变更范围膨胀。
5. **删除清单即计划 2.1 节**:审批流/通知/数据质量/导出/FHIR/definition_audit_log/draft-generation/schema-extraction 均为无前端死代码,适用用户确认的"无前端可先行移除"原则;删除时同步清路由/菜单/i18n 残留。
6. **Slot 恢复标准属性名 type**:Slot 生产未接线,改动限于 common-facilities + 迁移脚本(列改名),低风险顺手对齐。
7. **第 11 章审计只出报告**:item_mapping* 三表是否对应标准模型,经 Milvus 核实 11179-3 第 11 章原文后出结论,MDR/EXT 归属由用户决策,本变更不动 item-mapping 模块本体(仅改其对 Versions/SemVerValidator 的依赖指向)。

## 迁移脚本约定(沿用阶段 1 模式)

- SQL Server:IF NOT EXISTS 加列/建表后,同批次后续语句一律 EXEC(N'...') 动态 SQL(同批编译看不到新对象);删 FK 列先用 sys.foreign_keys 动态定位约束名。
- dm:每语句独立 BEGIN EXECUTE IMMEDIATE '...' EXCEPTION WHEN OTHERS THEN NULL; END; / 块。
- sqlite:占位 SELECT 1,新列由 ddl-auto=update 补建。
- 每个卫星实体迁移:建新表(或交由 ddl-auto)→ 从原表回填 → 动态删旧 FK → 删旧列。
