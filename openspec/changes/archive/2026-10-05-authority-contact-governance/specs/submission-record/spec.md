# Submission Record Delta Specification

## ADDED Requirements

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
