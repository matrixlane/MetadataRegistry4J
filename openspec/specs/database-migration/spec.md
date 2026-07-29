# database-migration Specification

## Purpose
TBD - created by archiving change edition-aware-init-and-db-portability. Update Purpose after archive.
## Requirements
### Requirement: 跨库迁移管线
系统 SHALL 提供将当前数据库全部数据迁移到另一种受支持数据库的迁移能力，管线为：源库全量导出 → 目标库连接测试 → 目标库建表与基线迁移 → 按依赖序导入 → 逐表行数校验 → 切换数据源配置 → 引导重启。目标方言 MUST 是当前部署版本所支持的方言（`TEAM` 版不得迁移到 SQLite）。

#### Scenario: SQLite 迁移到达梦
- **WHEN** 个人版管理员发起从 SQLite 到达梦 DM8 的迁移，目标连接测试通过且目标库为空
- **THEN** 系统 SHALL 完成导出、导入与行数校验，切换配置并提示重启

#### Scenario: 团队版迁移目标为 SQLite 被拒绝
- **WHEN** 部署版本为 `TEAM`，管理员尝试将数据迁移到 SQLite
- **THEN** 系统 SHALL 拒绝并提示团队版不支持 SQLite

### Requirement: 迁移期间只读维护状态
迁移执行期间，系统 MUST 进入只读维护状态：所有写操作（业务数据修改、账号变更、配置变更）MUST 被拒绝并返回明确的维护提示，读操作可继续。迁移完成或中止后 MUST 恢复正常状态。

#### Scenario: 迁移期间写入被拒绝
- **WHEN** 迁移正在执行，用户尝试修改数据元素
- **THEN** 系统 SHALL 拒绝该写操作并提示系统处于迁移维护状态

### Requirement: 目标库必须为空
迁移导入的前置条件是目标库不含既有业务数据。目标库已存在业务数据时，系统 MUST 拒绝导入并提示先使用空库，防止发生数据合并事故。

#### Scenario: 非空目标库被拒绝
- **WHEN** 目标库中已存在业务数据，管理员发起迁移导入
- **THEN** 系统 SHALL 拒绝导入并提示更换为空数据库

### Requirement: 迁移校验与回滚
迁移完成后，系统 MUST 对每个表执行源库与目标库的行数比对，全部一致方可判定迁移成功并允许切换配置。任一步骤失败时，系统 MUST 保留原数据源配置与原库数据不变，允许管理员中止迁移并回滚到迁移前状态。

#### Scenario: 行数不一致不切换
- **WHEN** 导入完成后某表行数与源库不一致
- **THEN** 系统 SHALL 判定迁移失败，不修改数据源配置，并报告差异表清单

#### Scenario: 迁移失败回滚
- **WHEN** 迁移在导入阶段失败，管理员选择中止
- **THEN** 系统 SHALL 退出维护状态，继续使用原数据源，原库数据完整可用

