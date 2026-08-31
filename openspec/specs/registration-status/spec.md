# registration-status Specification

## Purpose
TBD - created by archiving change iso-11179-3-chapter-9-registration-package. Update Purpose after archive.
## Requirements
### Requirement: RegistrationStatus covers ISO/IEC 11179-3:2023 states
The system SHALL provide a `RegistrationStatus` enumeration that includes all statuses defined in ISO/IEC 11179-3:2023, 9.6.2: `INCOMPLETE`, `SUBMITTED`, `RECORDED`, `QUALIFIED`, `STANDARD`, `PREFERRED`, `SUPERSEDED`, `RETIRED`, `HISTORICAL`, and `APPLICATION`.

#### Scenario: Application state is selectable
- **WHEN** an administrator creates or updates a `Registration_State`
- **THEN** the system accepts `APPLICATION` as a valid registration status

#### Scenario: State transition does not reject Application
- **GIVEN** an administered item is in a non-terminal status
- **WHEN** the administrator transitions it to `APPLICATION`
- **THEN** the transition is persisted without validation errors

### Requirement: 字符串枚举的持久化读取兼容标准大小写形式
ISO/IEC 11179 各部分对枚举取值的书写大小写约定不一致（如 11179-6:2023 的注册状态为首字母大写 `Incomplete`/`Recorded`/`Standard`/…，而 11179-3:2023 表 20 的可接受性为小写）。系统 MUST 保证所有以字符串形式持久化的枚举字段在读取时大小写不敏感：数据库中按标准格式（首字母大写或小写）导入的历史数据 SHALL 正常映射为对应 Java 枚举常量，MUST NOT 抛出枚举解析异常。写入路径 MAY 继续落库 Java 枚举常量名（大写），不改变既有存储格式；持久化层 MUST NOT 通过枚举 CHECK 约束把取值限定为大写常量名，以免阻塞按标准格式的直接 SQL 导入。

#### Scenario: 读取首字母大写的标准格式注册状态
- **WHEN** 数据库中 `registration_state.registration_status` 为按 11179-6:2023 书写的 `Standard`（历史导入数据）
- **THEN** 系统 SHALL 正常读取并映射为枚举 `STANDARD`，不抛出 `No enum constant` 异常

#### Scenario: 读取大写枚举名保持兼容
- **WHEN** 数据库中字符串枚举列存储为应用自身写入的大写枚举名（如 `RECORDED`）
- **THEN** 系统 SHALL 正常读取并映射为对应枚举常量

