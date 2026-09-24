# submission-record Specification (delta)

> 本文件是 `align-submission-record-to-iso` 提案的 spec delta，归档时合并入
> `openspec/specs/submission-record/spec.md`。

## Purpose

按 ISO/IEC 11179-3:2023 表 27 重设计 SubmissionRecord 生命周期：Submission_Record 表示**一次提交动作**（可关联
多个 Administered_Item），仅在工作空间提交（含单条、批量、request-candidate）时创建；编辑保存不再生成
SubmissionRecord；编辑界面提供只读 SubmissionRecord 列表展示历史提交；历史脏数据按 SYSTEM_DRAFT 标注保留。

## MODIFIED Requirements

### Requirement: 工作空间提交按批次创建提交记录

将原 spec.md 的 Requirement `随条目保存生成提交记录` 替换为本 Requirement：

工作空间提交（单条或批量，含 `request-candidate`）SHALL 创建 SubmissionRecord 并按"提交动作"批次粒度
关联 AdministeredItem。编辑保存（create / update）MUST NOT 创建 SubmissionRecord；编辑保存仅维护
Stewardship_Record（已通过 `applyStewardshipRecord` 实现，本变更不动该路径）。

#### Scenario: 单条工作空间提交创建 1 条 SR 关联该 item

- **WHEN** 用户在工作空间点击单条提交并确认提交信息
- **THEN** 系统创建 1 条 SubmissionRecord（source=USER_SUBMISSION），`registeredItems` 关联该 item

#### Scenario: 批量工作空间提交创建 1 条 SR 关联全部成功 item

- **WHEN** 用户在工作空间批量选择 N 个 item 并提交，其中 K 个成功（N-K 失败）
- **THEN** 系统创建 1 条 SubmissionRecord（source=USER_SUBMISSION），`registeredItems` 仅关联 K 个成功 item；
  失败 item 不挂该 SR

#### Scenario: request-candidate 创建 1 条 SR 关联该 item

- **WHEN** 用户在工作空间点击"请求成为候选"并确认提交信息
- **THEN** 系统创建 1 条 SubmissionRecord（source=USER_SUBMISSION），`registeredItems` 关联该 item

#### Scenario: 编辑保存不创建 SR

- **WHEN** 用户在 Item 编辑界面保存（create 或 update），不通过工作空间提交
- **THEN** 系统 MUST NOT 新增 SubmissionRecord；StewardshipRecord 按请求体 stewardship 块维护

## REMOVED Requirements

### Requirement: 随条目保存生成提交记录（原 spec.md Requirement 1）

删除原 spec.md Requirement 1（"系统在保存 Administered_Item 时 SHALL 在同一事务内生成一条新的
SubmissionRecord 并关联该条目；系统 MUST NOT 复用或改写历史 SubmissionRecord"）及其所有 Scenario
（新建条目生成提交记录、内容修订生成新提交记录）。由 MODIFIED Requirement 取代。

## ADDED Requirements

### Requirement: 主管记录在编辑表单可维护

编辑 Item 时 SHALL 允许用户维护 Stewardship_Record（联系人 + 所属组织），保存时通过 `applyStewardshipRecord`
落库。NULL = 不变更既有主管记录；显式给出的联系人 MUST 属于当前账号绑定的 Contact；organizationId 缺省时
取该联系人所属组织。

#### Scenario: 编辑保存维护主管记录

- **WHEN** 编辑表单 stewardship.contactId / organizationId 非空
- **THEN** 保存后该 item 的 stewardshipRecord 指向新联系人 + 组织

#### Scenario: 编辑保存不传 stewardship 不变更

- **WHEN** 编辑表单 stewardship 缺省（NULL）
- **THEN** 既有 stewardshipRecord 保持不变

### Requirement: 编辑界面提交记录只读

Item 编辑表单 SHALL 提供 SubmissionRecord 只读列表（折叠面板），每行展示：来源（USER_SUBMISSION / 草稿自动生成）、
联系人姓名、所属组织名、关联条目数、提交时间（按 id 降序近似）。列表为空时显示空态文案。

#### Scenario: 列表展示历史 SR

- **WHEN** 用户打开已多次提交的 Item 的编辑表单
- **THEN** 提交记录面板展示该 item 关联的全部 SubmissionRecord，按 id 降序排列

#### Scenario: 列表为空

- **WHEN** 用户打开新创建尚未提交过的 Item
- **THEN** 提交记录面板显示空态文案"暂无提交记录"

### Requirement: 历史脏数据标注 SYSTEM_DRAFT

迁移 `V20260924002__Submission_record_source.sql` SHALL 在 `submission_record` 表加 `source varchar(32) null` 列。
回填规则：若现有 SR 在 `submission_record_registered_item` 中是该 SR 唯一关联 Item 且创建时间早于本次重构锚点
（2026-09-24 16:00），标记为 `SYSTEM_DRAFT`（即历史脏数据）；其余标记为 `USER_SUBMISSION`。新 INSERT 一律写
`USER_SUBMISSION`。

#### Scenario: 旧 SR 行回填

- **WHEN** 执行迁移 `V20260924002` 后
- **THEN** 旧 SubmissionRecord 行均有非空 `source`；后续 `SubmissionRecord` 实例创建时 source=USER_SUBMISSION

#### Scenario: UI 区分来源

- **WHEN** 编辑表单展示历史 SR
- **THEN** USER_SUBMISSION 标记"用户提交"，SYSTEM_DRAFT 标记"草稿自动生成"