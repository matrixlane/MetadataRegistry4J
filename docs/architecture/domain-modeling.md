<!-- domain-modeling:start -->
# 领域建模原则（Domain Modeling Principles）

本文档记录本项目在 ISO/IEC 11179-3:2023 元模型基础上的领域建模约定。  
新功能设计、命名、重构前请先通读本文，避免与既有约定冲突。

> 最近复核：2026-09-13，对照 2026-09-01 扩展剥离（`2026-09-01-ext-extraction`）后架构更新 §5–§7。

> 文档定位：AGENTS.md 的领域知识补充。后续如需正式纳入项目根 AGENTS.md，
> 请通过 PR 评审后将内容合并过去，并删除本文件。

---

## 1. 标识符术语（来自 FHIR R4）

| 术语 | 英文 | 映射到本项目 | 出处 |
|------|------|--------------|------|
| **逻辑标识** | Logical ID | `Entity.id`（JPA UUID，技术唯一） | FHIR R4 `Resource.id` |
| **业务标识** | Business Identifier | `ScopedIdentifier.identifier`（业务语义唯一） | FHIR R4 `Resource.identifier` |

**重要约束**：
- 一个 Item 可以有**多个** ScopedIdentifier（不同 Namespace、不同版本），但只有**一个** Entity.id
- Entity.id 是**逻辑标识**，仅在数据库内保证唯一，**不应暴露为业务可见的标识**
- ScopedIdentifier.identifier 是**业务标识**，对用户友好，由数据治理组织制订组成规则
- 这两个是 FHIR 标准里的**正式分类**，不是代称

---

## 2. 组织层级语义

### 2.1 三个"组织"角色的区分

| 角色 | 实体 | 含义 | 是否会变 |
|------|------|------|----------|
| **本注册机构** | `Registration_Authority` (RA) | 本注册表的所有者（如医院、HIS 厂商） | **不变**，永远指向本机构 |
| **元数据管理组织** | `Stewardship_Record.organization` | 元数据真正的"管理组织"，可能是外部组织 | 原创 = RA 本身；外部引入 = 外部组织 |
| **注册用户** | `Account → Individual → Registrar → Registration_Authority` | 在 RA 下工作的具体用户 | 多对多但实践中一对一（详见 §4） |

### 2.2 典型用例：导入 LOINC 实验室临床术语

> 用户（"我"）作为 **HIS 厂商** 的员工，希望在本注册表里维护 LOINC 的整套临床术语标准。
> Namespace 的名称是 **LOINC**，管理组织是 **Regenstrief Institute**。

建模对应：

| 概念 | 实体 | 具体值 |
|------|------|--------|
| Namespace 名称 | `Item.designation.sign` | `"LOINC"` |
| Namespace 管理组织 | `Stewardship_Record.organization` | Regenstrief Institute |
| 本注册机构（RA） | `Registration_Authority` | **HIS 厂商本身**（与 LOINC 无关） |
| 注册用户 | `Account → Individual → Registrar → RA` | HIS 厂商内部员工 |

**关键点**：即便数据来自外部（如 LOINC 由 Regenstrief Institute 管理），本注册表的 **RA 永远是本机构**。
外部管理组织只体现在 `Stewardship_Record.organization`，**不要**误把它塞进 `Namespace.registrationAuthority`。

### 2.3 典型用例：医院导入卫健委数据标准

| 概念 | 实体 | 具体值 |
|------|------|--------|
| Namespace 名称 | `Item.designation.sign` | `"WS/T 某某-2020"`（卫健委标准号） |
| Namespace 管理组织 | `Stewardship_Record.organization` | 卫健委（具体司局） |
| 本注册机构（RA） | `Registration_Authority` | **本医院** |
| 标识生成策略 | Namespace 上的 Slot | `MANUAL_ONLY`（外部标准不可自动生成） |

### 2.4 典型用例：医院原创数据元

| 概念 | 实体 | 具体值 |
|------|------|--------|
| Namespace 名称 | `Item.designation.sign` | `"HOSP-IMPL-2024"`（院内自建命名空间） |
| Namespace 管理组织 | `Stewardship_Record.organization` | 本医院（信息科） |
| 本注册机构（RA） | `Registration_Authority` | **本医院** |
| 标识生成策略 | Namespace 上的 Slot | `AUTO_PREFERRED`（自动生成，必要时可覆盖） |

---

## 3. Namespace 设计原则

### 3.1 Namespace 的本质

Namespace 是**纯命名空间概念**，不承载"内部/外部"判断、也不存放静态业务配置字段。
所有超出标准规范的扩展属性必须通过 §5 描述的 Slot 机制承载。

### 3.2 Namespace 的名称

- Namespace 的"人类可读名称" = `Item.designations[*].sign.value`（多语言指称）
- 至少需要一条 `languageIdentifier = "en-US"` 或 `"zh-CN"` 的指称
- 短码前缀另存于 `Namespace.shorthandPrefix`（如 `"LOINC"` 短码 `"loinc"`）

### 3.3 Namespace 与"标准代码集"的关系

LOINC、SNOMED CT、ICD-10、卫健委发布的数据标准等"标准代码集"在本系统中**都是** Namespace：
- 一个 Namespace = 一套完整的外部标准
- 该 Namespace 下的 ScopedIdentifier 携带具体标准条目的业务标识
- Namespace 的命名约定由数据治理组织在导入时定义

### 3.4 "内部/外部"是相对观察，不入存储

- 某个 Namespace 对某用户是"内部"、对另一用户是"外部"——这是**相对关系**，不存为 Namespace 字段
- 当前**没有功能需要**显式显示或判断这种关系
- 未来如果需要，按 `Account → Individual → Registrar → RA` 与 `Namespace.registrationAuthority` 的相等性推导即可

---

## 4. 用户与注册机构的绑定

```
Account → Individual → Registrar → Registration_Authority
```

- 一个 Account 可关联多个 Registrar（理论）
- **实践中**只考虑 1:1 情况（一个用户只在一个 RA 下工作）
- 未来多 RA 场景作为后续需求处理，不在当前设计范围

---

## 5. 扩展属性约定（卫星实体模式）

### 5.1 演进说明

历史上扩展属性曾约定一律走 Slot（name-value-datatype 三元组）。2026-09-01 扩展剥离（`openspec/changes/2026-09-01-ext-extraction`）后，**治理类扩展改为类型化卫星实体**（`metadata-registry-ext` 模块，EXT schema），Slot 仅保留给真正的临时三元组扩展。

### 5.2 卫星实体（现行约定）

- **形态**：按主实体一一对应或按需挂载，**有意不建 JPA 关联**，经 id 由单点服务读取
- **已落地的卫星实体**：
  - `NamespaceIdentifierSettings` — Namespace 标识符治理（`versioningPolicy` / `identifierInputMode` / `versionInputMode` / `manualIdentifierPattern`）
  - `ScopedIdentifierLifecycle` — ScopedIdentifier 生命周期扩展
  - `ContactAccountBinding` — Contact 账号绑定
  - `LanguageGovernance` — LanguageIdentification 启用治理
  - `NamingConventionRules` — NamingConvention 规则集
- **禁止**在 MDR 标准实体上新增静态治理字段；新增治理扩展**必须**新建卫星实体，不回退到 Slot 散养

### 5.3 Slot（保留机制）

- 标准定义：`ISO/IEC 11179-3:2023, 6.4.6 — Slot`
- 描述：A dynamic extension mechanism for Item, providing name-value-datatype triples
- 代码：`metadata-registry-common-facilities/.../basicandcore/core/Slot.java`（`Slot.value` 为多值 `List<String>`，2026-09-01 按标准收紧）
- 容器：`Item.slots`（`@OneToMany`），`Namespace extends Item` 自动获得
- 仅用于真正的动态三元组；治理/配置类扩展一律走 §5.2 卫星实体

---

## 6. 历史遗留清理（已全部完成）

> 2026-06-01 记录的以下待清理项，经 2026-09-01 扩展剥离全部完成：

- ✅ `IdentifierType` 枚举（INTERNAL/URN/OID/LOINC/SNOMED/CUSTOM）—— 已删除
- ✅ `ScopedIdentifier.identifierType` 字段 —— 已删除
- ✅ `ScopedIdentifier.effectiveStrategyConfig` 字段 —— 已删除
- ✅ `IdentifierGenerationStrategyRegistry` 按 `(namespace, type)` 双键路由 —— 已简化为按 namespace
- ✅ `IdentifierStrategyConfig`（迁 `metadata-registry-ext`）及其 `identifierType` 字段 —— 已删除
- ✅ `identifier_sequence_registry.identifier_type` 字段 —— 已删除

---

## 7. 引用文档

- ISO/IEC 11179-3:2023, Clause 6 (Core Model) — Slot 在 6.4.6
- ISO/IEC 11179-3:2023, Clause 7 (Identification) — ScopedIdentifier 在 Clause 7
- ISO/IEC 11179-6:2023 (Registration) — Registration acting body (RAB) 概念
- FHIR R4 Resource 规范 — Logical ID vs Business Identifier 分类
- 项目内：`docs/audits/iso11179-conformance-audit-2026-09-01.md`（一致性审计基线）、`docs/audits/iso11179-clause11-itemmapping-audit-2026-09-01.md`（第 11 章审计）
<!-- domain-modeling:end -->
