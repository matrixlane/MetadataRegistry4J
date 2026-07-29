## ADDED Requirements

### Requirement: 部署版本选择与持久化
系统 SHALL 在初始化向导的第一步提供部署版本选择，候选值为 `PERSONAL`（个人版）与 `TEAM`（团队版），默认值为 `PERSONAL`。所选版本 MUST 在初始化提交（commit）时持久化到 `system_setting` 的 `system.edition` 键。版本写入后 MUST NOT 通过常规管理界面或 API 修改；变更版本只能通过恢复出厂设置后重新初始化。

#### Scenario: 提交初始化时持久化版本
- **WHEN** 管理员在初始化向导中选择 `TEAM` 并完成提交
- **THEN** 系统 SHALL 将 `system.edition=TEAM` 写入 `system_setting`，并将系统标记为已初始化

#### Scenario: 已初始化后尝试修改版本
- **WHEN** 系统已初始化，任何调用方尝试通过 API 修改 `system.edition`
- **THEN** 系统 SHALL 拒绝该请求并返回业务校验错误

### Requirement: 版本状态查询
系统 SHALL 在初始化状态接口（`/api/v1/init-wizard/status`）的响应中返回当前部署版本。系统未初始化时 MUST 返回 `null` 或空值，由前端引导用户进入版本选择。

#### Scenario: 未初始化时查询版本
- **WHEN** 系统尚未完成初始化，客户端查询初始化状态
- **THEN** 响应 SHALL 指示未初始化，且版本字段为空

#### Scenario: 已初始化时查询版本
- **WHEN** 系统已完成初始化且版本为 `PERSONAL`，客户端查询初始化状态
- **THEN** 响应 SHALL 包含 `edition=PERSONAL`

### Requirement: 个人版功能约束
当部署版本为 `PERSONAL` 时，系统 SHALL 强制注册表治理策略为 SINGLE（仅允许一个注册机构），MUST NOT 提供切换治理策略的入口，且 MUST 隐藏多注册机构管理入口。初始化向导 SHALL 自动创建一个默认注册机构，治理字段（注册机构标识、命名机构等）MUST 使用合理默认值，允许用户后续补全。

#### Scenario: 个人版初始化自动创建注册机构
- **WHEN** 管理员以 `PERSONAL` 版本完成初始化向导
- **THEN** 系统 SHALL 自动创建一个默认注册机构，且管理员账号 SHALL 自动成为其注册员

#### Scenario: 个人版禁止新增注册机构
- **WHEN** 部署版本为 `PERSONAL` 且已存在注册机构，调用方请求创建新的注册机构
- **THEN** 系统 SHALL 拒绝该请求并返回业务校验错误

### Requirement: 团队版功能约束
当部署版本为 `TEAM` 时，系统 SHALL 提供完整的初始化治理配置（注册机构、命名空间、注册表规范），注册表治理策略默认为 SINGLE。系统 SHALL 允许超级管理员在系统设置中将治理策略切换为 MULTI，切换后 MUST 允许创建多个注册机构。

#### Scenario: 团队版默认单注册机构
- **WHEN** 部署版本为 `TEAM` 且治理策略为 SINGLE，已存在一个注册机构，调用方请求创建新的注册机构
- **THEN** 系统 SHALL 拒绝该请求并返回业务校验错误

#### Scenario: 团队版切换为多注册机构策略
- **WHEN** 部署版本为 `TEAM`，超级管理员将治理策略切换为 MULTI
- **THEN** 系统 SHALL 允许创建第二个及更多注册机构

### Requirement: 云端版本占位
系统 SHALL 在版本枚举中保留 `CLOUD` 占位值，但 MUST NOT 在初始化向导中提供该选项；通过 API 直接提交 `CLOUD` 时系统 MUST 拒绝并返回明确的错误信息。`CLOUD` 占位 MUST NOT 引入任何租户隔离行为。

#### Scenario: 通过 API 提交云端版本
- **WHEN** 调用方在初始化提交中指定 `CLOUD` 版本
- **THEN** 系统 SHALL 拒绝该请求，并提示该版本在当前交付中不可用
