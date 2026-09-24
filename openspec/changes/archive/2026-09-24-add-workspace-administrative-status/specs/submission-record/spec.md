# submission-record Delta

## ADDED Requirements

### Requirement: 工作空间提交须显式设置提交记录

工作空间中的提交操作（单个或批量，含"请求成为候选"）SHALL 弹出提交信息选择器，要求用户显式确认或修改 Submission_Record 后再执行：联系人与组织默认预填当前用户默认联系人及其所属组织，用户可修改为本账号绑定的其他联系人及其组织。提交成功后，每条管理项 SHALL 生成一条新的 SubmissionRecord（沿用"随条目保存生成提交记录"的语义，不得复用或改写历史记录）。

#### Scenario: 提交弹窗默认预填可修改

- **WHEN** 用户在工作空间点击提交（单个或批量）
- **THEN** 弹出提交信息选择器，联系人与组织预填为默认联系人及其组织，用户可修改

#### Scenario: 提交生成新提交记录

- **WHEN** 用户确认提交信息并提交
- **THEN** 每个管理项生成一条以其所选联系人与组织的新 SubmissionRecord
