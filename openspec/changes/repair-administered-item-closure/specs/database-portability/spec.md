# database-portability Delta

## ADDED Requirements

### Requirement: 条目写操作在 SQLite 上不因 join 上限失败

SQLite 单条查询的 FROM 项上限（64 表）是平台限制，系统 SHALL 保证条目的更新与删除
路径不触发该限制。特别地，**带受管项（`Administered_Item`）实例的条目 SHALL 能在
SQLite 上正常更新与删除**，不得因关联对象图过深而返回 500。

系统 SHALL 通过查询形状收敛达成该目标：权限校验等只需要归属注册机构的路径，
SHALL NOT 加载完整受管项实体对象图。

> 2026-10-09 事故：为 11,177 条基准条目补建受管项实例后，SQLite（个人版）上这些条目
> 的更新与删除全部触发 `[SQLITE_ERROR] at most 64 tables in a join`。SQL Server 与达梦
> 无此限制，故缺陷仅在 SQLite 暴露，易被团队版冒烟漏过。

#### Scenario: 带受管项条目可在 SQLite 上更新

- **GIVEN** SQLite 数据库中某条目具备 `administered_item` 实例
- **WHEN** 经应用更新该条目
- **THEN** 更新成功，不返回 500，日志中不出现 `at most 64 tables in a join`

#### Scenario: 带受管项条目可在 SQLite 上删除

- **GIVEN** SQLite 数据库中某条目具备 `administered_item` 实例
- **WHEN** 经应用删除该条目
- **THEN** 删除成功，不返回 500，日志中不出现 `at most 64 tables in a join`

#### Scenario: 权限校验不加载完整受管项对象图

- **WHEN** 对某条目执行修改权限校验
- **THEN** 仅查询归属注册机构标识，不展开受管项的完整关联对象图

#### Scenario: SQL Server 与达梦行为一致

- **GIVEN** 同一带受管项条目的更新操作分别在 SQL Server、SQLite、达梦上执行
- **WHEN** 各方言均完成该操作
- **THEN** 三者结果一致，无方言特有的失败