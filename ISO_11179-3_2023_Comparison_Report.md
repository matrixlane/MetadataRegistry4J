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
| `Naming_Convention` 缺失 | 标准 8.4.4 定义了 `Naming_Convention`，继承 `Item`，用于命名规则 | 未实现 | 补充实现 |
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
| `Designation` ↔ `Naming_Convention`（`naming_convention_conformance`） | 无关联 | 缺失 |
| `Namespace` ↔ `Naming_Convention`（`naming_convention_utilization`） | 无关联 | 缺失 |
| `Namespace` 的约束属性（`one_name_per_item_indicator`, `one_item_per_name_indicator`, `mandatory_naming_convention_indicator`, `shorthand_prefix`, `scheme_reference`） | `Namespace` 仅包含 `scopedIdentifiers` 和 `namingAuthority`，缺少上述属性 | 缺失 |
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

5. **补充 `Namespace` 的缺失属性**：
   - `oneNamePerItemIndicator`
   - `oneItemPerNameIndicator`
   - `mandatoryNamingConventionIndicator`
   - `shorthandPrefix`
   - `schemeReference`

### 低优先级

6. **补充 `Constraint_Set`** 和 **`Naming_Convention`** 类。
7. **补充 `Registry_Specification`** 类。
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

