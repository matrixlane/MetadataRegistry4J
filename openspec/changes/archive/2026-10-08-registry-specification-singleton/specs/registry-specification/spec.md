# Registry Specification Delta Specification

## MODIFIED Requirements

### Requirement: 注册规范唯一且初始化后禁止增删
注册规范（Registry_Specification）为注册表的固有组成部分，全系统 MUST 有且仅有一条。任何在已存在记录时提交的新增请求 MUST 被拒绝并返回业务校验错误（400 Bad Request）；系统初始化完成后，注册规范 MUST NOT 被删除：后端 MUST 拒绝单条删除请求（400），批量删除经同一删除接口逐条执行，MUST 同样逐条被拒绝。前端 MUST 在注册规范设置页隐藏新增、删除与批量删除入口，仅保留编辑更新。系统未初始化（初始化向导草稿阶段）时：新增仅由初始化向导经 createDefault 创建（setup 前清空业务数据保证唯一），删除请求 MAY 放行。

#### Scenario: 已存在注册规范时再次新增
- **WHEN** 系统中已存在一条注册规范记录，用户提交新增注册规范请求（任意入口含 API 直连）
- **THEN** System SHALL 拒绝请求并返回 400 业务校验错误，提示注册规范有且仅有一条
- **AND** 既有注册规范记录保持不变

#### Scenario: 初始化完成后删除注册规范
- **WHEN** 系统已初始化，用户提交删除注册规范请求（单条或批量）
- **THEN** System SHALL 拒绝请求并返回 400 业务校验错误，提示初始化后注册规范不允许删除
- **AND** 注册规范记录保持不变

#### Scenario: 初始化完成后注册规范设置页仅保留编辑入口
- **WHEN** 系统已初始化，用户打开注册规范设置页
- **THEN** 不出现"新建"按钮，列表行操作列不出现"删除"按钮，且不提供批量删除（多选删除）入口，仅保留"编辑"

#### Scenario: 未初始化（向导草稿阶段）删除注册规范
- **WHEN** 系统尚未初始化，用户提交删除注册规范请求
- **THEN** System SHALL 按既有删除逻辑放行（初始化向导草稿数据允许清理重建）

#### Scenario: 初始化向导重复提交后仍仅一条注册规范
- **WHEN** 超级管理员重复提交初始化向导 setup（前次草稿未提交 commit）
- **THEN** System SHALL 先清空业务数据再经 createDefault 创建，最终系统中仍仅存在一条注册规范
