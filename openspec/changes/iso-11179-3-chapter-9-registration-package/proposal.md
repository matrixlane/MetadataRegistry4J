## Why

当前 MetadataRegistry4j 的 Registration 元模型与 ISO/IEC 11179-3:2023 第9章（Registration package）已基本对齐，但仍有若干类与关联缺失。为支持完整的注册生命周期管理、约束治理与注册环境声明，需要补充缺失的元模型元素，使项目能够声称对第9章的注册合规性。

## What Changes

- 新增 `Constraint_Set` 实体类（继承 `Item`）及 `registered_item_constraint_set` 关联，用于注册机构对注册项施加命名、定义、分类等约束。
 - 新增 `Registry_Specification` 实体类，用于描述注册环境（语言、字符集、URL、标准、合规等级等）。
 - 新增 `Registry_Specification` 实体类，用于描述注册环境（语言、字符集、URL、标准、合规等级等）。复用并修复前端已有的 `/registry-specifications` 占位路由，使其指向实际可运行的管理页面。
- 在 `RegisteredItem` 与 `ReferenceDocument` 之间补充 `reference` 多对多关联，支持为注册项附加参考文档。
- 将 `RegistrationStatus` 枚举恢复 `APPLICATION` 状态，以完整覆盖 ISO/IEC 11179-3:2023 第9章的注册状态集合。
- 补充 `Constraint_Set` 和 `Registry_Specification` 对应的 REST API、DTO、种子数据及单元测试。

## Capabilities

### New Capabilities
- `constraint-set`: 定义并管理注册项约束集。
- `registry-specification`: 声明注册环境的元数据规格。
- `reference-document-link`: 将注册项与参考文档关联。

### Modified Capabilities
- `registration-status`: 在现有生命周期枚举中恢复 `APPLICATION` 状态，扩展可表达的注册状态。

## Impact

- 受影响模块：`metadata-registry-common-facilities`（领域模型）、`metadata-registry-web`（REST API、服务、种子数据）。
 - 数据库：Hibernate 将自动新增 `constraint_set`、`registry_specification` 及相关连接表。
 - 前端：复用 `/registry-specifications` 现有菜单/路由，创建缺失的 `RegistrySpecificationSettings.vue` 组件；`Constraint_Set` 的 UI 页面后续按需补充。
 - 向后兼容：现有 `RegistrationStatus` 中移除 `CANDIDATE` 的历史遗留状态不受影响；恢复 `APPLICATION` 仅扩展枚举值。
