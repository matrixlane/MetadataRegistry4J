## ADDED Requirements

### Requirement: 段模板引擎托管于扩展模块
段模板引擎(segment/ 与 segment/types/ 全部 19 类)SHALL 托管于 `metadata-registry-ext` 模块;模板设计器 UI、策略配置接口与版本化行为 SHALL 保持不变。

#### Scenario: 模板解析行为不变
- **WHEN** 使用既有段模板生成或反向解析标识符
- **THEN** 结果与迁移前一致

#### Scenario: 设计器页面可用
- **WHEN** 打开段规则设计器 UI
- **THEN** 模板编辑、预览、保存功能正常
