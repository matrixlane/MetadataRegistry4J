# Design: registry-specification-model-correction

详细设计见 `docs/superpowers/specs/2026-10-09-registry-specification-model-correction-design.md`（Superpowers 设计稿，含标准原文证据与方案比较）。本文件记录 OpenSpec 语义下的技术决策。

## 标准依据（Milvus 核实，2026-10-09）

- ISO/IEC 11179-3:2023 §9.4.10 Registry_Specification class：无 Direct superclass 小节；Associations 为 "No associations are specified in this metamodel region."；Attributes 表 28 全为自有属性（`name 1 Sign`、`comment`、`web_address`、`default_language`、`alternative_language 0..*`、`standard`、`degree_of_conformance`、`conformance_level`、`conformance_label`、`included_features`、`excluded_features`、`default_time_zone`）。
- 对照组（确认标准声明 Item 子类的固定句式）：Namespace §7.3.3.1、Context §6.4.2.3、Naming_Convention §8.4.4.1、Constraint_Set §9.4.5.1、Classification_Scheme_Item_Relationship_Type §10.3.4.1。

## 技术决策

### 决策 1：独立根实体而非其他继承安排

`RegistrySpecification extends Entity`（`Entity` 为 `@MappedSuperclass`，仅提供 `UUID id` 主键）。同模块既有 5 个先例（`Contact`、`Individual`、`LanguageIdentification`、`Organization`、`Role`）。表结构不变（`mdr.registry_specification` 本就自有全部表 28 列），仅消除 JOINED 语义与 FK。

备选（保留为 ext 扩展、直接关联替代继承）被否决：均属非标准扩展，违背"标准没有的不保留"裁定。

### 决策 2：孤儿清理机制整体退役而非保留

`cleanupOrphanRecords()`（`@EventListener(ApplicationReadyEvent)`）与 `findOrphanIds()` 随 FK 根除而删除。历史迁移 `V20260728001__Clean_registry_specification_orphans.sql` 保留不动（Flyway 已应用历史；语义在新模型下自然空转，重放无副作用）。此举同时消除 2026-10-09 启动挂起事故的性能根因（全表 `itemRepository.findAll()` 反连接内存比对）。

### 决策 3：三方言迁移采用"动态解析 FK + 全守卫"

- **SQL Server**：`V20261009008` 经 `sys.foreign_keys` 动态解析 `mdr.registry_specification` 上指向 `mdr.item` 的 FK（Hibernate 命名策略生成、名称不可硬编码，`SELECT DISTINCT` 防复合约束重复）并动态删除；随后按 FK 依赖序删除 spec 的 designation 关联子行（`designation_context`/`designation_definition_pairing`/`designation_namespace`/`naming_convention_conformance`）→ designation → definition 子表（`definition_context` + pairing 的 definition_id 侧）→ definition → slot → **ext**.version_history → 注册链（ext.registration_transition_approval/attached_item/administered_item/scoped_identifier/registered_item/identified_item）→ item 基行。version_history 与 transition 表自 mdr-standard-compliance-cleanup 起属 ext schema（mdr 同名旧表为历史残留，不在本迁移范围）。全新库（无 FK 无基行）由条件删除天然空转。**评审 B1 修正**：不得引用已被 V20261009005/09006 删除的旧名表（`designation_context_relation`/`designation_naming_convention`）——曾被旧 jar 重建的残留实例不在本迁移清理范围。
- **SQLite**：裸表名；存在性守卫同上。全新 SQLite 库由 Hibernate 建表无 FK，迁移天然空转；若真实 SQLite 库核实存在声明式 FK，按项目既有"重建表"模式（参见 V20260921001 先例）重建为无 FK 表。
- **达梦**：`MDR.` 全限定，逻辑同 SQL Server；无实例，脚本审查 + sqlite/sqlserver 冒烟代替实测（项目惯例）。

### 决策 4：防再发 = 机械守护 + 规则 + 记忆三层

1. **守护测试** `StandardInheritanceConformanceTest`（common-facilities，纯反射）：硬断言 `RegistrySpecification.superclass == Entity`；扫描全部 `@Entity`，凡 `extends Item` 者必须命中白名单（条目含标准条款引用，已核实 6 条 + 待审计占位指向 backlog），新增未登记继承边即失败。
2. **AGENTS.md 增补"实体继承规则"**：extends 关系必须有对应标准 "Direct superclass" 条款依据；新增/变更继承必须同步白名单并以 Milvus/PDF 原文核对。
3. **记忆库**：session journal 记录事故链；LEARNING_BACKLOG 登记其余 Item 子类全量审计。

### 决策 5：联邦/搜索清理为删除而非保留死分支

`instanceof RegistrySpecification` 过滤与搜索类型分支在新模型下类型上恒假/恒 0 命中，属死代码，删除以免误导。`MetadataSearchController.SUPPORTED_TYPES` 移除该类型——实际降级语义为 `sanitizeType` 把未知类型滤掉返回 `null`（等价于不按类型过滤、返回全部类型命中），而非 `1=0`；spec delta"结果集中不包含注册规范记录"的断言仍成立（spec 不产生 item 行，天然不可达）。

## 数据形态（迁移前 → 迁移后）

迁移前（JOINED，由 `createDefault` 产生）：

```
mdr.item                    1 基行（仅 id）
mdr.registry_specification  1 行（同 id，FK→item）
mdr.designation             1 行（item_id=该 id，value=spec.name）
mdr.item …（identified_item/administered_item/slot/definition 均 0 行）
```

迁移后：`mdr.registry_specification` 行原样保留（表 28 数据无损），其余三行删除，FK 消失。

## 风险与对策

| 风险 | 对策 |
|---|---|
| Hibernate 历史 FK 名称不定 | 动态解析，不硬编码 |
| designation 子表被其他 FK 引用 | 依赖序先子后父；先在 SQL Server 备份库验证 |
| 前端契约破坏性变更 | 前后端同发布，dist→static 同步 + 重打 jar（10-08 部署事故教训） |
| 其余 Item 子类同类偏差 | 白名单测试锁定现状 + backlog 全量审计 |
