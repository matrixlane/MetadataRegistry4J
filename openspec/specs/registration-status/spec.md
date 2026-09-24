# registration-status Specification

## Purpose

注册状态能力：注册状态枚举与 ISO/IEC 11179-3:2023 表 30（9.6.2）及 ISO/IEC 11179-6:2023 表 1（4.3.2）严格对齐，持久化读取兼容标准书写形式（大小写不敏感、"Preferred Standard" 含空格写法），并规定新建管理项的初始注册状态统一从 Incomplete 开始（11179-6:2023 4.3.3.1.2/1.4 生命周期入口态），创建时显式初始状态仅限白名单标定（INCOMPLETE/APPLICATION/HISTORICAL，生命周期态拒绝）。

## Requirements

### Requirement: RegistrationStatus covers ISO/IEC 11179-3:2023 states
The system SHALL provide a `RegistrationStatus` enumeration whose values correspond exactly to the statuses defined in ISO/IEC 11179-3:2023, Table 30 (9.6.2) and ISO/IEC 11179-6:2023, Table 1 (4.3.2): `INCOMPLETE`, `CANDIDATE`, `RECORDED`, `QUALIFIED`, `STANDARD`, `PREFERRED_STANDARD`, `SUPERSEDED`, `RETIRED`, `HISTORICAL`, and `APPLICATION`. The Java enum constant `PREFERRED_STANDARD` corresponds to the standard status name "Preferred Standard"; `CANDIDATE` corresponds to "Candidate". The status `SUBMITTED` does not exist in the standard and SHALL NOT appear.

The persistence layer SHALL read string-persisted registration status case-insensitively and tolerate the standard's written form (including the space in "Preferred Standard"), mapping it to the corresponding Java enum constant, so that data imported in standard written form does not raise an enum resolution error.

#### Scenario: Candidate state is selectable
- **WHEN** an administrator creates or updates a `Registration_State`
- **THEN** the system accepts `CANDIDATE` as a valid registration status

#### Scenario: Preferred Standard persisted in standard written form is readable
- **WHEN** the database column `registration_state.registration_status` contains the standard written value `Preferred Standard`
- **THEN** the system reads it back as the enum constant `PREFERRED_STANDARD` without raising `No enum constant`

#### Scenario: Submitted is not a valid status
- **WHEN** any code path attempts to resolve `SUBMITTED` as a `RegistrationStatus`
- **THEN** the resolution fails, because `SUBMITTED` is not defined in the standard

#### Scenario: State transition does not reject Candidate
- **GIVEN** an administered item is in `INCOMPLETE` status
- **WHEN** the administrator transitions it to `CANDIDATE`
- **THEN** the transition is persisted without validation errors

### Requirement: 字符串枚举的持久化读取兼容标准大小写形式
ISO/IEC 11179 各部分对枚举取值的书写大小写约定不一致（如 11179-6:2023 的注册状态为首字母大写 `Incomplete`/`Recorded`/`Standard`/…，而 11179-3:2023 表 20 的可接受性为小写）。系统 MUST 保证所有以字符串形式持久化的枚举字段在读取时大小写不敏感：数据库中按标准格式（首字母大写或小写）导入的历史数据 SHALL 正常映射为对应 Java 枚举常量，MUST NOT 抛出枚举解析异常。写入路径 MAY 继续落库 Java 枚举常量名（大写），不改变既有存储格式；持久化层 MUST NOT 通过枚举 CHECK 约束把取值限定为大写常量名，以免阻塞按标准格式的直接 SQL 导入。

#### Scenario: 读取首字母大写的标准格式注册状态
- **WHEN** 数据库中 `registration_state.registration_status` 为按 11179-6:2023 书写的 `Standard`（历史导入数据）
- **THEN** 系统 SHALL 正常读取并映射为枚举 `STANDARD`，不抛出 `No enum constant` 异常

#### Scenario: 读取大写枚举名保持兼容
- **WHEN** 数据库中字符串枚举列存储为应用自身写入的大写枚举名（如 `RECORDED`）
- **THEN** 系统 SHALL 正常读取并映射为对应枚举常量

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
