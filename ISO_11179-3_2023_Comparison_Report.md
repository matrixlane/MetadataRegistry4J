# ISO/IEC 11179-3:2023 建模对比验证报告

> 对比对象：ISO/IEC 11179-3:2023《信息技术 — 元数据注册系统（MDR）— 第3部分：注册公共设施的元模型》
> 项目：MetadataRegistry4j
> 日期：2026/05/07

---

## 一、总体结论

当前项目的建模与 ISO/IEC 11179-3:2023 的 **核心结构（Core）、标识（Identification）、指称与定义（Designation & Definition）、注册（Registration）** 基本一致，类层次和关联关系实现了标准的主要意图。

但存在 **两个完全缺失的区域**、**若干属性/拼写偏差** 以及 **部分抽象类定义不一致** 的问题。

---

## 二、一致的部分

### 2.1 Core Model（基本与核心）

| 标准类 | 项目类 | 状态 |
|--------|--------|------|
| `Item` | `Item` | 存在，作为所有注册项的基类 |
| `Concept` | `Concept` | 继承 `Item` |
| `Context` | `Context` | 继承 `Item` |
| `Slot` | `Slot` | 独立实体，通过关联扩展 `Item` |

### 2.2 Identification（标识）

| 标准类 | 项目类 | 状态 |
|--------|--------|------|
| `Identified_Item` | `IdentifiedItem` | 作为独立类，通过 `OneToOne` 关联 `Item`（与标准的 `item_identification` 关联语义一致） |
| `Namespace` | `Namespace` | 继承 `Item` |
| `Scoped_Identifier` | `ScopedIdentifier` | 关联 `Namespace` 与 `IdentifiedItem` |

### 2.3 Designation & Definition（指称与定义）

| 标准类 | 项目类 | 状态 |
|--------|--------|------|
| `Designation` | `Designation` | 一致 |
| `Definition` | `Definition` | 一致 |
| `Designation_Context` | `DesignationContext` | 一致 |
| `Definition_Context` | `DefinitionContext` | 一致 |
| `Designation_Definition_Pairing` | `DesignationDefinitionPairing` | 一致 |
| `Acceptability` | `Acceptability` | 枚举值覆盖标准定义 |

### 2.4 Registration（注册）

| 标准类 | 项目类 | 状态 |
|--------|--------|------|
| `Registered_Item` | `RegisteredItem` | 继承 `IdentifiedItem` |
| `Administered_Item` | `AdministeredItem` | 继承 `RegisteredItem` |
| `Attached_Item` | `AttachedItem` | 继承 `RegisteredItem`（与标准一致，是 `Registered_Item` 的互斥子类） |
| `Registration_State` | `RegistrationState` | 作为复合属性/关联实体 |
| `Registration_Status` | `RegistrationStatus` | 枚举值基本覆盖 |
| `Stewardship_Record` | `StewardshipRecord` | 一致 |
| `Submission_Record` | `SubmissionRecord` | 一致 |
| `Registration_Authority` | `RegistrationAuthority` | 继承 `Organization` |
| `Registrar` | `Registrar` | 继承 `Contact` |

### 2.5 Basic Classes（基本类）

| 标准类 | 项目类 | 状态 |
|--------|--------|------|
| `Organization` | `Organization` | 一致 |
| `Individual` | `Individual` | 一致 |
| `Contact` | `Contact` | 一致 |
| `Role` | `Role` | 一致 |
| `Reference_Document` | `ReferenceDocument` | 一致 |
| `Document_Type` | `DocumentType` | 一致 |
| `Language_Identification` | `LanguageIdentification` | 一致 |

### 2.6 Data Specification（数据规范，ISO/IEC 11179-31）

项目中已实现的类与 11179-31 的基本结构对应：

- `DataElement`, `DataElementConcept`, `ValueDomain`, `ConceptualDomain`
- `ObjectClass`, `Property`, `PermissibleValue`, `ValueMeaning`
- `DataType`, `UnitOfMeasure`, `MeasureClass`, `Dimensionality`

---

## 三、不一致与缺失

### 3.1 完全缺失的元模型区域

#### （1）Classification（分类）— 第10章

标准定义了完整的分类元模型，项目中 **没有任何实现**：

- `Classification_Scheme` (10.3.1)
- `Classification_Scheme_Item` (10.3.2)
- `Classification_Scheme_Item_Relationship` (10.3.3)
- `Classification_Scheme_Item_Relationship_Type` (10.3.4)
- 相关关联（`item_classification`, `classification_scheme_membership`, `subject_classification_scheme_item`, `object_classification_scheme_item`, `classification_scheme_item_relationship_categorization` 等）

> **影响**：无法支持对注册项进行分类。

> **注**：标准中不存在 `Classified_Item`、`Subject_Classification`、`Object_Classification` 或 `Classification` 类。分类通过 `item_classification` 关联将 `Item` 与 `Classification_Scheme_Item` 绑定实现。

#### （2）Mapping（映射）— 第11章

标准定义了注册项之间的映射机制，项目中 **没有任何实现**：

- `Item_Mapping`
- `Subject_Mapping`
- `Object_Mapping`
- `Item_Mapping_Degree` 枚举

> **影响**：无法支持注册项之间的语义映射（如“相同于”、“派生于”等关系）。

---

### 3.2 类定义偏差

| 问题 | 标准定义 | 当前实现 | 建议 |
|------|----------|----------|------|
| ✅ `Item` 不是抽象类 | `Item` 是 **abstract**（Page 48: "Item is an abstract superclass"） | `Item` 是具体实体类，可实例化 | 已标记为 `abstract`（WI-006） |
| ✅ `Concept` 不是抽象类 | `Concept` 是 **abstract** | `Concept` 是具体实体类 | 已标记为 `abstract`（WI-006） |
| `Constraint_Set` 缺失 | 标准 9.4.5 定义了 `Constraint_Set`，继承 `Item`，用于注册约束 | 已补充（`ConstraintSet` 实体、`registered_item_constraint_set` 关联、CRUD API、种子数据） | 完成 |
| ✅ `Naming_Convention` 已实现 | 标准 8.4.4 定义了 `Naming_Convention`，继承 `Item`，用于命名规则 | 已补充（`NamingConvention` 实体、标准属性 `scopeRule`/`authorityRule`/`semanticRule`/`syntacticRule`/`lexicalRule`、`rulesJson` 规则引擎、CRUD API、前端页面） | 完成 |
| `Registry_Specification` 缺失 | 标准 9.4.10 定义了 `Registry_Specification`，描述注册环境 | 已补充（`RegistrySpecification` 实体、Table 28 属性、CRUD API、前端页面、种子数据） | 完成 |

---

### 3.3 属性与类型偏差

| 类 | 属性 | 当前实现 | 标准定义 | 偏差程度 |
|----|------|----------|----------|----------|
| `AdministeredItem` | `changeDescription` | `String` | `Text` | 低（Java实现简化） |
| `AdministeredItem` | `explanatoryComment` | `String` | `Text` | 低 |
| `AdministeredItem` | `origin` | `String` | `Text` | 低 |
| `RegistrationState` | `unresolvedIssue` | `String` | `Text` | 低 |
| `RegistrationState` | `administrativeNote` | `String` | `Text` | 低 |
| `ValueDomain` | `maximumCharacterQuantity` | `Integer` | `Integer` | 已修正 |
| `DescribedConceptualDomain` | 类名 | `DescribedConceptualDomain` | `DescribedConceptualDomain` | 已修正 |

---

### 3.4 关系与关联偏差

| 标准关联 | 项目实现 | 状态 |
|----------|----------|------|
| ✅ `Designation` ↔ `Naming_Convention`（`naming_convention_conformance`） | `Designation.namingConventions` 多对多关联，支持每行指称选择多个命名规范 | 已补充 |
| ✅ `Namespace` ↔ `Naming_Convention`（`naming_convention_utilization`） | `Namespace.namingConventions` 多对多关联，并保留历史单引用兼容 | 已补充 |
| ✅ `Namespace` 的约束属性 | `oneNamePerItemIndicator`/`oneItemPerNameIndicator`/`mandatoryNamingConventionIndicator`/`shorthandPrefix`/`schemeReference` 已加入实体与 DTO，并由 `NamespaceConstraintValidator` 执行 | 已补充 |
| `Scoped_Identifier` 的派生属性（`full_expansion`, `shorthand_expansion`） | `ScopedIdentifier` 仅有 `identifier` 和 `version` | 缺失 |
| `RegisteredItem` ↔ `Constraint_Set`（`registered_item_constraint_set`） | `@ManyToOne` 关联（`RegisteredItem.constraintSet`） | 已补充 |

---

### 3.5 数据规范层（11179-31）的细微问题

- `DataElement` 继承 `Item`，但标准中 `Data_Element` 通常应是一个可被注册和管理的项。当前继承关系正确，但如果需要完整的注册能力，应确保可以通过 `AdministeredItem` 进行管理（当前架构允许，因为 `Item` 和 `IdentifiedItem` 是分离的）。
- `PermissibleValue` 继承 `Item`。标准中 `Permissible_Value` 是一个 `designation`，其实现方式可能略有不同，但当前设计可接受。

---

## 四、建议修复清单

### 高优先级

1. **修复拼写错误**：
   - ✅ `DescribledConceptualDomain` → `DescribedConceptualDomain`（已修复）
   - ✅ `maxiumumCharaterQuantity` → `maximumCharacterQuantity`（已修复）

2. **将抽象类标记为 `abstract`**：
   - `Item.java`
   - `Concept.java`

### 中优先级

3. **实现 Classification 区域**：如果项目需要支持对注册项进行分类，需补充 `Classification_Scheme`、`Classification_Scheme_Item`、`Classification_Scheme_Item_Relationship`、`Classification_Scheme_Item_Relationship_Type` 等类，以及 `item_classification` 等关联。

4. **实现 Mapping 区域**：如果项目需要支持注册项之间的语义映射，需补充 `Item_Mapping`, `Subject_Mapping`, `Object_Mapping` 等类。

5. ✅ **补充 `Namespace` 的缺失属性**（已完成）：
   - `oneNamePerItemIndicator`
   - `oneItemPerNameIndicator`
   - `mandatoryNamingConventionIndicator`
   - `shorthandPrefix`
   - `schemeReference`

### 低优先级

6. ✅ **补充 `Constraint_Set`** 和 **`Naming_Convention`** 类（已完成）。
7. ✅ **补充 `Registry_Specification`** 类（已完成）。
8. **将部分 `String` 属性统一为 `Text` 类型**（如 `changeDescription`, `explanatoryComment` 等），以保持与标准的数据类型严格一致。


---

## 五、已补充的实现（2026-07-24 变更 `iso-11179-3-chapter-9-registration-package`）

本次 OpenSpec 变更补齐了 ISO/IEC 11179-3:2023 第9章 Registration package 中缺失的元模型元素与关联：

| 标准元素 | 项目实现 | 状态 |
|---|---|---|
| `Constraint_Set` (9.4.5) | `ConstraintSet` 实体类，继承 `Item`，包含 `itemShallBeDesignatedIndicator`、`itemShallBeDefinedIndicator`、`itemShallBeClassifiedIndicator` | 完成 |
| `Registry_Specification` (9.4.10) | `RegistrySpecification` 实体类，包含 Table 28 全部属性；新增 `DegreeOfConformance` 枚举 | 完成 |
| `reference` association (9.5.2) | `RegisteredItem` 与 `ReferenceDocument` 的 `@ManyToMany` 关联表 | 完成 |
| `registered_item_constraint_set` association (9.5.3) | `RegisteredItem` 与 `ConstraintSet` 的 `@ManyToOne` 关联 | 完成 |
| `Registration_Status` 枚举 | 恢复 `APPLICATION` 状态，覆盖标准 Table 30 中的 `Application` | 完成 |
| REST API | `/api/v1/constraint-sets`、`/api/v1/registry-specifications`、`/api/v1/registered-items/{id}/reference-documents`、`/api/v1/registered-items/{id}/constraint-set` | 完成 |
| 前端 | 新增 `RegistrySpecificationSettings.vue`，补充 `registrySpecificationApi` 与 i18n | 完成 |
| 种子数据 | `DataInitializer` 初始化默认 `ConstraintSet` 与 `RegistrySpecification` | 完成 |
| 测试 | 新增 `ConstraintSetServiceTest`、`RegistrySpecificationServiceTest`、`ConstraintSetControllerTest`、`RegistrySpecificationControllerTest`；扩展 `RegistrationLifecycleServiceTest` 覆盖 `APPLICATION` 状态迁移 | 完成 |

### 说明

- 前端路由 `/registry-specifications` 与菜单“注册规范”此前已存在，但仅指向占位组件；本次变更创建了实际可运行的 `RegistrySpecificationSettings.vue` 并对接后端 API。
- 标准 Table 30 同时包含 `Candidate` 状态，但项目历史变更 WI-019 已明确将其映射为 `SUBMITTED`，本变更未恢复 `CANDIDATE` 枚举值。
- `OpenSpec validate --changes --json` 验证通过。
---

## 六、已补充的实现（2026-07-30 变更 ISO/IEC 11179-3:2023 第8章 Figure 6 关系一致性）

本次 OpenSpec 变更补齐了 ISO/IEC 11179-3:2023 第8章 Designation & Definition 包中缺失的元模型元素、关联与前端交互：

| 标准元素 | 项目实现 | 状态 |
|---|---|---|
| `Naming_Convention` (8.4.4) | `NamingConvention` 实体类，继承 `Item`；新增标准属性 `scopeRule`、`authorityRule`、`semanticRule`、`syntacticRule`、`lexicalRule`；保留 `rulesJson` 规则引擎；提供 CRUD API、前端列表与表单 | 完成 |
| `naming_convention_conformance` | `Designation.namingConventions` `@ManyToMany` 关联表；`MultilingualTextEditor` 每行指称支持多选命名规范 | 完成 |
| `naming_convention_utilization` | `Namespace.namingConventions` `@ManyToMany` 关联表；保留原 `namingConvention` 单引用兼容 | 完成 |
| Namespace 约束属性 | `Namespace` 实体与 DTO 增加 `oneNamePerItemIndicator`、`oneItemPerNameIndicator`、`mandatoryNamingConventionIndicator`、`shorthandPrefix`、`schemeReference`；`NamespaceConstraintValidator` 执行约束 | 完成 |
| 直接可接受性 | `Designation` / `Definition` 实体增加 `Acceptability` 字段；`TextLineResponse` 返回可接受性 | 完成 |
| 关系端点 | `/api/v1/items/{itemId}/designation-definition-relationships` 返回指称、定义、上下文、配对及命名规范 | 完成 |
| 上下文反向浏览 | `ContextService.findUsages` + `/api/v1/contexts/{id}/usages`；`ContextUsageDialog.vue` 按条目分组展示引用 | 完成 |
| 可接受性搜索 | `/metadata/search` 支持 `contextId` 与 `acceptability` 过滤；`BrowserView` 结果行展示 `PREFERRED` 徽章 | 完成 |
| 配对编辑器增强 | `DesignationDefinitionPairingEditor.vue` 必填上下文、重复检测、语言+上下文标签 | 完成 |
| 孤立行与 `PREFERRED` 冲突 | `MultilingualTextEditor` 检测孤立行并支持例外标记；`ItemFormDialog` 阻止保存冲突/未处理孤立行 | 完成 |
| 元数据详情关系页 | `MetadataDetailView.vue` 新增指称、定义、上下文、配对懒加载标签页 | 完成 |

### 说明

- 前端新增 i18n key：`multilingual`、`pairing`、`contextUsage`、`context`、`relationship`、`browser`、`itemForm`。
- 新增单测：`MultilingualTextEditor.spec.ts`、`DesignationDefinitionPairingEditor.spec.ts`、`ContextUsageDialog.spec.ts`。
- `OpenSpec validate --all` 验证通过。

---

## 七、已补充的实现（2026-08-11 变更：指称/定义默认语言与语言标识可选）

本次 OpenSpec 变更依据 ISO/IEC 11179-3:2023 Figure 6 — Designation and Definition metamodel region 的语义，将语言标识与来源文档由“必填”改为“可选”，并引入注册表默认语言自动补全机制：

| 标准/需求点 | 项目实现 | 状态 |
|---|---|---|
| 语言标识可选 | `TextLine.languageId` 移除 `@NotNull`；`DesignationRequest` / `DefinitionRequest` 的 `languageId` 移除 `@NotNull` | 完成 |
| 来源文档可选 | `TextLine.sourceId` 保持可选；前端 `MultilingualTextEditor` 不再强制选择来源文档 | 完成 |
| 注册表默认语言 | `RegistrySpecification.defaultLanguage` 作为默认语言来源；`ItemAdministrationService`、`DesignationService`、`DefinitionService` 新增 `resolveLanguage(UUID languageId)`，未指定语言时自动回退到首个注册规范的默认语言 | 完成 |
| 默认语言全局加载 | 新增 `useRegistryDefaultLanguage()` 组合式函数；`AppLayout.vue` 挂载时加载并缓存，无默认语言或加载失败时向用户显示全局提示 | 完成 |
| 编辑器默认填充 | `DesignationEditor`、`DefinitionEditor`、`MultilingualTextEditor` 支持 `defaultLanguageId` prop，新增行自动填充默认语言；允许用户清空后保存 | 完成 |
| 通用编辑器抽象 | 新增 `DesignationDefinitionSection.vue`，作为 Item 抽象层通用指称/定义编辑区；`NamespaceList`、`ConceptualDomainList`、`ValueDomainList`、`NamingConventionList` 已统一接入 | 完成 |
| 注册规范设置 | `RegistrySpecificationSettings.vue` 增加“默认语言”选择器，支持管理员配置 | 完成 |
| 后端测试 | `ItemAdministrationServiceTest`、`DesignationServiceTest`、`DefinitionServiceTest` 新增默认语言解析与缺失场景测试 | 完成 |
| 前端测试 | 新增 `DesignationEditor.spec.ts`、`DefinitionEditor.spec.ts`，并扩展 `MultilingualTextEditor.spec.ts` 默认语言用例 | 完成 |

### 说明

- 本次变更不改变 `ItemFormDialog.vue` 与 `ContextList.vue` 的现有编辑器选择（仍使用 `MultilingualTextEditor`），符合 OpenSpec design.md 中的 Non-Goals 约定。
- `OpenSpec validate --all` 验证通过；`npx vite build` 与 `npm run test` 通过；`mvnw test -pl metadata-registry-web` 通过。
- 2026-08-12 修复：初始化向导在 `RegistrySpecificationService.createDefault` 中仅将语言标识用于指称、未写入 `RegistrySpecification.defaultLanguage` 字段，导致登录后仍提示无默认语言；已修正为同时写入 `defaultLanguageId`。同时修正 `useRegistryDefaultLanguage()` 对 `registrySpecificationApi.list` 响应结构的解析，使其读取 `defaultLanguageId` / `defaultLanguageIdentifier` 扁平字段而非嵌套对象。
