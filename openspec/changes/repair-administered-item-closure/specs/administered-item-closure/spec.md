# administered-item-closure Specification

## Purpose

受管项闭环能力：依据 ISO/IEC 11179-3:2023 §9.4.1.2（"Registered_Item shall be
instantiated as either an Administered_Item or an Attached_Item but not both"）与
§7.4.1 item_identification 关联，规定已标识条目 SHALL 完成受管链闭环；规定裸 SQL
数据导入路径 MUST 自建完整关联链（`administered_item` 与 `registration_state` 全库仅由
应用服务创建，裸 SQL 天然产不出）；规定以守护测试将闭环不变式纳入构建，并规定验收
MUST 含语义不变式断言而非仅比对行数/字节。

2026-10-09 事故：WS 363/364 基准还原脚本为裸 SQL，未自建受管链，导致 11,177 个已标识
条目既非 Administered_Item 亦非 Attached_Item，注册状态恒为空、操作落入死角，且因
验收只比对行数与字节而长期未被发现。

## ADDED Requirements

### Requirement: 已标识条目 SHALL 完成受管链闭环

依据 ISO/IEC 11179-3:2023 §9.4.1.2，凡已建立 `Identified_Item` 实例的条目（等价于
拥有 `Scoped_Identifier`，§7.4.2 要求每个 Identified_Item 至少关联一个
Scoped_Identifier），SHALL 沿 §9.4.1 的继承链落到 `Administered_Item` 或
`Attached_Item`，不得停在 `Identified_Item` 中间层。系统 SHALL 保证不存在「有
`identified_item` 而无 `registered_item`」的条目。

> §7.4.1 的 NOTE 允许条目不带标识符进入注册库（`item_identification` 关联为零或一），
> 故本要求仅约束**已标识**条目；未标识条目不受此约束。

#### Scenario: 闭环完整性与基线脚本一致

- **WHEN** 对已执行基准还原的数据库反连接检查
- **THEN** 不存在「有 `identified_item` 而无对应 `registered_item`」的行，违反集合为空

#### Scenario: 无标识符的条目不受闭环约束

- **GIVEN** 某条目没有 `identified_item` 实例（如系统内部构造的条目）
- **WHEN** 执行闭环不变式检查
- **THEN** 该条目不计入违反集合，不导致检查失败

#### Scenario: 已标识条目可在注册项列表中出现

- **GIVEN** 某条目拥有 `Scoped_Identifier`
- **WHEN** 注册项列表按状态过滤查询
- **THEN** 该条目因具备 `Administered_Item` 实例而可被列出，不再因缺少受管项而不可见

### Requirement: 条目注册状态经完整受管链可读

已标识条目的注册状态 SHALL 沿 `item_identification → Identified_Item → Registered_Item →
Administered_Item → registration association → Registration_State` 链可读。系统 SHALL
保证链路中任一环节缺失时返回可辨识的"未建立受管链"语义，而非静默的空值。

#### Scenario: 受管链完整时注册状态可读

- **GIVEN** 某条目沿受管链可到达 `Administered_Item`，且其关联的 `Registration_State`
  记录了注册状态
- **WHEN** 查询该条目的注册元数据
- **THEN** 返回该 `Registration_State` 的注册状态，而非空值

#### Scenario: 基准导入条目的注册状态与生效日期

- **WHEN** WS 363/364 基准数据经还原脚本导入
- **THEN** 每个条目可读到注册状态 `PREFERRED_STANDARD`，且生效日期为对应 WS/T
  363/364-2023 各部分标准首页声明的实施日期

### Requirement: 裸 SQL 数据导入 MUST 自建完整关联链

凡以裸 SQL（含基准还原脚本、批量导入脚本、数据库层恢复）向注册库写入条目数据的路径，
MUST 写入完整关联链——除 `item` 及其内容类实例、`identified_item`、`scoped_identifier` 外，
SHALL 同时建立 `registered_item`、`administered_item` 与 `registration_state` 实例。

`administered_item` 与 `registration_state` 在应用中仅由条目管理的建链服务创建；裸 SQL
路径绕过该服务，天然产不出这两张表。

#### Scenario: 基准还原脚本产出闭环完整的数据

- **WHEN** 在空白 schema 上执行基准还原脚本，随后执行闭环不变式检查
- **THEN** 检查通过，无「有标识无受管项」的条目

#### Scenario: 脚本幂等可重跑

- **WHEN** 对已执行过还原脚本的数据库重复执行同一脚本
- **THEN** 不产生重复主键，不改变既有受管链实例的状态与生效日期

#### Scenario: 脚本不硬编码条目数量

- **GIVEN** 目标库的 `identified_item` 行数与生成源不同
- **WHEN** 执行补壳脚本
- **THEN** 脚本以集合方式对「有 `identified_item` 而无 `registered_item`」的条目补壳，
  结果覆盖全部此类条目，不依赖任何硬编码行数

### Requirement: 基准数据生成器 MUST 纳入受管表

用于生成基准还原脚本的导出工具，其表清单 MUST 包含 `registered_item`、
`administered_item` 与 `registration_state`，并 SHALL 生成对应的补壳语句。生成源缺失
期间，导出工具的表清单修正 SHALL 先于补壳脚本落地，避免恢复生成源后重新导出仍产出
缺环数据。

#### Scenario: 生成器表清单含受管表

- **WHEN** 检查基准生成工具的表清单常量
- **THEN** 清单包含 `registered_item`、`administered_item`、`registration_state`

### Requirement: 闭环不变式 SHALL 有守护测试

系统 SHALL 提供构建期守护测试，断言「每个 `identified_item` 都可沿受管链到达
`Administered_Item` 或 `Attached_Item`」。该测试 SHALL 与实体继承符合性守护测试分离
（后者守继承边，本项守跨表正交链不变式，语义不同）。

守护测试 SHALL 能在基准数据补壳前失败、补壳后通过，以构成缺陷已被复现并修复的证据。

#### Scenario: 补壳前守护测试失败

- **GIVEN** 一个已执行裸 SQL 基准还原、尚未补壳的数据库
- **WHEN** 运行闭环守护测试
- **THEN** 测试失败并列出违反条目，证明缺陷可被检测

#### Scenario: 补壳后守护测试通过

- **WHEN** 在同一数据库上执行补壳脚本后重跑守护测试
- **THEN** 测试通过

#### Scenario: 未登记的违反条目被逐条报出

- **GIVEN** 某数据库中人为构造一条「有 `identified_item` 而无 `registered_item`」的记录
- **WHEN** 运行闭环守护测试
- **THEN** 测试失败并指出该条目的标识符，而非仅给出聚合计数

### Requirement: 补壳条目不伪造归属与状态历史

为外部导入条目补建的受管实例 SHALL NOT 伪造注册机构归属（`registration_authority_id`
留空，由应用按活动注册人员身份推导）；SHALL NOT 伪造 `previous_state` 状态历史链
（外部导入条目并非经本注册库逐级推进而来）。注册状态取标准认定的值，生效日期取原
标准文档声明的实施日期。

#### Scenario: 补壳条目注册机构留空

- **WHEN** 检查补壳后条目的受管实例
- **THEN** `registration_authority_id` 为空，界面上的注册机构信息仍由命名空间呈现

#### Scenario: 补壳条目无历史状态链

- **WHEN** 检查补壳后条目的 `registration_state`
- **THEN** `previous_state_id` 为空，仅有一行表示"以该状态进入注册库"的记录