---
status: draft
type: feature
author: Codex
created: 2026-07-24
updated: 2026-07-24
---

# OpenSpec/PR 提案：Namespace 与 ISO/IEC 11179-3:2023 对齐

## 背景

ISO/IEC 11179-3:2023 的 **Identification（第7章）** 与 **Designation & Definition（第8章）** 将 `Namespace` 定义为 `Identified_Item` 的关键作用域容器，并规定了命名空间治理属性、命名规范（Naming_Convention）以及注册机构（Registration_Authority）之间的关联。当前项目 `MetadataRegistry4j` 的 `Namespace` 实现缺少若干关键属性，`Naming_Convention` 完全缺失，导致无法按标准表达命名空间规则、命名冲突约束以及注册机构命名空间。

本提案在 Milvus 优先检索标准文档不可得的情况下，依据本地 ISO/IEC 11179-3:2023 对比报告（`ISO_11179-3_2023_Comparison_Report.md`）及源码现状，补齐差距并生成可执行的 OpenSpec 变更。

## 目标

1. 使 `Namespace` 实体的属性与 ISO/IEC 11179-3:2023 中 `Namespace` 的元模型定义对齐。
2. 移除项目中自行添加、标准中不存在的 `NamespaceStatus` 生命周期概念，统一使用 `AdministeredItem → RegistrationState` 表达注册状态。
3. 保留并澄清 `Namespace.registrationAuthority` 与 `RegistrationAuthority.namespace` 的双向可选（0..1）关联。
4. 引入 `Naming_Convention` 管理，并提供独立 REST 端点。
5. 在应用服务层实现 Namespace 三大治理约束的强制校验：
   - `one_name_per_item_indicator`
   - `one_item_per_name_indicator`
   - `mandatory_naming_convention_indicator`

## 范围

**包含：**
- `metadata-registry-common-facilities`：实体模型变更（`Namespace`、`NamingConvention`、`RegistrationAuthority`）。
- `metadata-registry-web`：DTO、Repository、Service、Controller、Flyway 迁移、单元/集成测试。
- OpenSpec 设计文档与任务拆分。

**不包含：**
- 前端 UI 字段新增（可选后续任务）。
- 与本次 Namespace 改动无关的既有测试失败修复。

## 关键决策

### 1. NamespaceStatus 的去留
标准中不存在 `NamespaceStatus`。项目中原先自行引入的 `NamespaceStatus` 枚举、`NamespaceStateMachine` 以及 DTO 中的 `namespaceStatus` 字段全部移除，避免与 `AdministeredItem` 的注册状态机产生两套并行的生命周期语义。

### 2. Namespace ↔ RegistrationAuthority 双向可选关联
ISO/IEC 11179-3:2023 中 `registration_authority_namespace` 关联允许 `Namespace` 承载某个 `RegistrationAuthority` 的标识符作用域，同时 `RegistrationAuthority` 也可通过反向引用访问其命名空间。两个方向均设为 `0..1`，即：
- `Namespace.registrationAuthority` 为可选 `@OneToOne`。
- `RegistrationAuthority.namespace` 为 `mappedBy` 的反向 `@OneToOne`，同样可选。

### 3. namingAuthority 与 RegistrationAuthority 的区分
- `namingAuthority`（`Organization`）：对命名空间内的命名具有命名权的机构，标准属性名为 `naming_authority`。
- `registrationAuthority`（`RegistrationAuthority`）：对注册项具有注册管理权的机构，通过 `registration_authority_namespace` 关联。
- 二者语义不同，分别对应标准中的不同角色，必须同时保留。

### 4. NamingConvention 作为独立 Administered Item
`Naming_Convention` 在标准中继承自 `Item`，因此走完整的 `Item` 生命周期，提供独立 REST 端点 `/api/v1/naming-conventions`，支持标识、注册、命名、定义和分类。

## 依赖

- 现有 `Item`、`IdentifiedItem`、`AdministeredItem`、`ScopedIdentifier`、`Designation`、`RegistrationAuthority` 实体。
- Flyway 迁移框架。
- 现有 `NamingConflictDetector` 需扩展以支持 `ScopedIdentifier` 级别的标识符冲突检测。

## 任务拆分

见 [tasks.md](tasks.md)。

## 设计文档

见 [design.md](design.md) 及以下详细规格：
- [namespace-iso11179-model](specs/namespace-iso11179-model/spec.md)
- [namespace-constraint-enforcement](specs/namespace-constraint-enforcement/spec.md)
- [naming-convention-management](specs/naming-convention-management/spec.md)

## 验收标准

1. `Namespace` 实体包含 `shorthandPrefix`、`schemeReference`、`oneNamePerItemIndicator`、`oneItemPerNameIndicator`、`mandatoryNamingConventionIndicator`、`namingAuthority`、`registrationAuthority`、`parent`、`namingConventions`。
2. `NamespaceStatus` 枚举、`NamespaceStateMachine`、DTO 与数据库中的 `namespace_status` 字段全部移除。
3. `NamingConvention` 实体、Service、Controller、Repository 存在，端点 `/api/v1/naming-conventions` 可用。
4. `NamespaceConstraintValidator` 在 `NamespaceService`、`DesignationService`、`ScopedIdentifierService` 中被调用，并通过单元测试。
5. 相关 Flyway 迁移可顺利执行，单元/集成测试通过。

## 备注

- Milvus 向量检索当前因集合缺少 sparse 向量字段无法使用，后续如要重新检索标准原文，需先修复 Milvus schema 或回退到本地 PDF/对比报告。
- 前端如需输入 Namespace 治理属性，建议另开独立任务处理 UI 字段。
