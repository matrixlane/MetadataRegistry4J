## Why

MetadataRegistry4j 的域模型已基本覆盖 ISO/IEC 11179-3:2023 第 8 章（指称与定义包）中的 `Designation`、`Definition`、`DesignationContext`、`DefinitionContext`、`DesignationDefinitionPairing` 与 `Acceptability`。但标准在第 8.4.4 节定义的 `Naming_Convention`（命名约定）类及其与 `Designation`、`Namespace` 的关联在当前模型中缺失：`Namespace` 仅以 `UUID namingConventionId` 记录一个无类型约束的外键，`Designation` 也没有声明其遵循的命名约定。这导致命名规则无法作为注册项被管理、版本化和引用，削弱了与标准的一致性。

## What Changes

- 新增 `NamingConvention` 域实体，作为 `Item` 的子类，纳入注册体系（可被标识、被指称、被定义、被注册管理）。
- 在 `Designation` 与 `NamingConvention` 之间建立 `naming_convention_conformance` 关联，标识某个指称遵循的命名约定。
- 将 `Namespace` 中的 `namingConventionId`（裸 UUID）替换为到 `NamingConvention` 的 JPA 关联（`naming_convention_utilization`），保留现有字段作为兼容过渡列。
- 扩展 `metadata-registry-web` 中已有的 `NamingConventionEngine`，使其能够加载并执行注册在 `NamingConvention` 实体中的规则定义。
- 新增 Flyway 迁移脚本，将 `namespace` 表的命名约定外键从 `UUID` 指向 `naming_convention` 表，并补充 `designation_naming_convention` 关联表。
- 补充单元测试与集成测试，验证 `NamingConvention` 的 CRUD、关联持久化及规则执行。
- **BREAKING**：`Namespace.getNamingConventionId()` 的返回类型/语义将调整为实体引用；调用方需迁移。

## Capabilities

### New Capabilities

- `iso-11179-3-ch8-naming-convention`: 实现 ISO/IEC 11179-3:2023 第 8 章的 `Naming_Convention` 类及其与 `Designation`、`Namespace` 的关联，使命名约定成为一等注册项。

### Modified Capabilities

- （无现有 OpenSpec 规范需要修改；本变更只新增域模型能力，不修改现有业务功能的需求行为。）

## Impact

- `metadata-registry-common-facilities`：新增 `designationanddefinition` 包下的 `NamingConvention` 实体及关联调整；`Namespace`、`Designation` 增加 JPA 关系。
- `metadata-registry-web`：`NamingConventionEngine` 需支持从 `NamingConvention` 实体读取规则；相关 API/DTO 需暴露 `namingConventionId`。
- 数据库：`namespace` 表外键迁移，`designation_naming_convention` 关联表新增；`naming_convention` 表新增。
- 测试与文档：补充 JPA 测试、迁移测试；更新 `ISO_11179-3_2023_Comparison_Report.md` 中第 8 章的状态。
