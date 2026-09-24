# registration-process Delta

## MODIFIED Requirements

### Requirement: 审批累积与生效模型

对需要审核的迁移，系统 SHALL 累积合格 Registrar 的批准记录：同一 Registrar 对同一 (item, from, to) 迁移 MUST 只计一次批准。当去重后的批准人数达到该迁移规则的通过人数（任一人或至少 N 人）时，系统 SHALL 在同一事务内完成状态迁移并清除该迁移的全部批准记录。Administered Item 的注册状态发生任何变化后，系统 SHALL 清除该项全部未生效的批准记录。系统 MUST NOT 提供发起审核操作。

对 `RECORDED→QUALIFIED` 申请（管理状态 `PROVISIONALLY_QUALIFIED`），系统 SHALL 提供驳回操作：具备批准资格的 Registrar 驳回后，注册状态保持不变，管理状态变为 `REJECTED`，该迁移的全部未生效批准记录 SHALL 被作废。

#### Scenario: 达到 quorum 自动生效

- **GIVEN** 迁移 `RECORDED→QUALIFIED` 的通过规则为至少 2 人，且已有 1 名 Registrar 批准
- **WHEN** 第 2 名不同的合格 Registrar 批准该迁移
- **THEN** 状态迁移在同一事务内生效，且该迁移的批准记录被清除

#### Scenario: 重复批准被拒绝

- **GIVEN** Registrar 甲已对某迁移投出批准
- **WHEN** 甲再次批准同一迁移
- **THEN** 系统拒绝重复批准或幂等忽略，批准人数不增加

#### Scenario: 状态变化清除陈旧批准

- **GIVEN** 某 Administered Item 的迁移 A→B 已有未生效批准
- **WHEN** 该项的注册状态经其他路径发生变化
- **THEN** 全部未生效批准记录被清除

#### Scenario: 驳回置管理状态为 Rejected 并作废批准

- **GIVEN** 某管理项处于 `PROVISIONALLY_QUALIFIED`（`RECORDED→QUALIFIED` 申请审核中）
- **WHEN** 具备批准资格的 Registrar 驳回该申请
- **THEN** 注册状态保持 `RECORDED`，管理状态变为 `REJECTED`，该迁移全部未生效批准被作废

### Requirement: 迁移执行与批准的权限

无需审核的迁移 SHALL 仅由具备写权限的用户（现行 ADMIN/REGISTRAR 角色矩阵，以及下述归属本用户的提交者白名单迁移）直接执行。需要审核的迁移的批准操作 SHALL 要求操作者持有该 Administered Item 所属注册机构的活动 Registrar 身份，且在该迁移规则的审核人集合内。Recorded 及以上目标状态的强制属性/关联校验 SHALL 在迁移生效时强制执行（11179-3:2023, 4.5）。

`INCOMPLETE→CANDIDATE` 迁移 SHALL 允许归属本用户的提交者直接执行，前置条件为：该管理项满足 Candidate 最低需求（具有主管记录），且当前用户属于该管理项的提交记录联系人或主管记录联系人。该白名单 MUST NOT 扩展到其他迁移。

#### Scenario: 非审核集合内用户批准被拒

- **GIVEN** 用户不持有该项所属注册机构的活动 Registrar 身份
- **WHEN** 该用户尝试批准一项需要审核的迁移
- **THEN** 系统拒绝并返回权限错误

#### Scenario: 生效时强制校验失败则回滚

- **GIVEN** 某迁移达到通过人数，但目标状态的强制校验（如 MeasureClass 须关联 UnitOfMeasure）不满足
- **WHEN** 系统尝试生效该迁移
- **THEN** 整个生效事务回滚，并返回校验错误，批准记录的处理方式与回滚一致

#### Scenario: 归属提交者执行请求成为候选

- **GIVEN** 某 `INCOMPLETE` 管理项具有主管记录，当前用户为提交者角色且属于该管理项提交记录联系人
- **WHEN** 该用户执行 `INCOMPLETE→CANDIDATE` 迁移
- **THEN** 迁移被执行，注册状态变为 `CANDIDATE`

#### Scenario: 非归属用户执行白名单迁移被拒

- **GIVEN** 当前用户不属于某 `INCOMPLETE` 管理项的提交记录联系人或主管记录联系人
- **WHEN** 该用户（即使是提交者角色）执行 `INCOMPLETE→CANDIDATE` 迁移
- **THEN** 系统拒绝并返回权限错误
