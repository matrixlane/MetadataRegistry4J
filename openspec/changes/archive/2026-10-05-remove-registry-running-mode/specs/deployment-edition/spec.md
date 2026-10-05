# Deployment Edition Delta Specification

## MODIFIED Requirements

### Requirement: 个人版功能约束
当部署版本为 `PERSONAL` 时，系统 SHALL 允许超级管理员创建任意数量的注册机构，但系统初始化创建的注册机构为带 Primary 标志的"主要"机构，MUST NOT 被删除；超级管理员创建新机构时 SHALL 自动成为该机构的注册人员（详见 `registration-authority-lifecycle` 能力）。初始化向导 MUST 与个人数据治理的先决条件对齐：展示注册规范、注册机构与命名空间配置步骤，治理字段（注册机构标识、注册机构等）SHALL 预填合理默认值以保持精简体验，允许管理员确认或修改；系统 MUST NOT 在用户无感知的情况下静默创建治理对象。系统不存在"单注册表/多注册表"运行模式概念。

#### Scenario: 个人版初始化创建注册机构
- **WHEN** 管理员以 `PERSONAL` 版本完成初始化向导
- **THEN** 系统 SHALL 依据向导中确认或修改后的治理配置创建注册机构（该机构带 Primary 标志、不可删除），且管理员账号 SHALL 自动成为其注册员

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
