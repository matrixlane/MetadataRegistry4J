# Deployment Edition Delta Specification

## ADDED Requirements

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

## MODIFIED Requirements

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
