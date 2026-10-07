# submission-record Delta Specification

## MODIFIED Requirements

### Requirement: 工作空间提交按批次创建提交记录

工作空间提交（单条 / 批量 / request-candidate）SHALL 创建 SubmissionRecord 并按"提交动作"批次粒度
关联 AdministeredItem：单条提交创建 1 条 SR 关联 1 个 item；批量提交（N 个 item）整批创建 1 条 SR
仅关联成功的 K 个 item，失败的 item 不挂 SR；request-candidate 创建 1 条 SR 关联 1 个 item。
编辑保存 MUST NOT 创建 SubmissionRecord；
编辑保存仅维护 StewardshipRecord（见 Requirement `主管记录在编辑表单可维护`）。SubmissionRecord
不再携带 `source` 字段（标准外扩展，已全栈移除）。

#### Scenario: 单条工作空间提交创建 1 条 SR

- **WHEN** 用户在工作空间点击单条提交并确认提交信息
- **THEN** 系统创建 1 条 SubmissionRecord，`registeredItems` 关联该 item

#### Scenario: 批量工作空间提交创建 1 条 SR 关联全部成功 item

- **WHEN** 用户在工作空间批量选择 N 个 item 并提交，其中 K 个成功（N-K 失败）
- **THEN** 系统创建 1 条 SubmissionRecord，`registeredItems` 仅关联 K 个成功 item

#### Scenario: request-candidate 创建 1 条 SR

- **WHEN** 用户在工作空间点击"请求成为候选"并确认提交信息
- **THEN** 系统创建 1 条 SubmissionRecord，`registeredItems` 关联该 item

#### Scenario: 编辑保存不创建 SR

- **WHEN** 用户在 Item 编辑界面保存（create / update），不通过工作空间提交
- **THEN** 系统 MUST NOT 新增 SubmissionRecord；StewardshipRecord 按请求体 stewardship 块维护

### Requirement: 编辑界面提交记录只读

Item 编辑表单 SHALL 提供 SubmissionRecord 只读列表（折叠面板），每行展示：联系人姓名、所属组织名、
关联条目数。数据源为后端 `ItemMetaResponse.submissionRecordSummaries`（按 id 降序）。列表为空时显示空态文案。

#### Scenario: 列表展示历史 SR

- **WHEN** 用户打开已多次提交的 Item 的编辑表单
- **THEN** 提交记录面板展示该 item 关联的全部 SubmissionRecord，按 id 降序排列

#### Scenario: 列表为空

- **WHEN** 用户打开新创建尚未提交过的 Item
- **THEN** 提交记录面板显示空态文案

## REMOVED Requirements

### Requirement: 提交记录来源标注

**Reason**: `source`（USER_SUBMISSION / SYSTEM_DRAFT）为 ISO/IEC 11179-3:2023 表 28 之外的扩展语义；2026-10-08 标准一致性清理决定全栈移除（迁移 `V20261009001` 删列 + 枚举/DTO/服务签名移除），Submission_Record 回归 organization/contact 标准口径。现存库该列全 NULL，无实际标注数据。
**Migration**: 三方言迁移 `V20261009001__Mdr_standard_compliance_cleanup` 删除 `submission_record.source` 列；代码侧删除 `SubmissionRecordSource` 枚举与全部引用。
