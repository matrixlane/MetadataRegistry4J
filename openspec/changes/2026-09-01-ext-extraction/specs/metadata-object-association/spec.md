## ADDED Requirements

### Requirement: 关联服务托管于 web 服务层
association 子包 11 个纯服务类(无 JPA 实体)SHALL 从标准模块 `metadata-registry-data-specification` 迁至 `metadata-registry-web` 的 service/association 包;关联 REST 端点与四实体关联管理界面行为 SHALL 保持不变。

#### Scenario: 标准模块不再包含关联服务
- **WHEN** 检查 `metadata-registry-data-specification` 的包结构
- **THEN** 不再包含 association 子包

#### Scenario: 关联管理界面行为不变
- **WHEN** 在四实体任一处使用关联管理 tab 建立/解除关联
- **THEN** 操作结果与迁移前一致
