# submission-record Specification

## Purpose

按 ISO/IEC 11179-3:2023 表 27 重设计 SubmissionRecord 生命周期：Submission_Record 表示**一次提交动作**
（可关联多个 Administered_Item），仅在工作空间提交（含单条 / 批量 / request-candidate）时创建；
编辑保存不再生成 SubmissionRecord，仅维护 StewardshipRecord；编辑界面提供 SubmissionRecord 只读列表
展示历史提交。（2026-10-08 标准一致性清理：source 扩展字段已全栈移除，见 mdr-standard-compliance。）

## Requirements

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

Item 编辑表单 SHALL 提供 SubmissionRecord 只读列表（折叠面板），每行展示：联系人姓名、所属组织名、
关联条目数。数据源为后端 `ItemMetaResponse.submissionRecordSummaries`（按 id 降序）。列表为空时显示空态文案。

#### Scenario: 列表展示历史 SR

- **WHEN** 用户打开已多次提交的 Item 的编辑表单
- **THEN** 提交记录面板展示该 item 关联的全部 SubmissionRecord，按 id 降序排列

#### Scenario: 列表为空

- **WHEN** 用户打开新创建尚未提交过的 Item
- **THEN** 提交记录面板显示空态文案

### Requirement: 提交组织可选与提交联系人属主校验
工作空间提交（含单条提交、批量提交与请求成为候选）的提交信息 SHALL 支持显式设置提交组织；提交组织的可选项 MUST 限定为当前账号拥有联系人身份的组织。提交联系人 MUST 为提交用户本人在所选组织下的联系人身份：`organizationId` 显式给出时，系统 MUST 从当前账号绑定的身份中解析该组织下的身份作为提交联系人，解析不到 MUST 返回业务校验错误并提示先创建为该机构联系人；`contactId` 与 `organizationId` 同时给出时，系统 MUST 校验该联系人确实隶属于所选组织，否则拒绝。仅提交 `contactId`（不传 `organizationId`）的兼容路径行为保持不变。前端提交入口 SHALL 在当前账号对当前所选机构无任何联系人身份时弹出提示，引导用户先到"我的"入口创建为该机构联系人，而不是直接打开提交表单。

#### Scenario: 选择组织后自动以本人身份提交

- **WHEN** 用户在提交对话框选择其拥有联系人身份的组织并确认提交
- **THEN** 创建的 SubmissionRecord 其 organization 为所选组织，contact 为当前账号在该组织下的联系人身份

#### Scenario: 无身份组织提交被后端拒绝

- **WHEN** 提交请求中的 organizationId 指向当前账号没有任何联系人身份的组织
- **THEN** 系统 SHALL 拒绝提交并返回业务校验错误，提示先在"我的"入口创建为该机构联系人

#### Scenario: 联系人不属于所选组织被拒绝

- **WHEN** 提交请求同时给出 contactId 与 organizationId，但该联系人的所属组织不是所选组织
- **THEN** 系统 SHALL 拒绝提交并返回业务校验错误

#### Scenario: 非所选机构联系人点击提交入口收到提示

- **WHEN** 当前账号对当前所选机构没有任何联系人身份，用户点击工作空间的提交（或请求成为候选）入口
- **THEN** 前端 SHALL 弹出提示，说明提交元数据前需先创建为该注册机构的联系人，并指引到"我的"入口，不打开提交表单
