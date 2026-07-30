## Context

当前 `metadata-registry-common-facilities` 已按 ISO/IEC 11179-3:2023 第 8 章实现了 `Designation`、`Definition` 及其上下文/配对类，且 `Namespace`（第 7 章）已具备 `mandatoryNamingConventionIndicator`、`oneNamePerItemIndicator`、`oneItemPerNameIndicator`、`shorthandPrefix`、`schemeReference` 等属性。然而标准在第 8.4.4 节要求 `Naming_Convention` 本身是一个可被注册、指称和定义的 `Item` 子类，并通过 `naming_convention_conformance`（`Designation`→`Naming_Convention`）和 `naming_convention_utilization`（`Namespace`→`Naming_Convention`）关联被引用。当前实现中：

- `Namespace` 仅有 `UUID namingConventionId`，没有真正的域实体引用；
- `Designation` 没有指向命名约定的关联；
- `metadata-registry-web` 的 `NamingConventionEngine` 是运行时字符串校验工具，其规则不是注册项。

## Goals / Non-Goals

**Goals:**
- 将 `Naming_Convention` 建模为 `Item` 子类，支持标识、指称、定义、注册状态及槽扩展。
- 建立 `Designation` ↔ `NamingConvention` 与 `Namespace` ↔ `NamingConvention` 的 JPA 关联。
- 保持现有 `NamingConventionEngine` 的运行时 API 不变，但支持从 `NamingConvention` 实体加载规则。
- 提供数据库迁移脚本，保证已有 `namespace.naming_convention_id` 数据可迁移。

**Non-Goals:**
- 不重新定义命名规则语法（继续复用 `NamingConventionRule` 体系）。
- 不替换现有的 `NamingConventionEngine` 调用点，只在其内部增加从实体读取规则的能力。
- 不实现完整的约束/校验触发器（只建立模型与关联，触发器可在后续 WI 中补充）。

## Decisions

1. **继承策略**：`NamingConvention` 继承 `Item`（JOINED 继承），与 `Designation`、`Definition` 位于同一 `designationanddefinition` 包。这样可直接复用 `IdentifiedItem`、`RegisteredItem`、`AdministeredItem` 等注册体系。
2. **规则存储形式**：`NamingConvention` 包含一个 `rules` 文本/JSON 列，由 `NamingConventionEngine` 在运行时解析为 `NamingConventionRule` 列表。规则语法保持与现有 web 模块一致，避免双重建模。
3. **关联关系**：
   - `Namespace` → `NamingConvention`：多对一（`@ManyToOne`），并保留 `naming_convention_id` 列作为外键，实现无损迁移。
   - `Designation` → `NamingConvention`：多对多（`@ManyToMany`）或一对多（`@ManyToOne`）。根据标准，一个 `Designation` 可遵循多个命名约定，因此采用多对多，并新增关联表 `designation_naming_convention`。
4. **与 web 模块集成**：`NamingConventionEngine` 增加 `evaluate(Designation designation)` 或 `evaluate(String value, UUID namingConventionId)` 重载，优先从注册实体加载规则，失败时回退到原有规则集合。
5. **向后兼容**：保留 `Namespace.namingConventionId` 字段名，但类型从 `UUID` 改为 `NamingConvention` 实体；现有 getter/setter 签名调整为返回/接收实体。受影响的调用点（如服务层）在 tasks 中列出并迁移。

## Risks / Trade-offs

- [风险] `Namespace` 表可能已有 `naming_convention_id` 数据指向不存在的 UUID 或内部规则。→ 迁移脚本先检查外键完整性，对无效值设为 NULL，并在应用日志中告警。
- [风险] `NamingConvention` 成为 `Item` 子类后，所有注册项通用查询（如按类型）都会包含它，可能影响现有索引。→ 在 `item` 继承表中增加 `dtype` 过滤已在 JOINED 策略下自动处理。
- [风险] 规则存储为文本/JSON 会降低类型安全性。→ 规则加载时校验并记录非法规则；后续可考虑单独规则表做更严格建模。
- [权衡] 多对多关联增加关联表复杂度，但更符合标准语义；若业务上只需一个命名约定，可改为多对一并降低复杂度。本次按标准实现多对多。

## Migration Plan

1. 新增 `V20260724001__create_naming_convention.sql`：创建 `naming_convention` 表（继承 `item`）及规则列。
2. 新增 `V20260724002__namespace_naming_convention_fk.sql`：将 `namespace.naming_convention_id` 改为外键；清理无效值。
3. 新增 `V20260724003__designation_naming_convention.sql`：创建 `designation_naming_convention` 关联表。
4. 回滚：删除外键约束、关联表、`naming_convention` 表；将 `namespace.naming_convention_id` 恢复为无约束 UUID（数据不可逆，需备份）。

## Open Questions

- 命名规则 JSON 格式是否采用现有 `NamingConventionRule` 的序列化形式，还是引入新的 DSL？
- 是否需要在 `NamingConvention` 上增加 `appliesToItemType` 等限定属性，以约束其适用范围？
- 是否将现有内置命名约定（如系统默认）也迁移为 `NamingConvention` 实体？
