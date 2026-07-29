 ## Context
 
 ISO/IEC 11179-3:2023 第9章定义了 Registration package，包含 Registered_Item、Administered_Item、Attached_Item、Registration_State、Constraint_Set、Registration_Authority、Registrar、Stewardship_Record、Submission_Record 与 Registry_Specification 等类，以及它们之间的关联。当前项目中除 Constraint_Set、Registry_Specification 外，其余类均已实现；reference 关联与 registered_item_constraint_set 关联缺失；RegistrationStatus 枚举在 WI-019 中移除了标准中的 APPLICATION 状态。
 
 > **注意**：前端路由表中存在 `/registry-specifications`（菜单显示“注册规范”），但对应的组件文件 `views/registry-spec/RegistrySpecificationSettings.vue` 不存在，且后端没有任何 Registry_Specification 实体、Repository、Service 或 Controller。因此该菜单项仅为占位，并非 ISO/IEC 11179-3:2023 第9章 Registry_Specification 类的实现。本 change 将补全真正的后端领域模型，并将前端路由指向实际可运行的页面。
 
 ## Goals / Non-Goals
 
 **Goals:**
 - 补全第9章要求的 Constraint_Set 与 Registry_Specification 领域模型。
 - 建立 RegisteredItem 与 ReferenceDocument 的 reference 关联。
 - 建立 RegisteredItem 与 ConstraintSet 的 registered_item_constraint_set 关联。
 - 恢复 RegistrationStatus.APPLICATION，使枚举覆盖标准定义。
 - 为新模型提供 CRUD REST API 与种子数据，保证项目可启动并验证。
 - 修复现有 `/registry-specifications` 前端路由，使其指向实际可运行的管理页面。
 
 **Non-Goals:**
 - 不实现 Classification package（第10章）与 Item Mapping package（第11章）。
 - 不实现 Constraint_Set 的子类（如其他 ISO/IEC 11179 部分所定义的扩展约束）。

## Decisions

### 1. 实体类直接继承 Item
Constraint_Set 与 Registry_Specification 均继承 Item（标准分别指定为 Item 子类），从而复用 Item 的标识、指称、定义与 Slot 扩展能力。
**理由**：与现有 Namespace、Concept 等类保持一致，避免引入新的基类。

### 2. 约束集与注册项采用多对多关联
标准中 registered_item_constraint_set 将 Registered_Item 与 Constraint_Set 绑定（0..* 对 0..1）。项目中将映射为 RegisteredItem 到 ConstraintSet 的 @ManyToOne（即多个注册项可共享同一约束集）。
**理由**：与标准语义一致，且简化存储；约束集本身不持有注册项列表，避免双向循环依赖。

### 3. 参考文档与注册项采用多对多关联
标准中 reference 关联为 Reference_Document 与 Registered_Item 之间的多对多关系。项目中将映射为 RegisteredItem 与 ReferenceDocument 之间的 @ManyToMany 关联表。
**理由**：ReferenceDocument 已在 common-facilities 中定义，复用现有实体。

### 4. Registry_Specification 为单例/独立实体
Registry_Specification 描述整个注册环境，标准未指定与其他注册项的关联。项目将其作为独立资源管理，种子数据创建一条默认记录。
**理由**：避免过度设计；后续若支持多注册环境，可扩展为集合。

### 5. 恢复 APPLICATION 状态但不恢复 CANDIDATE
标准包含 Application 状态；项目历史曾移除 CANDIDATE 与 APPLICATION。仅恢复 APPLICATION，不恢复已明确弃用的 CANDIDATE。
**理由**：CANDIDATE 非 ISO/IEC 11179-3:2023 标准状态，而 APPLICATION 是标准状态之一。

### 6. 属性类型使用现有类型映射
标准中的 Text 映射为 String（与现有 AdministeredItem 一致）；Datetime 映射为 LocalDateTime；Sign 映射为 String；枚举 Degree_of_Conformance 新建 Java 枚举。
**理由**：与项目现有类型约定保持一致，避免与 Hibernate/JPA 类型映射冲突。

## Risks / Trade-offs

| 风险 | 缓解措施 |
|---|---|
| 新增实体导致 Hibernate 自动 DDL 产生大量连接表 | 使用 ddl-auto=update 已启用，新增表可通过启动自动创建；生产环境应使用 Flyway 迁移脚本。 |
| RegistrationStatus 增加 APPLICATION 可能影响现有状态机逻辑 | 在 RegistrationState 转换校验中，将 APPLICATION 视为与 SUBMITTED/RECORDED 同等可转换的合法状态。 |
| Constraint_Set 的布尔约束目前仅存储，无法自动执行 | 在 spec 中明确为存储层合规，自动校验作为后续可选增强。 |
| Registry_Specification 的 default_time_zone 标准定义为 Datetime 但仅使用小时/分钟 | 使用 String 存储偏移字符串（如 -0230），避免 LocalDateTime 语义混淆。 |

## Open Questions

- 是否需要为 Constraint_Set 提供 UI 管理页面？（本 change 暂不提供，后续按需）
- 是否需要在 DataInitializer 中为默认注册机构绑定一个默认 Constraint_Set？（建议绑定一个默认约束集，要求命名、定义、分类）
