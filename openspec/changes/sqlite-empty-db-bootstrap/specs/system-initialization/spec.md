# system-initialization Delta

## ADDED Requirements

### Requirement: 全新 SQLite 库启动后可进入初始化向导

个人版使用全新 SQLite 文件库启动时，系统 SHALL 先创建当前应用数据库结构，再执行 Flyway 新库基线和静态预置，然后开放初始化向导状态查询。该流程 MUST NOT 依赖预先存在的数据库文件或手动导入基准库。基线之后的初始化 SHALL 使用既有 `setup`/`commit` 服务建立治理数据和完整注册项关联链。

#### Scenario: 新检出无数据库文件时读取初始化状态

- **GIVEN** 本地 SQLite 数据库文件不存在
- **WHEN** 个人版应用启动并请求 `/api/v1/init-wizard/status`
- **THEN** 应用 SHALL 正常响应未初始化状态，SQLite 文件、schema、共同 Flyway 基线和静态预置均已就绪

#### Scenario: 新库完成初始化

- **GIVEN** 全新 SQLite 库的初始化向导可用
- **WHEN** 个人版执行 `setup` 后执行 `commit`
- **THEN** 系统 SHALL 标记为已初始化，且初始化生成的已标识条目满足受管链语义闭环
