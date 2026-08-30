## MODIFIED Requirements

### Requirement: 关联 picker 默认显示业务标识符而非 UUID

任何跨 Item 关联的 picker（数据元 / 数据元概念 / 概念域 / 值域 4 个表单中所有 Item-to-Item 关联字段） MUST NOT 在候选项 trigger 位置展示 UUID。候选项 MUST 优先展示业务标识符（即 `scoped_identifier.identifier` 或同义字段，形如 `VD.00001`、`CD.00012`、`MD.DE.00001`、`NS.VD.00001`），主标识符缺失时退化为 item 的 preferredDesignation，UUID 仅作为内部 value 字段存在。

#### Scenario: 候选项以分隔符展示完整信息

- **WHEN** `/metadata/search` 返回一条 `identifier="VD.00001"`、`itemLabel="身高"`、`version="1.0.0"` 的 ValueDomain
- **THEN** picker 下拉项与 trigger 位置的标签 MUST 为 `VD.00001 | (v1.0.0) | 身高 | <最近时间>`，各信息段以 ` | ` 分隔
- **AND** 各段缺失时跳过该段（不保留空分隔符）

#### Scenario: 时间取创建与最后修改中较晚者

- **WHEN** 候选项的 `lastChangeDateTime` 晚于 `creationDateTime`
- **THEN** 时间段 MUST 显示为 `最后修改于：YYYY-MM-DD HH:mm`
- **WHEN** `creationDateTime` 晚于或不晚于 `lastChangeDateTime`（或后者缺失）
- **THEN** 时间段 MUST 显示为 `创建于：YYYY-MM-DD HH:mm`
- **AND** 两者都缺失时 MUST NOT 显示时间段

#### Scenario: 标识符缺失时降级到名称

- **WHEN** 后端返回一条无 `identifier` 但有 `itemLabel="Unnamed"` 的行
- **THEN** picker trigger MUST 展示 `Unnamed`（label），MUST NOT 展示 UUID

#### Scenario: 标识符与名称都缺失

- **WHEN** 后端返回一条 `identifier=null` 且 `itemLabel=null` 的行
- **THEN** picker MUST 显示一个本地化 "（无标题）"占位，MUST NOT 展示 UUID

## ADDED Requirements

### Requirement: 多选模式

picker SHALL 支持 `allow-multiple` 多选模式：开启后 trigger 以可折叠标签展示全部已选项，`modelValue` 为字符串数组；该模式供一对多关联的批量添加场景使用。

#### Scenario: 多选添加

- **WHEN** picker 以 `allow-multiple=true` 使用，用户依次选择多个候选项
- **THEN** 已选项以 collapse-tags 形式全部保留在 trigger 中
- **AND** `update:modelValue` 发出的值为已选项 ID 数组

#### Scenario: 多选模式下的空值

- **WHEN** `allow-multiple=true` 且 `modelValue` 为 `null`
- **THEN** picker 内部值 MUST 规范化为空数组（而非 `null`）
