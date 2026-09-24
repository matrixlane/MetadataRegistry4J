# submission-record Specification

## Purpose

本能力规定 SubmissionRecord 随 Administered_Item 保存而生成的行为：新建条目与每次提交新版条目内容各生成一条 SubmissionRecord，其联系人与组织默认预填提交者的默认联系人及其所属组织，提交者可修改，且提交身份必须是提交者本人绑定的 Contact。

## ADDED Requirements

### Requirement: 随条目保存生成提交记录

系统在保存 Administered_Item（新建，以及每次提交一版新的内容修订）时，SHALL 在同一事务内生成一条新的 SubmissionRecord 并关联该条目。系统 MUST NOT 复用或改写历史 SubmissionRecord。

#### Scenario: 新建条目生成提交记录

- **WHEN** 用户成功创建一条 Administered_Item
- **THEN** 系统同时生成一条关联该条目的 SubmissionRecord

#### Scenario: 内容修订生成新提交记录

- **WHEN** 用户提交一版新的条目内容（更新）
- **THEN** 系统生成一条新的 SubmissionRecord，历史 SubmissionRecord 保持不变

### Requirement: 提交信息默认预填且可修改

条目创建/编辑界面 SHALL 提供提交信息（联系人、所属组织），其默认值 SHALL 为提交者的默认联系人及其所属组织；提交者可修改。请求未显式提供提交信息时，服务端 SHALL 按同一规则预填。界面与预填值 MUST NOT 泄露或允许选择他人绑定的 Contact。

#### Scenario: 默认预填

- **WHEN** 拥有默认联系人的用户打开条目创建表单
- **THEN** 提交信息的联系人与组织分别预填为默认联系人及其所属组织，用户可修改

#### Scenario: 服务端兜底预填

- **WHEN** 创建/更新请求未携带提交信息
- **THEN** 服务端以提交者默认联系人及其所属组织预填 SubmissionRecord；无默认联系人时回退到其绑定列表中的第一条身份

### Requirement: 提交身份归属校验

SubmissionRecord 中的联系人 MUST 是提交者本人账号绑定的 Contact；请求中显式给出的联系人不属于提交者本人时，系统 MUST 拒绝该请求。所属组织缺省时 SHALL 取该联系人所属组织。

#### Scenario: 代填他人联系人被拒绝

- **WHEN** 请求中提交的 contactId 不是当前账号绑定的任何 Contact
- **THEN** 系统拒绝该请求并返回校验错误

#### Scenario: 组织缺省推导

- **WHEN** 请求仅提供 contactId 而未提供 organizationId
- **THEN** 系统将该联系人所属组织写入 SubmissionRecord
