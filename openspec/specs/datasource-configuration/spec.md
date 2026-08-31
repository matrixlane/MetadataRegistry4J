# datasource-configuration Specification

## Purpose
TBD - created by archiving change edition-aware-init-and-db-portability. Update Purpose after archive.
## Requirements
### Requirement: 数据源配置模型与方言合法性
系统 SHALL 支持配置以下数据源：SQLite（文件路径）、SQL Server（主机/端口/库名/凭据）、达梦 DM8（主机/端口/库名或模式/凭据）。配置 MUST 显式记录方言标识（`sqlite`/`sqlserver`/`dm`）。方言合法性 MUST 受部署版本约束：`TEAM` 版 MUST NOT 接受 `sqlite`；`PERSONAL` 版接受全部三种方言。

#### Scenario: 团队版配置 SQLite 被拒绝
- **WHEN** 部署版本为 `TEAM`，调用方提交方言为 `sqlite` 的数据源配置
- **THEN** 系统 SHALL 拒绝该配置并返回明确的业务校验错误

#### Scenario: 个人版配置达梦被接受
- **WHEN** 部署版本为 `PERSONAL`，调用方提交合法的达梦连接参数并通过连接测试
- **THEN** 系统 SHALL 接受并持久化该配置

### Requirement: 连接测试
系统 SHALL 提供数据源连接测试能力：在保存或应用配置之前，使用所给参数建立测试连接并探测数据库版本与方言。连接测试失败的配置 MUST NOT 被持久化，MUST NOT 影响当前运行中的数据源。

#### Scenario: 连接测试失败不落地
- **WHEN** 用户提交无法连通的数据源参数（错误主机或错误凭据）
- **THEN** 系统 SHALL 返回明确的连接失败原因，且不写入任何配置文件

#### Scenario: 连接测试成功
- **WHEN** 用户提交可达的 SQL Server 连接参数
- **THEN** 系统 SHALL 返回成功及探测到的数据库版本信息

### Requirement: 配置持久化与重启生效
数据源配置 MUST 持久化到本地配置文件（`application-local.yml`），MUST NOT 存储在数据库中。配置变更统一以"写入配置 + 引导重启"方式生效，系统 MUST 在变更后明确提示需要重启。口令类字段 MUST NOT 在界面回显或日志中明文出现。

#### Scenario: 保存配置后提示重启
- **WHEN** 管理员在系统设置中保存新的数据源配置
- **THEN** 系统 SHALL 将配置写入 `application-local.yml`，并提示重启后生效

#### Scenario: 口令不明文回显
- **WHEN** 管理员在系统设置中查看当前数据源配置
- **THEN** 界面 SHALL 以打码形式展示口令字段

### Requirement: 系统设置中的数据源管理
系统设置 SHALL 提供当前数据源信息的只读视图（方言、地址、库名）与变更入口。变更入口 MUST 要求超级管理员权限，MUST 执行连接测试，并在保存前明确提示"变更将导致系统重启后方可生效"。

#### Scenario: 非管理员尝试变更数据源
- **WHEN** 非超级管理员用户调用数据源配置变更接口
- **THEN** 系统 SHALL 拒绝该请求并返回权限错误

#### Scenario: 管理员变更数据源
- **WHEN** 超级管理员提交新的达梦数据源配置且连接测试通过
- **THEN** 系统 SHALL 持久化配置并提示重启生效

### Requirement: 初始化向导数据库步骤的保存入口合并
初始化向导的数据库步骤 SHALL 只保留一个前进入口（"下一步"），MUST NOT 提供独立的"保存数据源配置"按钮。选择"配置新的数据源"时，表单 MUST 预填为当前数据源配置（凭据除外，凭据不回传、留空由用户按需填写），使表单所见与当前生效配置一致。修改过任一连接参数时，"下一步"即保存数据源配置，保存前 MUST 已通过当前参数的连接测试，保存后按"写入配置 + 引导重启"流程处理；未修改任何参数时，"下一步" MUST 等价于"保持当前数据源并继续"，MUST NOT 触发保存或重启提示。连接参数在测试通过后被再次修改时，此前的测试结果 MUST 失效。

#### Scenario: 修改参数后下一步即保存
- **WHEN** 用户在向导数据库步骤选择"配置新的数据源"，修改了连接参数，连接测试已通过，点击"下一步"
- **THEN** 系统 SHALL 保存该数据源配置并提示重启后生效

#### Scenario: 修改参数但未测试连接时下一步被拦截
- **WHEN** 用户修改了连接参数但未通过连接测试，点击"下一步"
- **THEN** 系统 SHALL 阻止前进并提示先测试连接，且不写入任何配置文件

#### Scenario: 未修改参数时下一步直接前进
- **WHEN** 用户选择"配置新的数据源"但未修改任何连接参数（包括修改后改回原值），点击"下一步"
- **THEN** 系统 SHALL 与"保持当前数据源并继续"同样处理：直接进入下一步，不保存配置、不提示重启

#### Scenario: 配置新的数据源表单预填当前配置
- **WHEN** 用户在向导数据库步骤选择"配置新的数据源"
- **THEN** 表单 SHALL 预填当前生效数据源的方言与连接参数（凭据字段留空）
- **AND** 当前数据源信息卡片 SHALL 展示当前生效的方言与地址


