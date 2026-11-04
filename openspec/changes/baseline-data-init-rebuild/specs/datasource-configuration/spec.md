## ADDED Requirements

### Requirement: SQLite 默认库文件位于用户目录

SQLite 数据源的默认库文件路径 MUST 为用户目录下的 `~/.metadata-registry/data/metadata-registry.db`，MUST NOT 默认为程序目录下的 `data/` 同名文件。该默认路径 MUST 在初始化向导与系统设置的文件路径输入框中作为默认值呈现，用户 MUST 能在系统初始化过程与系统设置中修改实际库文件路径。程序目录 `data/` 下的同名文件为预置基准数据的数据库文件（用途见 `baseline-data-scripts` 能力），其存在 MUST NOT 影响默认路径的判定。

#### Scenario: 无配置全新启动落在用户目录

- **GIVEN** 系统无任何用户数据源配置
- **WHEN** 以 SQLite 方言全新启动应用
- **THEN** 应用连接 `~/.metadata-registry/data/metadata-registry.db`（不存在则创建文件与父目录），程序目录 `data/` 不被写入库文件

#### Scenario: 初始化与设置中可修改路径

- **WHEN** 用户在初始化向导数据库步骤或系统设置数据源页提交一个自定义 SQLite 文件路径
- **THEN** 配置经连接测试通过后持久化，重启后应用连接新路径
