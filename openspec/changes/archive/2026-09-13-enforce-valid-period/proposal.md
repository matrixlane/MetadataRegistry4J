# 提案：强制值域/概念域有效日期（valid_period）

## Why

ISO/IEC 11179-31:2023 表 5（Value_Domain）与表 2（Conceptual_Domain）中 `valid_period` 基数均为 **1（必备）**，且 §7.4.2.8.5 / §7.4.2.1.5.1 约束要求必须含 `start_datetime`。当前实现（实体仅 `@Embedded`、完整性校验缺失）允许持久化无有效日期的值域/概念域——与标准不符，且上次值域数据类型事故证明「映射与校验口径不一致」会产生不可加载的畸形数据。现有 16 个值域与 16 个概念域全部未填有效日期，需一次性补齐。

## What Changes

- **服务端强制**：值域、概念域在创建/更新保存前校验 `validPeriod.start` 必填（缺失即 400，错误消息引用标准条款）；结束日期保持可选（缺省表示仍有效，同标准释义）。
- **数据修复**：既有 16 行值域 + 16 行概念域回填 `start_date_time`（优先取标识生命周期创建时间，缺失取固定基准日 2026-01-01 并在迁移说明中注明）；SQL 留档于 data/。
- **前端**：值域与概念域编辑表单的有效期起始日期设为必填（新建默认预填当日）；保存被拒时展示服务端错误。
- **导入/迁移路径**：数据导入创建值域/概念域时若缺有效日期，以导入时间补 start（保持可导入）。
- **标准符合性记录**：在未归档变更与架构文档中登记该约束已强制。

## Capabilities

### New Capabilities

- `enforce-valid-period`: 值域/概念域 valid_period 必备约束的服务端校验、数据回填、表单必填与导入兜底。

### Modified Capabilities

（无已归档 spec 被修改；ISO 符合性注记写入未归档变更与 openspec/specs/system.md 或相关设计文档。）

## Impact

- 后端：`ValueDomainAssociationValidator`（扩展）、概念域对应校验点、值域/概念域 Service 测试；数据回填 SQL（mdr.value_domain / mdr.conceptual_domain）。
- 前端：ItemFormDialog 有效期字段必填与默认值（值域、概念域对话框共用处）。
- 数据：一次性 UPDATE（16+16 行）；无 schema 变更（列保持可空，约束在应用层强制，理由见 design）。
- 导入：数据迁移导入路径的默认值逻辑。
