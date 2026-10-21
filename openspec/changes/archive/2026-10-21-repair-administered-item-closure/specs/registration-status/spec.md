# registration-status Delta

## ADDED Requirements

### Requirement: 数据库层注册状态约束与标准值集对齐

应用层枚举正确并不足够：`registration_state.registration_status` 列在数据库层 SHALL
带有 CHECK 约束，且其允许值集 MUST 与 ISO/IEC 11179-3:2023 Table 30（9.6.2）/
ISO/IEC 11179-6:2023 Table 1（4.3.2）的十个标准状态严格一致——`INCOMPLETE`、
`CANDIDATE`、`RECORDED`、`QUALIFIED`、`STANDARD`、`PREFERRED_STANDARD`、
`SUPERSEDED`、`RETIRED`、`HISTORICAL`、`APPLICATION`。

标准中不存在的 `SUBMITTED` 与 `PREFERRED` SHALL NOT 出现在该约束中；标准存在的
`CANDIDATE` 与 `PREFERRED_STANDARD` SHALL NOT 缺失。持久化书写形式 SHALL 容忍大小写
与"Preferred Standard"含空格的写法。

该约束在 SQL Server、SQLite、达梦三种方言下 SHALL 一致。迁移 SHALL 幂等：目标约束
已是标准值集时为 no-op。

> 2026-10-09 事故：真实库约束仍是历史错误方向的旧值集（由已生效的早期错误迁移写入），
> 而反向纠偏脚本因版本号 ≤ Flyway 基线版本而被跳过，从未执行。实测
> `PREFERRED_STANDARD` 写入被 CHECK 拒绝。任何标准状态在 DB 层不可写入即属缺陷。

#### Scenario: Preferred Standard 可在数据库层写入

- **WHEN** 向 `registration_state` 写入 `registration_status = 'PREFERRED_STANDARD'` 的记录
- **THEN** 写入被接受，不触发 CHECK 约束违例

#### Scenario: Candidate 可在数据库层写入

- **WHEN** 向 `registration_state` 写入 `registration_status = 'CANDIDATE'` 的记录
- **THEN** 写入被接受，不触发 CHECK 约束违例

#### Scenario: 标准外的 Submitted 被数据库层拒绝

- **WHEN** 向 `registration_state` 写入 `registration_status = 'SUBMITTED'` 的记录
- **THEN** 写入被 CHECK 约束拒绝，因为 `SUBMITTED` 不是标准注册状态

#### Scenario: 标准外的 Preferred 被数据库层拒绝

- **WHEN** 向 `registration_state` 写入 `registration_status = 'PREFERRED'` 的记录
- **THEN** 写入被 CHECK 约束拒绝，`PREFERRED` 不是标准注册状态名

#### Scenario: 全新库与存量库约束一致

- **GIVEN** 一个执行过全部迁移的全新 schema 与一个已存在数据的 schema
- **WHEN** 分别检查 `registration_state.registration_status` 的允许值集
- **THEN** 两者均为同一标准十值集，无一方残留历史错误值集

#### Scenario: 迁移幂等

- **WHEN** 约束修正迁移在已符合标准值集的数据库上再次执行
- **THEN** 不报错、不改变既有数据，仅为 no-op