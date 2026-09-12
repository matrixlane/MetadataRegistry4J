# ai-generate-everywhere Specification

## Purpose
TBD - created by archiving change ai-generate-everywhere. Update Purpose after archive.

## Requirements

### Requirement: 字段级 AI 生成全模式覆盖

凡提供指称或定义编辑能力的界面，其编辑行 MUST 提供字段级「AI 生成」入口，**新建与编辑模式均可用**（仍仅 Registrar）。共用 ItemFormDialog 的注册对象（数据元、值域、数据元概念、值含义、数据类型、数据类型方案、计量单位等）SHALL 经同一机制自动获得该能力；后续新增编辑界面 MUST 默认遵循（偏离须在对应变更中说明）。

#### Scenario: 新建数据元时生成定义

- **WHEN** Registrar 在新建数据元对话框的定义编辑行点击「AI 生成」
- **THEN** 系统以当前表单已填内容为上下文发起生成，候选展示与回填行为与编辑模式一致

#### Scenario: 值域编辑页可用

- **WHEN** Registrar 打开值域的新建或编辑对话框
- **THEN** 指称/定义编辑行同样呈现「AI 生成」按钮，交互语义（点击生成、悬停回看、进行中恢复、防重入、行级缓存）与数据元一致

#### Scenario: 非 Registrar 无入口

- **WHEN** 非 Registrar 用户打开任一新建/编辑对话框
- **THEN** 不呈现「AI 生成」按钮

### Requirement: 草稿驱动的 complete-metadata

complete-metadata 任务的 itemId SHALL 改为可选：无 itemId 时 MUST 接受 `params.draft`（条目类型 + 已填指称/定义行），以其组装「已知元数据」提示字段段；写作规则、推断任务段与 JSON 输出契约与库内模式一致。`itemId` 与 `draft` 同给时 MUST 以 itemId 为准；两者皆缺或 draft 无有效文本行 MUST 返回参数校验错误。单值文本 MUST 按既有上限截断。

#### Scenario: 草稿模式提示词

- **WHEN** 以 draft={itemType: DataElement, designations:[{language: zh-CN, value: 接收标本日期时间}]} 创建生成作业
- **THEN** 提示词的已知元数据段包含该条目类型与指称行，输出契约与库内模式相同

#### Scenario: 空草稿被拒绝

- **WHEN** 无 itemId 且 draft 不存在或全部文本行为空
- **THEN** 系统返回参数校验错误，不创建作业

#### Scenario: itemId 优先

- **WHEN** 请求同时携带 itemId 与 draft
- **THEN** 系统以库内条目组装提示词，忽略 draft

### Requirement: 普及原则入架构文档

架构文档 `openspec/specs/system.md` SHALL 记录 AI 生成普及原则：凡可编辑指称/定义的界面 MUST 提供字段级 AI 生成入口（含新建模式），统一复用既定机制，配置前置到系统设置。

#### Scenario: 架构文档可查

- **WHEN** 读者查阅 openspec/specs/system.md 的 AI 辅助章节
- **THEN** 可见 AI 生成普及原则条文，与上述需求一致
