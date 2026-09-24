# submission-record Specification

## Purpose

按 ISO/IEC 11179-3:2023 表 27 重设计 SubmissionRecord 生命周期：Submission_Record 表示**一次提交动作**
（可关联多个 Administered_Item），仅在工作空间提交（含单条 / 批量 / request-candidate）时创建；
编辑保存不再生成 SubmissionRecord，仅维护 StewardshipRecord；编辑界面提供 SubmissionRecord 只读列表
展示历史提交；历史脏数据按 SYSTEM_DRAFT 标注保留。

## Requirements

### Requirement: 工作空间提交按批次创建提交记录

工作空间提交（单条 / 批量 / request-candidate）SHALL 创建 SubmissionRecord 并按"提交动作"批次粒度
关联 AdministeredItem：单条提交创建 1 条 SR 关联 1 个 item；批量提交（N 个 item）整批创建 1 条 SR
仅关联成功的 K 个 item，失败的 item 不挂 SR；request-candidate 创建 1 条 SR 关联 1 个 item。
所有工作空间提交产生的 SR 标注 `source=USER_SUBMISSION`。编辑保存 MUST NOT 创建 SubmissionRecord；
编辑保存仅维护 StewardshipRecord（见 Requirement `主管记录在编辑表单可维护`）。

#### Scenario: 单条工作空间提交创建 1 条 SR

- **WHEN** 用户在工作空间点击单条提交并确认提交信息
- **THEN** 系统创建 1 条 SubmissionRecord（source=USER_SUBMISSION），`registeredItems` 关联该 item

#### Scenario: 批量工作空间提交创建 1 条 SR 关联全部成功 item

- **WHEN** 用户在工作空间批量选择 N 个 item 并提交，其中 K 个成功（N-K 失败）
- **THEN** 系统创建 1 条 SubmissionRecord（source=USER_SUBMISSION），`registeredItems` 仅关联 K 个成功 item

#### Scenario: request-candidate 创建 1 条 SR

- **WHEN** 用户在工作空间点击"请求成为候选"并确认提交信息
- **THEN** 系统创建 1 条 SubmissionRecord（source=USER_SUBMISSION），`registeredItems` 关联该 item

#### Scenario: 编辑保存不创建 SR

- **WHEN** 用户在 Item 编辑界面保存（create / update），不通过工作空间提交
- **THEN** 系统 MUST NOT 新增 SubmissionRecord；StewardshipRecord 按请求体 stewardship 块维护

### Requirement: 主管记录在编辑表单可维护

编辑 Item 时 SHALL 允许用户维护 StewardshipRecord（联系人 + 所属组织），保存时通过
`applyStewardshipRecord` 落库。NULL = 不变更既有主管记录；显式给出的联系人 MUST 属于当前账号
绑定的 Contact；organizationId 缺省时取该联系人所属组织。

#### Scenario: 编辑保存维护主管记录

- **WHEN** 编辑表单 stewardship.contactId / organizationId 非空
- **THEN** 保存后该 item 的 stewardshipRecord 指向新联系人 + 组织

#### Scenario: 编辑保存不传 stewardship 不变更

- **WHEN** 编辑表单 stewardship 缺省（NULL）
- **THEN** 既有 stewardshipRecord 保持不变

### Requirement: 编辑界面提交记录只读

Item 编辑表单 SHALL 提供 SubmissionRecord 只读列表（折叠面板），每行展示：来源（USER_SUBMISSION /
草稿自动生成）、联系人姓名、所属组织名、关联条目数。数据源为后端 `ItemMetaResponse.submissionRecordSummaries`
（按 id 降序）。列表为空时显示空态文案。

#### Scenario: 列表展示历史 SR

- **WHEN** 用户打开已多次提交的 Item 的编辑表单
- **THEN** 提交记录面板展示该 item 关联的全部 SubmissionRecord，按 id 降序排列

#### Scenario: 列表为空

- **WHEN** 用户打开新创建尚未提交过的 Item
- **THEN** 提交记录面板显示空态文案

### Requirement: 提交记录来源标注

SubmissionRecord 实体 SHALL 提供 `source` 字段（`SubmissionRecordSource` 枚举：`USER_SUBMISSION` /
`SYSTEM_DRAFT`）。新代码路径创建 SR 时一律写 `USER_SUBMISSION`；SYSTEM_DRAFT 实例不再产生，仅用于
历史脏数据标注保留。

#### Scenario: 历史 SR 迁移一次性回填

- **WHEN** 执行迁移 `V20260924002__Submission_record_source` 后
- **THEN** 旧 SubmissionRecord 行均有非空 `source=SYSTEM_DRAFT`；新 INSERT 一律 USER_SUBMISSION

#### Scenario: UI 区分来源

- **WHEN** 编辑表单展示历史 SR
- **THEN** USER_SUBMISSION 显示"用户提交"tag（success 色），SYSTEM_DRAFT 显示"草稿自动生成"tag（info 色）