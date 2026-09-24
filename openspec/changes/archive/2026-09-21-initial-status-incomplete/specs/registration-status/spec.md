# registration-status Delta

## ADDED Requirements

### Requirement: 新建管理项初始注册状态为 Incomplete

依据 ISO/IEC 11179-6:2023 4.3.2 Table 1 与 4.3.3.1.2，`Incomplete` 是注册生命周期的入口态（最低元数据仅需标识符与提交者信息；不纳入版本控制；约束不强制），`Recorded` 要求全部强制元数据属性完成并经注册机构确认（4.3.3.1.4）。系统 SHALL 在创建 Administered Item（任何条目类型、任何创建路径，含联邦并入）时，将其初始注册状态设置为 `INCOMPLETE`，而非任何更高级别状态。存量条目不受影响，本需求不引入数据迁移。

#### Scenario: 新建数据元初始状态为 Incomplete

- **WHEN** 用户经创建表单新建一个数据元且请求未携带 `registrationStatus`
- **THEN** 该条目的注册状态为 `INCOMPLETE`，生效时间为创建时刻，无历史前态

#### Scenario: 联邦并入条目初始状态为 Incomplete

- **WHEN** 一条目经联邦并入流程在目的注册机构创建，且并入请求未携带注册状态
- **THEN** 该条目在目的方的注册状态为 `INCOMPLETE`

#### Scenario: 存量条目不受影响

- **GIVEN** 一个既有条目当前注册状态为 `RECORDED`
- **WHEN** 本变更部署完成
- **THEN** 该条目注册状态保持 `RECORDED` 不变

### Requirement: 创建时显式初始状态标定白名单

依据 ISO/IEC 11179-6:2023 4.3.2 Table 1，`Application` 与 `Historical` 为文档态入口标定；生命周期态 SHALL 自 `Incomplete` 起经推进流程逐级获得。创建请求显式携带 `registrationStatus` 时，系统 SHALL 仅接受 `INCOMPLETE`（幂等默认）、`APPLICATION`、`HISTORICAL`；对 `CANDIDATE`、`RECORDED`、`QUALIFIED`、`STANDARD`、`PREFERRED_STANDARD`、`SUPERSEDED`、`RETIRED` SHALL 拒绝并返回业务错误 `INVALID_INITIAL_STATUS`（1125），提示经推进流程获得生命周期态。既有（非新建）条目的状态变更守卫不变：表单携带与当前不同的状态仍拒绝（1124），相同则视为未变更。

#### Scenario: 显式标定 Application 允许

- **WHEN** 创建请求显式携带 `registrationStatus = APPLICATION`
- **THEN** 条目以 `APPLICATION` 初始状态创建成功

#### Scenario: 显式标定 Historical 允许

- **WHEN** 创建请求显式携带 `registrationStatus = HISTORICAL`
- **THEN** 条目以 `HISTORICAL` 初始状态创建成功

#### Scenario: 显式直建 Recorded 被拒绝

- **WHEN** 创建请求显式携带 `registrationStatus = RECORDED`
- **THEN** 创建被拒绝，返回业务错误 1125，提示须经推进流程（INCOMPLETE→CANDIDATE→RECORDED）获得

#### Scenario: 显式直建 Qualified 被拒绝

- **WHEN** 创建请求显式携带 `registrationStatus = QUALIFIED`
- **THEN** 创建被拒绝，返回业务错误 1125

#### Scenario: 显式直建 Candidate 被拒绝

- **WHEN** 创建请求显式携带 `registrationStatus = CANDIDATE`
- **THEN** 创建被拒绝，返回业务错误 1125

#### Scenario: 既有条目表单改状态仍被拒

- **GIVEN** 一个既有条目当前注册状态为 `RECORDED`
- **WHEN** 更新请求携带不同的 `registrationStatus`
- **THEN** 请求被拒绝（1124），状态保持不变
