## ADDED Requirements

### Requirement: 命名空间策略由扩展卫星实体承载
命名空间的标识符策略属性(versioningPolicy、identifierInputMode、versionInputMode、manualIdentifierPattern)SHALL 从标准实体 `Namespace` 剥离,改由一对一卫星实体 `NamespaceIdentifierPolicy`(namespace_id 一对一)承载;ScopedIdentifier 的生命周期属性(creationMode、versionStatus 及时间戳)SHALL 剥离至卫星实体 `ScopedIdentifierLifecycle`。`Namespace`/`ScopedIdentifier` 标准实体 SHALL 只保留标准属性;DTO 与前端字段 SHALL 保持不变。

#### Scenario: 存量策略数据无损迁移
- **GIVEN** 库中 namespace 表已有策略字段数据
- **WHEN** 执行迁移脚本
- **THEN** NamespaceIdentifierPolicy 表逐行回填相同值,namespace 旧列删除后策略读写结果不变

#### Scenario: 命名空间设置页行为不变
- **WHEN** 在命名空间设置页修改标识符输入模式并保存
- **THEN** 再次打开页面显示修改后的值,且校验行为与迁移前一致
