# 设计：强制值域/概念域有效日期（valid_period）

## Context

标准依据已核实（PDF 原文）：ISO/IEC 11179-31:2023 表 5（Value_Domain：datatype 1、valid_period 1、format 0..1、max char 0..1、uom 0..1；§7.4.2.8.5 valid_period 须含 start_datetime）、表 2（Conceptual_Domain：valid_period 1；§7.4.2.1.5.1 同约束）。上次已将 datatype 的必备校验落入 `ValueDomainAssociationValidator.validateCompleteness`（报 MetadataAssociationValidationException）。本次把 valid_period 以同模式补齐，并扩展到概念域。

## Decisions

### D1：应用层强制，不改数据库可空性

约束落点在完整性校验器（与 datatype 同一模式），**不**把 `start_date_time` 列改 NOT NULL：三种方言（SQLite/SQL Server/达梦）下 ddl-auto 对既有列的空性变更行为不一致，且应用层校验错误消息可引用标准条款、可测试。实体 `ValueDomain.validPeriod` 与 ConceptualDomain 对应字段补 javadoc 注明标准基数与校验落点。

- 值域：`ValueDomainAssociationValidator.validateCompleteness` 增加——`validPeriod == null || validPeriod.getStart() == null` → 抛 MetadataAssociationValidationException（消息：「值域必须填写有效日期（起始时间）（ISO/IEC 11179-31:2023 表 5 valid_period 基数为 1）」）。校验顺序：dataType 在先。
- 概念域：概念域的保存路径（ConceptualDomainService 或对应校验器）加同等校验（消息相应替换）。实施时先定位概念域的保存与校验入口（与值域同构则同模式；不同构则在 Service apply 方法内直接校验）。

### D2：数据回填策略

既有 16 值域 + 16 概念域：`UPDATE ... SET start_date_time = <回填值> WHERE start_date_time IS NULL`。回填值优先级：

1. 该条目标识生命周期的创建时间（ext.scoped_identifier_lifecycle.created_at，经 scoped_identifier → identified_item 关联；实施时验证关联可行）；
2. 缺失时固定为 `2026-01-01T00:00:00`，并在执行 SQL 头注释与变更记录中注明该基准日是占位值，注册机构可经编辑界面按需修正。

`end_date_time` 不动（保持 NULL = 仍有效，与标准释义一致）。SQL 存 `data/_backfill_valid_period.sql` 留档。回填属一次性运维操作，不写 Flyway（数据迁移而非结构变更，且需按环境核查）。

### D3：前端必填与默认

- 值域、概念域编辑表单（ItemFormDialog 属性区或对应区块）的有效期**起始日期**：必填标记（*），新建模式默认预填当日；编辑模式若为空显示空并依赖服务端拒绝（既有数据已回填，正常不会出现）。
- 提交载荷：`validPeriodStart` 由起始日期映射（既有字段路径不变）。

### D4：导入/迁移兜底

数据迁移导入路径创建值域/概念域时，若源数据无有效日期，以导入执行时间补 `start`（保证导入可用且满足约束）。实施时定位导入的值域/概念域创建点（迁移服务），在其映射处补默认值；找不到统一入口则逐点补齐并列明。

### D5：测试与回归锚点

- 校验器单测：缺 start 拒、仅 start 通过、start+end 通过；值域既有测试若构造了无 validPeriod 的请求需同步补值（同上次 datatype 模式）。
- 概念域同等。
- 既有全量回归不因校验新增而红（导入路径测试若涉值域创建需带 start 或走 D4 默认）。

## Risks / Trade-offs

- [强制破坏既有编辑流] → 已用 D2 回填消除；前端必填+默认降低摩擦。
- [回填基准日是占位值] → 在 SQL 与变更记录明示；语义上 start 含义由注册机构定义（标准允许），占位值可后改。
- [导入数据无有效日期] → D4 兜底，不阻塞迁移。
- [概念域保存路径与值域不同构] → 实施时先定位，偏离在报告中说明。

## Migration Plan

1. 服务端校验 + 单测（先红后绿）。
2. 数据回填 SQL 执行与核查（16+16 行）。
3. 前端必填/默认 + vitest。
4. 导入兜底 + 全量回归 + 部署验证。
回滚：校验为应用层增量可回退；回填数据不逆向（无害）。
