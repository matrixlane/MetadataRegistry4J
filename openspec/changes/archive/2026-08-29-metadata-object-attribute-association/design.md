## Context

当前四个核心元数据对象（`DataElement`、`DataElementConcept`、`ValueDomain`、`ConceptualDomain`）的关联关系通过 JPA 注解直接定义在实体类中：

- `DataElement` 通过 `@ManyToOne` 关联 `DataElementConcept` 与 `ValueDomain`。
- `DataElementConcept` 通过 `@ManyToOne` 关联 `ObjectClass` 与 `Property`，通过 `@ManyToMany` 关联 `ConceptualDomain`。
- `ValueDomain` 通过 `@ManyToOne` 关联 `DataType` 与 `UnitOfMeasure`，通过 `@OneToMany` 反向关联 `DataElement`。
- `ConceptualDomain` 通过 `@ManyToMany` 反向关联 `DataElementConcept`。

业务代码（Service/Controller）直接调用 setter 或 Repository 操作这些关联，导致：
- 关联操作（如数据元与概念/值域的绑定、数据元概念与对象类/属性的绑定）直接通过实体 setter 完成，关联规则与级联维护散落在各 Service 中，难以复用和统一测试。
- 解除、替换关联时没有统一入口，容易出现级联遗漏或脏数据。
- 新增元模型规则时修改点分散。

## Goals / Non-Goals

**Goals：**
- 建立统一的元数据对象关联操作抽象，覆盖建立、解除、替换和校验四种基本操作。
- 将 ISO/IEC 11179 中关于数据元、数据元概念、值域、概念域的关联约束集中到校验器中。
- 在不改变数据库表结构的前提下，改造现有 Service/Controller，使其通过新的关联操作 API 处理关系变更。
- 为四个核心对象提供可独立运行的关联关系单元测试。

**Non-Goals：**
- 不修改现有 REST API 的 URL 与请求/响应契约（保持向后兼容）。
- 不引入新的持久化表或修改现有表结构。
- 不改造非这四个对象之外的其它元数据对象关联。
- 不替代 JPA 作为持久化机制，仅在 JPA 实体之上增加业务层封装。

## Decisions

### 1. 引入 `MetadataObjectAssociationService` 统一处理关联操作
- **选择**：在 `metadata-registry-data-specification` 模块中新增领域服务 `MetadataObjectAssociationService`，对外暴露 `associate`、`dissociate`、`replace`、`validate` 方法。
- **理由**：将关联操作从 HTTP 层和实体 setter 中抽离，使关联逻辑可独立测试、复用和扩展。
- **替代方案**：在每个对象 Service 中各自维护关联方法。 rejected，因为无法解决分散问题。

### 2. 校验规则通过策略模式或独立校验器实现
- **选择**：为每种关联关系定义独立校验器（如 `DataElementAssociationValidator`、`DataElementConceptAssociationValidator`），由 `MetadataObjectAssociationService` 在执行前调用。
- **理由**：不同关联对的约束不同（如数据元需同时具有概念和值域，数据元概念需同时具有对象类和属性），独立校验器便于单独扩展和单元测试。
- **替代方案**：在 Service 中写死 if/else。 rejected，因为难以维护和测试。

### 3. 保持现有 JPA 实体与数据库表结构不变
- **选择**：实体类中的 `@ManyToOne`、`@ManyToMany` 等字段保留，仅通过 Service 层封装对这些字段的修改。
- **理由**：减少迁移风险，避免数据库迁移脚本变更，符合 `AGENTS.md` 中“实体类不得硬编码 schema”等可移植性要求。
- **替代方案**：重构为多对多中间实体或引入关联表。 rejected，因为超出本次范围并引入 schema 变更。

### 4. 改造现有 Controller/Service 按能力逐个替换
- **选择**：按能力分阶段改造：先完成 `MetadataObjectAssociationService` 与校验器，再逐个替换 `DataElementService`、`DataElementConceptService`、`ValueDomainService`、`ConceptualDomainService` 中的关联逻辑。
- **理由**：降低一次性改动范围，便于逐步回归测试。
- **替代方案**：一次性全量替换。 rejected，因为风险过高。

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|---------|
| 现有测试用例依赖旧 Service 的关联实现方式，改造后可能失效。 | 改造前梳理并补充现有测试；每个 Service 替换后单独运行对应测试套件。 |
| 统一服务粒度设计过粗，未来难以支持特殊关联逻辑。 | 采用策略模式，校验器与操作可针对具体关联对扩展；通用方法仅负责流程编排。 |
| Controller 直接暴露实体对象，统一服务内部修改可能意外影响序列化。 | 保持实体字段不变，仅调整业务操作入口；API 契约不变。 |
| 与 ISO/IEC 11179 标准对应关系需要持续对齐。 | 实现前通过 Milvus 检索 ISO 11179-3 相关章节作为依据，并在代码注释或文档中标注。 |

## Migration Plan

1. 在 `metadata-registry-data-specification` 中新增 `MetadataObjectAssociationService` 与关联校验器接口/实现。
2. 为 `DataElement`、`DataElementConcept` 的关联校验编写单元测试。
3. 逐个替换四个 Service 中的关联操作：
   - `DataElementService`
   - `DataElementConceptService`
   - `ValueDomainService`
   - `ConceptualDomainService`
4. 运行对应 Controller/Service 测试，确认 API 行为不变。
5. 如有需要，更新 `ARCHITECTURE.md` 或模块内 README 中关于关联关系的说明。
6. 合并 `feature/refactor-metadata-object-relations` 分支。

## Open Questions

- 是否需要支持批量关联/解除关联操作（如一次为多个数据元指定同一值域）？
- 关联变更是否需要触发审计事件或注册状态变更？目前按业务现状假设不需要。
- `ConceptualDomain` 与 `DataElementConcept` 的多对多关系是否允许双向维护，还是统一由某一方主导？需要结合现有 UI 与业务确认。
