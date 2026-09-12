# enforce-valid-period Specification

## Purpose
TBD - created by archiving change enforce-valid-period. Update Purpose after archive.

## Requirements

### Requirement: 值域有效日期强制

系统 SHALL 在值域创建与更新保存前校验 `validPeriod.start` 必填：缺失时拒绝并返回参数校验错误，错误消息引用 ISO/IEC 11179-31:2023 表 5（valid_period 基数为 1）。`validPeriod.end` 保持可选（缺省表示仍有效）。

#### Scenario: 缺起始日期被拒绝

- **WHEN** 创建或更新值域时未提供有效日期起始时间
- **THEN** 系统返回参数校验错误，消息含标准条款引用，不产生任何持久化写入

#### Scenario: 仅起始日期可保存

- **WHEN** 值域提供起始日期而无结束日期
- **THEN** 保存成功，结束日期为空表示仍有效

### Requirement: 概念域有效日期强制

系统 SHALL 在概念域创建与更新保存前施加同等校验：valid_period 起始时间必填（ISO/IEC 11179-31:2023 表 2 与 §7.4.2.1.5.1），结束日期可选。

#### Scenario: 概念域缺起始日期被拒绝

- **WHEN** 创建或更新概念域时未提供有效日期起始时间
- **THEN** 系统返回参数校验错误，不产生任何持久化写入

### Requirement: 既有数据回填

系统 SHALL 提供一次性数据修复：全部既有的 `start_date_time` 为空的值域与概念域行被回填（优先取标识生命周期创建时间，缺失取基准日 2026-01-01 并注明为占位值）；回填 SQL 留档备查。回填后既有条目的编辑不因校验受阻。

#### Scenario: 回填后编辑既有值域

- **WHEN** 数据回填完成后编辑任一既有值域
- **THEN** 不因有效日期缺失被校验拒绝

### Requirement: 表单必填与导入兜底

值域与概念域编辑表单 SHALL 将有效期起始日期标记为必填（新建默认预填当日）；数据迁移导入路径在源数据缺有效日期时 SHALL 以导入执行时间补起始日期，保证导入可用。

#### Scenario: 新建值域表单

- **WHEN** Registrar 打开新建值域对话框
- **THEN** 有效期起始日期已预填当日且标记必填；清空后保存被前端或服务端拒绝

#### Scenario: 导入缺有效日期的值域

- **WHEN** 数据迁移导入的源数据中值域无有效日期
- **THEN** 导入以执行时间补其 start_date_time，导入流程不因校验失败
