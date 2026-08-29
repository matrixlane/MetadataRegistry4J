## Why

当前 `DataElement`、`DataElementConcept`、`ValueDomain`、`ConceptualDomain` 四个核心元数据对象之间的关联关系直接散落在 JPA 实体注解与各 Service/Controller 中，缺乏统一的“属性-定位-关联”操作抽象。随着 ISO/IEC 11179 元模型规则（如数据元必须由数据元概念与值域共同派生、概念域与数据元概念的多对多约束等）不断细化，现有实现导致关联校验、级联维护和跨对象查询的修改点分散，容易遗漏且难以测试。因此需要重构这四个对象的关联操作逻辑，建立统一的元数据对象关系管理与服务层能力。

## What Changes

- 引入统一的**元数据对象关联服务/组件**，封装对象间建立、解除、替换和校验关联的标准流程。
- 将以下关联操作从实体注解下沉/集中到服务层，并补充业务规则校验：
  - `DataElement` ↔ `DataElementConcept`、`ValueDomain`
  - `DataElementConcept` ↔ `ObjectClass`、`Property`、`ConceptualDomain`
  - `ValueDomain` ↔ `DataType`、`UnitOfMeasure`、`DataElement`
  - `ConceptualDomain` ↔ `DataElementConcept`
- 新增关联变更前的**合法性校验**：校验源/目标类型是否匹配、多对多关联是否双向一致；关联目标的存在性由现有 Repository 加载逻辑保证。
- 改造 `metadata-registry-web` 模块中对应的 Controller 与 Service，使其调用新的关联操作 API，避免在 HTTP 层直接操作实体关系。
- 补充并更新四个核心对象的单元测试与集成测试，覆盖正常关联、解除关联、非法关联和级联场景。

## Capabilities

### New Capabilities

- `metadata-object-association`：统一的元数据对象关联操作能力，提供建立、解除、替换、校验对象间关联的标准 API。
- `data-element-relation-validation`：数据元与其数据元概念、值域之间关联关系的业务规则校验。
- `data-element-concept-relation-validation`：数据元概念与对象类、属性、概念域之间关联关系的业务规则校验。

### Modified Capabilities

- 无。本次改造聚焦新增统一的关联操作能力与校验规则，现有能力在 spec 层面的行为保持不变，仅实现方式调整。

## Impact

- 影响模块：`metadata-registry-data-specification`（实体与领域逻辑）、`metadata-registry-web`（Service 与 Controller）、对应测试。
- 数据模型：实体表结构原则上保持不变（关联字段已存在），但关联中间表 `conceptual_domain_dec` 的维护逻辑可能调整。
- API：REST API 行为应保持向后兼容，内部 Service API 会有新增/调整。
- 依赖：需要遵循 `AGENTS.md` 中的数据库可移植性规则，不引入方言专有 SQL；涉及 ISO/IEC 11179 对应关系时需通过 Milvus 查询标准文档作为依据。
