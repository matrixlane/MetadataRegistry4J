# Registry Specification Delta Specification

## ADDED Requirements

### Requirement: 初始化后注册规范禁止删除
系统初始化完成后，注册规范（Registry_Specification）MUST NOT 被删除：后端 MUST 拒绝单条删除请求并返回业务校验错误（400 Bad Request）；批量删除经同一删除接口逐条执行，MUST 同样逐条被拒绝。前端 MUST 在注册规范设置页隐藏删除与批量删除入口，仅保留编辑更新。系统未初始化（初始化向导草稿阶段）时删除请求 MAY 放行。

#### Scenario: 初始化完成后删除注册规范
- **WHEN** 系统已初始化，用户提交删除注册规范请求（单条或批量）
- **THEN** System SHALL 拒绝请求并返回 400 业务校验错误，提示初始化后注册规范不允许删除
- **AND** 注册规范记录保持不变

#### Scenario: 初始化完成后注册规范设置页无删除入口
- **WHEN** 系统已初始化，用户打开注册规范设置页
- **THEN** 列表行操作列不出现"删除"按钮，且不提供批量删除（多选删除）入口，仅保留"编辑"

#### Scenario: 未初始化（向导草稿阶段）删除注册规范
- **WHEN** 系统尚未初始化，用户提交删除注册规范请求
- **THEN** System SHALL 按既有删除逻辑放行（初始化向导草稿数据允许清理重建）
