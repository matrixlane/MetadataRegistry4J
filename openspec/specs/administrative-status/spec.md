# administrative-status Specification

## Purpose
管理状态能力：为 `Registration_State.administrative_status`（ISO/IEC 11179-3:2023 表 22，0..1 String）建立应用层枚举 `AdministrativeStatus`，规定管理状态在新建/编辑/提交/审核/回退全生命周期中的推进规则，并提供 Recorded 及以上管理项的版本回退。管理状态是注册机构行政流程中的进行态指称，不改变注册状态本身。

## Requirements

### Requirement: AdministrativeStatus 枚举与字符串持久化映射

系统 SHALL 提供 `AdministrativeStatus` 枚举，取值收录 ISO/IEC 11179-6:2023 附录 D.3 Table D.1 示例词表并包含注册机构扩充值，共 16 个常量：`DRAFT`、`INITIAL`、`PROVISIONALLY_CANDIDATE`、`PROVISIONALLY_RECORDED`、`APPROVED_AS_RECORDED`、`PROVISIONALLY_QUALIFIED`、`APPROVED_AS_QUALIFIED`、`PROVISIONALLY_STANDARD`、`APPROVED_AS_STANDARD`、`PROVISIONALLY_PREFERRED_STANDARD`、`APPROVED_AS_PREFERRED_STANDARD`、`PROVISIONALLY_SUPERSEDED`、`APPROVED_AS_SUPERSEDED`、`PROVISIONALLY_RETIRED`、`WITHDRAWN`、`REJECTED`。系统 MUST NOT 包含 `APPROVED_AS_CANDIDATE` 取值（Candidate 准入无需审批，用户裁定剔除）。

持久化层 SHALL 保持 `registration_state.administrative_status` 列为 String 类型不变；内存中 SHALL 以枚举处理，经 JPA AttributeConverter 映射：写入落库标准原文小写串（如 `provisionally qualified`），读取 SHALL 大小写不敏感并容忍标准书写形式（如 `Provisionally Qualified`），未知值 MUST 抛出枚举解析错误（响亮失败）。该属性为 0..1，NULL SHALL 表示"无进行中的行政流程"。

#### Scenario: 写入与读取往返

- **WHEN** 系统将某管理项的管理状态置为 `PROVISIONALLY_QUALIFIED`
- **THEN** 数据库 `registration_state.administrative_status` 存为小写串 `provisionally qualified`，读回映射为枚举 `PROVISIONALLY_QUALIFIED`

#### Scenario: 读取标准 Title Case 书写形式

- **WHEN** 数据库该列存按标准书写的 `Provisionally Qualified`
- **THEN** 系统正常读取为 `PROVISIONALLY_QUALIFIED`，不抛解析异常

#### Scenario: 空值表示无进行中行政流程

- **WHEN** 某管理项当前无待办行政流程
- **THEN** 该列值为 NULL，应用层映射为"清空"状态

### Requirement: 新建与编辑保存置为 Draft

当用户新建管理项并保存，或更改既有管理项并保存时，系统 SHALL 将该管理项的管理状态置为 `DRAFT`（草稿），无论其注册状态处于何级。管理状态为 `REJECTED` 的管理项被用户修订并保存后，管理状态 SHALL 变为新的 `DRAFT`。本条规则 MUST NOT 改变管理项的注册状态。

#### Scenario: 新建保存后为草稿

- **WHEN** 用户创建一个新的管理项并保存
- **THEN** 该管理项管理状态为 `DRAFT`，注册状态为 `INCOMPLETE`

#### Scenario: 编辑已录管理项后为草稿

- **GIVEN** 某管理项注册状态为 `RECORDED`、管理状态为空
- **WHEN** 用户编辑并保存该管理项
- **THEN** 管理状态变为 `DRAFT`，注册状态保持 `RECORDED`

#### Scenario: 被拒绝管理项修订后为新草稿

- **GIVEN** 某管理项管理状态为 `REJECTED`
- **WHEN** 用户修订该管理项并保存
- **THEN** 管理状态变为新的 `DRAFT`

### Requirement: 提交清空管理状态且 Incomplete 落 Initial

用户在工作空间提交管理项（单个或批量，须显式设置 Submission_Record）后，系统 SHALL 清除该管理项的草稿状态：管理状态清空（NULL）。唯一例外：注册状态为 `INCOMPLETE` 的管理项提交后，管理状态 SHALL 落为 `INITIAL`（Table D.1 Incomplete 行的进入值）。提交 MUST NOT 改变注册状态。

#### Scenario: Recorded 草稿提交后清空

- **GIVEN** 某注册状态为 `RECORDED` 的管理项管理状态为 `DRAFT`
- **WHEN** 归属用户在工作空间提交该管理项
- **THEN** 管理状态清空（NULL），注册状态保持 `RECORDED`

#### Scenario: Incomplete 草稿提交后为 Initial

- **GIVEN** 某注册状态为 `INCOMPLETE` 的管理项管理状态为 `DRAFT`
- **WHEN** 归属用户在工作空间提交该管理项
- **THEN** 管理状态为 `INITIAL`，注册状态保持 `INCOMPLETE`

### Requirement: 申请合格与批准拒绝的推进规则

注册状态为 `RECORDED` 且主管记录联系人（StewardshipRecord.contact）为当前用户的管理项，主管人员可申请合格：系统 SHALL 置管理状态为 `PROVISIONALLY_QUALIFIED`，并发起既有 `RECORDED→QUALIFIED` 审核流。Registrar 批准（quorum 达成）后，注册状态 SHALL 变为 `QUALIFIED`，管理状态 SHALL 清空（NULL）。Registrar 拒绝后，注册状态 SHALL 保持 `RECORDED`，管理状态 SHALL 变为 `REJECTED`，该迁移的未生效批准记录 SHALL 被作废。

#### Scenario: 主管申请合格置为 Provisionally Qualified

- **GIVEN** 某管理项注册状态为 `RECORDED`、管理状态为 `APPROVED_AS_RECORDED` 或空，主管联系人为本用户
- **WHEN** 该用户为该管理项申请合格
- **THEN** 管理状态为 `PROVISIONALLY_QUALIFIED`，注册状态保持 `RECORDED`，`RECORDED→QUALIFIED` 审核流已发起

#### Scenario: 批准后注册状态提升且管理状态清空

- **GIVEN** 某管理项处于 `PROVISIONALLY_QUALIFIED`，`RECORDED→QUALIFIED` 审核流未生效批准数达到通过人数
- **WHEN**  Registrar 批准生效
- **THEN** 注册状态为 `QUALIFIED`，管理状态清空（NULL）

#### Scenario: 拒绝后管理状态为 Rejected

- **GIVEN** 某管理项处于 `PROVISIONALLY_QUALIFIED`
- **WHEN** 具备资格的 Registrar 驳回该申请
- **THEN** 管理状态为 `REJECTED`，注册状态保持 `RECORDED`，该迁移全部未生效批准被作废

### Requirement: 注册状态迁移后管理状态清空

除 `INCOMPLETE→CANDIDATE`（Request Candidate 见 workspace 能力）外，管理项的注册状态发生任何迁移后，系统 SHALL 将其管理状态清空（NULL）。管理状态变更只改写当前 `Registration_State` 行的 `administrative_status`，MUST NOT 新建注册状态行或改写 `previous_state` 链。

#### Scenario: 迁移生效后清空

- **GIVEN** 某管理项注册状态为 `QUALIFIED`、管理状态为 `DRAFT`
- **WHEN** 迁移 `QUALIFIED→STANDARD` 生效
- **THEN** 注册状态为 `STANDARD`，管理状态清空（NULL）

### Requirement: Recorded 及以上 Draft 与 Rejected 可回退上一提交版本

注册状态 ∈ {`CANDIDATE`, `RECORDED`, `QUALIFIED`, `STANDARD`, `PREFERRED_STANDARD`, `SUPERSEDED`, `RETIRED`} 且管理状态为 `DRAFT` 或 `REJECTED` 的管理项，归属用户可将其直接回退到上一个提交版本：系统 SHALL 恢复该管理项最近一条基线（`BASELINE`）快照的内容，丢弃草稿或被拒绝版本，写回 `ROLLED_BACK` 快照，并将管理状态清空（NULL）。回退 MUST NOT 改变注册状态。无基线快照时，若管理项为 `REJECTED` 且自进入拒绝以来内容无变更，系统 SHALL 仅将管理状态清空；其他无基线情形 MUST 拒绝并返回无版本可回退的错误。

回退窗口的注册状态范围由 `version-control` 能力的"回退窗口"节规定：依据 ISO/IEC 11179-6:2023 4.3.3.1.3 / 4.3.3.1.4 字面表述，`CANDIDATE` 起即受版本控制，故回退窗口按标准字面取 `CANDIDATE` 起（本条原先限定为 `RECORDED` 及以上，现与 11179-6 字面对齐）。

#### Scenario: 草稿回退恢复上一提交版本

- **GIVEN** 某注册状态为 `CANDIDATE` 的管理项管理状态为 `DRAFT`，且存在进入草稿前的基线快照
- **WHEN** 归属用户执行回退
- **THEN** 管理项内容恢复为基线快照内容，生成 `ROLLED_BACK` 快照，管理状态清空，注册状态保持 `CANDIDATE`

#### Scenario: 被拒绝版本回退并丢弃

- **GIVEN** 某注册状态 ∈ 回退窗口的管理项管理状态为 `REJECTED`，其被拒绝的内容相对基线有变更
- **WHEN** 归属用户执行回退
- **THEN** 被拒绝版本的内容被丢弃，管理项恢复为基线内容，管理状态清空

#### Scenario: 无基线且内容无变更的拒绝仅清状态

- **GIVEN** 某管理项管理状态为 `REJECTED`，自申请合格以来内容无变更（无基线快照）
- **WHEN** 归属用户执行回退
- **THEN** 管理状态清空，内容不变

#### Scenario: 无基线可回退被拒绝

- **GIVEN** 某管理项管理状态为 `DRAFT`，且不存在任何基线快照
- **WHEN** 归属用户执行回退
- **THEN** 系统拒绝并返回无版本可回退的错误

### Requirement: 版本快照为全量 DTO 并记录快照类别

系统 SHALL 将 `version_history` 的快照内容扩展为全量管理项 DTO（完整 Response 结构），JSON 外包 meta 记录快照类别（`BASELINE`、`DRAFT_EDIT`、`SUBMITTED`、`ROLLED_BACK`）、当时注册状态与管理状态。`BASELINE` SHALL 记录管理项从非草稿进入草稿的编辑前一刻完整状态，作为回退目标。本需求 MUST NOT 引入新表或 `version_history` 表结构变更。

#### Scenario: 进入草稿前记录基线

- **GIVEN** 某管理项管理状态为空
- **WHEN** 用户编辑该管理项（将进入草稿）
- **THEN** 系统在应用编辑前记录一条 `BASELINE` 全量快照，编辑完成后记录 `DRAFT_EDIT` 快照

#### Scenario: 快照含注册与管理状态上下文

- **WHEN** 系统写入任一新版本快照
- **THEN** 快照 meta 携带当时的注册状态与管理状态
