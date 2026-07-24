## MODIFIED Requirements

### Requirement: 数据库语言标识初始化
在系统首次启动或系统重新初始化阶段（例如恢复出厂设置后执行系统配置向导），系统必须检查数据库中的语言标识记录。如果不存在，则必须自动插入 G20 国家所涉及的 22 种常见语言标识（包含 zh-CN、en-US、ja-JP、de-DE、fr-FR、ru-RU、ko-KR 等），且默认状态为“启用”。

#### Scenario: 首次启动或重新初始化后自动加载内置语言
- **WHEN** 系统首次启动或在恢复出厂设置并重新初始化系统后
- **THEN** 数据库中成功写入包括 zh-CN、en-US、ja-JP 在内的 G20 常用语言列表，并且 `enabled` 标志全部为 `true`
