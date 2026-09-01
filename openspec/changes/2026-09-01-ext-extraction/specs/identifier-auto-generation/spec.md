## ADDED Requirements

### Requirement: 标识符生成策略框架托管于扩展模块
标识符自动生成策略框架(IdentifierGenerationStrategy 族、IdentifierStrategyConfig、SequenceRegistry、IdentifierVersionHistory 实体及版本治理 VersionStatus/VersioningPolicy/Versions/SemVerValidator)SHALL 托管于 `metadata-registry-ext` 模块(包 `io.github.matrixlane.metadataregistry.ext`),不再属于标准模块 `metadata-registry-common-facilities`;对外行为(DTO、REST 端点、前端交互)SHALL 保持不变。

#### Scenario: 标准模块不再包含策略框架类
- **WHEN** 检查 `metadata-registry-common-facilities` 的 identification 包
- **THEN** 其中不再包含 IdentifierGenerationStrategy 族、segment 模板引擎、版本治理等扩展类,仅保留标准第 7 章模型

#### Scenario: 自动生成功能行为不变
- **WHEN** 在启用了自动生成的命名空间下创建标识符
- **THEN** 生成结果与迁移前一致(同一策略同一配置产出同一格式标识符)
