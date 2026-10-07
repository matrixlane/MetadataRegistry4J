# deployment-edition Specification

## Purpose
TBD - created by archiving change edition-aware-init-and-db-portability. Update Purpose after archive.

## Requirements

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

当部署版本为 `PERSONAL` 时，系统 SHALL 允许超级管理员创建任意数量的注册机构，但系统初始化创建的注册机构为带 Primary 标志的"主要"机构，MUST NOT 被删除；超级管理员创建新机构时 SHALL 自动成为该机构的注册人员（详见 `registration-authority-lifecycle` 能力，其首个 Registrar 身份自动标记为默认注册人员）。初始化向导 MUST 与个人数据治理的先决条件对齐：展示注册规范、注册机构与命名空间配置步骤，治理字段（注册机构标识、注册机构等）SHALL 预填合理默认值以保持精简体验，允许管理员确认或修改；系统 MUST NOT 在用户无感知的情况下静默创建治理对象。系统不存在"单注册表/多注册表"运行模式概念。个人版不存在登录概念：用户管理、自主注册、密码与应急码相关能力对个人版 MUST NOT 提供入口或产生行为（详见"个人版免登录访问"）。

#### Scenario: 个人版初始化创建注册机构

- **WHEN** 管理员以 `PERSONAL` 版本完成初始化向导
- **THEN** 系统 SHALL 依据向导中确认或修改后的治理配置创建注册机构（该机构带 Primary 标志、不可删除），且管理员账号 SHALL 自动成为其注册员（首个 Registrar 自动标记为默认注册人员）

#### Scenario: 个人版禁止新增注册机构

- **WHEN** 部署版本为 `PERSONAL` 且已存在注册机构，非超级管理员调用方请求创建新的注册机构
- **THEN** 系统 SHALL 拒绝并返回业务校验错误（个人版新增机构仅限超级管理员本人，超级管理员创建机构无数量限制）

#### Scenario: 个人版主要注册机构禁止删除

- **WHEN** 部署版本为 `PERSONAL`，调用方请求删除初始化创建的主要注册机构
- **THEN** 系统 SHALL 拒绝并返回业务校验错误（删除保护规则详见 `registration-authority-lifecycle` 能力）

### Requirement: 团队版功能约束
当部署版本为 `TEAM` 时，系统 SHALL 提供完整的初始化治理配置（注册规范、注册机构、命名空间），超级管理员可创建任意数量的注册机构。系统不存在"单注册表/多注册表"运行模式概念，MUST NOT 提供治理策略切换入口。

#### Scenario: 团队版默认单注册机构
- **WHEN** 部署版本为 `TEAM` 且已存在一个注册机构，超级管理员请求创建新的注册机构
- **THEN** 系统 SHALL 允许创建（系统不存在治理策略概念，无默认数量限制）

#### Scenario: 团队版切换为多注册机构策略
- **WHEN** 管理员在系统设置中查找注册表治理策略（SINGLE/MULTI）切换入口或调用对应 API
- **THEN** 系统 MUST NOT 存在该入口或对应 API

### Requirement: 云端版本占位
系统 SHALL 在版本枚举中保留 `CLOUD` 占位值，但 MUST NOT 在初始化向导中提供该选项；通过 API 直接提交 `CLOUD` 时系统 MUST 拒绝并返回明确的错误信息。`CLOUD` 占位 MUST NOT 引入任何租户隔离行为。

#### Scenario: 通过 API 提交云端版本
- **WHEN** 调用方在初始化提交中指定 `CLOUD` 版本
- **THEN** 系统 SHALL 拒绝该请求，并提示该版本在当前交付中不可用

### Requirement: 个人版免登录访问

当部署版本为 `PERSONAL` 且系统已初始化时，系统 SHALL NOT 要求任何 API 请求携带认证凭证：无有效令牌的请求 SHALL 由系统自动以系统唯一超级管理员账号的身份处理（按该账号当前绑定状态装配注册人员/提交人角色）。个人版 MUST NOT 存在登录页面、注册入口、修改密码入口与用户管理入口；前端 SHALL 直接进入应用。个人版 MUST NOT 触发任何强制改密/临时密码/应急登录拦截（无登录流程故无密码状态语义）。团队版（`TEAM`）行为 MUST 完全不变：所有 API 仍要求有效令牌，登录/注册/密码机制照常。

#### Scenario: 个人版未携带令牌访问业务接口

- **WHEN** `PERSONAL` 版已初始化系统收到一个无 `Authorization` 头的业务 API 请求
- **THEN** 系统按唯一超级管理员身份处理该请求，其注册工作、提交、治理操作权限与该账号绑定状态一致

#### Scenario: 个人版前端无登录页

- **WHEN** 用户在 `PERSONAL` 版已初始化系统打开应用
- **THEN** 前端不呈现登录/注册界面，直接进入应用主界面

#### Scenario: 个人版不触发密码拦截

- **WHEN** `PERSONAL` 版系统处理任意已认证请求（含存量库升级场景）
- **THEN** 系统 MUST NOT 返回强制改密（498）拦截；密码相关标志对个人版不产生任何行为

#### Scenario: 团队版仍要求登录

- **WHEN** `TEAM` 版系统收到无有效令牌的业务 API 请求
- **THEN** 系统 SHALL 返回未认证错误（401），行为与本变更前一致

#### Scenario: 未初始化个人版不自动认证

- **WHEN** `PERSONAL` 版系统尚未完成初始化（无业务数据）
- **THEN** 请求保持匿名，初始化向导相关端点匿名可访问，其余 API 仍由未初始化访问控制返回 499
