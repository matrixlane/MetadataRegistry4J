# datasource-configuration Delta

## MODIFIED Requirements

### Requirement: 配置持久化与重启生效

数据源配置 MUST 持久化到本地配置文件（`application-local.yml`），MUST NOT 存储在数据库中。持久化的数据源配置 MUST 在应用重启后优先于各 profile 的默认数据源配置生效；profile 默认值仅在没有用户配置时使用。配置变更统一以“写入配置 + 引导重启”方式生效，系统 MUST 在变更后明确提示需要重启。口令类字段 MUST NOT 在界面回显或日志中明文出现。

#### Scenario: 保存配置后提示重启

- **WHEN** 管理员在系统设置中保存新的数据源配置
- **THEN** 系统 SHALL 将配置写入 `application-local.yml`，重启后 SHALL 使用该配置连接数据源，并提示重启后生效

#### Scenario: SQLite 新文件路径在重启后生效

- **GIVEN** 用户保存了尚不存在的 SQLite 文件路径
- **WHEN** 用户重启应用
- **THEN** 应用 SHALL 连接所选路径，创建文件及所需父目录，并在首次启动时初始化数据库结构

#### Scenario: 口令不明文回显

- **WHEN** 管理员在系统设置中查看当前数据源配置
- **THEN** 界面 SHALL 以打码形式展示口令字段
