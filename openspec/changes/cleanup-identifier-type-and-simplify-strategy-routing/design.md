## Context

WI-028-B（archive 变更 `2026-06-01-auto-generate-scoped-identifiers`）在 ISO 11179-3:2023 标准模型之外，引入了一套 `IdentifierType` 分类与 `(Namespace, IdentifierType)` 双键路由机制。该机制与 ISO 11179-3 Clause 7（Identification）和 Clause 6.4.6（Slot 扩展机制）的设计哲学不契合：

- ISO 11179-3 没有"标识符类型"作为分类属性。LOINC、SNOMED CT 等代码集在标准中应是 `Namespace`（一套完整标准的命名空间），而非 `IdentifierType` 的一个枚举值。
- 双键路由导致 `IdentifierGenerationStrategyRegistry`、`IdentifierStrategyConfig`、`IdentifierStrategyConfigService`、`SequenceProvider` 等多个核心组件同时承担"按 type 区分"的复杂度，违反了"最小化实体职责"原则。
- 实际使用中，PR-1 之前的代码分析显示 `IdentifierType` 字段几乎全部为默认值 `INTERNAL`，URN 类型仅占少数。双键路由带来的复杂度远超其价值。

本提案在保留 WI-028-B 引入的"自动生成 + 并发控制 + 策略可配置"核心能力基础上，移除 `IdentifierType` 这一非标维度。

## Goals / Non-Goals

**Goals**：
- 删除 `IdentifierType` 枚举及全链路引用
- 简化策略路由从 `(ns, type)` 双键为 `ns` 单键
- 保持自动生成能力、并发安全、可配置策略
- 数据兼容：现有 SI 数据的 `identifier` 字符串值不变
- SQL 迁移可移植（SQL Server / SQLite / 达梦）

**Non-Goals**：
- 重写注册流程的语义（如"内部/外部"判断逻辑、版本生成策略）
- 改动 `Namespace` 模型本身
- 改动 DTO 统一性（留给 PR-5）
- 改动前端 UI（留给 PR-5）
- 重新设计 `IdentifierGenerationStrategy` 接口本身
- 改动 `IdentifierStrategyConfig` 之外的其他配置实体

## Decisions

### 决策 1：枚举全删而非降级为可选

`IdentifierType` 整体删除，不保留为可空字段。理由：
- 简化路由逻辑最直接
- 避免后续"为兼容保留的字段变成新需求被迫启用"
- 现有数据无 `IdentifierType=NULL` 记录（DEFAULT 'INTERNAL'），迁移简单

### 决策 2：保留 `IdentifierStrategyConfig` 的版本化机制

`IdentifierStrategyConfig.version` 字段、`publish()/disable()` 生命周期保留。理由：
- 既有 SI 引用策略快照的能力（通过历史版本）是有价值的
- 简化不应削弱历史可追溯性
- 后续若需要回退到旧策略版本，机制依然完整

### 决策 3：Migration 顺序——先加列后删列模式

对于生产环境，PR-1 采用"两步走" migration（不在本 PR 范围，但设计上预留）：
1. 中间版本：`identifier_type` 标记 deprecated，运行时忽略
2. 清理版本：本 PR 的 migration，删列

**理由**：避免单次 migration 锁表时间过长。但本 PR 只实施"清理版本"——开发环境可以一刀切，生产环境部署时由 DBA 决定是否拆分。

### 决策 4：前端无改动

前端代码经过全面 `grep` 后**没有任何组件引用** `IdentifierType`、`identifierType`、`scopedIdentifier.identifierType` 字段。前端 List 页面用的是 `row.identifier`（来自 DTO 的扁平 `identifier` 字段）和 `row.id`（Entity.id）。

**理由**：本 PR 不影响前端 DTO 结构。前端 DTO 统一留给 PR-5。

### 决策 5：测试同步清理而非保留作废测试

被本 PR 改动的测试类如果不再适用其原测试意图，**重写** 而非删除。理由：
- 删除的测试：纯 `IdentifierType` 枚举行为测试（如 `IdentifierTypeTest`），无保留价值
- 重写的测试：原测试覆盖了策略路由逻辑，只是用例数据需调整（如 `IdentifierGenerationStrategyRegistryTest`）

## 影响面

### 文件改动统计（基于 2026-07-26 grep）

| 类别 | 出现次数 | 文件数 |
|------|----------|--------|
| `IdentifierType` / `identifierType` | 272 | 21 |
| `effectiveStrategyConfig` / `IdentifierStrategyConfig` / `IdentifierGenerationStrategyRegistry` | 135 | 16 |

### 涉及的核心文件

- `metadata-registry-common-facilities/src/main/java/io/github/matrixlane/metadataregistry/commonfacilities/identification/`
  - `IdentifierType.java` —— **删除**
  - `IdentifierStrategyConfig.java` —— 改：去 `identifierType` 字段
  - `IdentifierGenerationStrategyRegistry.java` —— 改：去 type 路由
  - `IdentifierStrategyConfigService.java` —— 改：去 type 参数
  - `IdentifierStrategyConfigService.java` —— 改：去 type
  - `SequenceRegistry.java` —— 改：去 type
  - `SequenceProvider.java` —— 改：去 type
  - `ScopedIdentifier.java` —— 改：去 `identifierType`、`effectiveStrategyConfig` 字段
  - `SequenceGenerationStrategy.java` —— 不变（已不依赖 type）
  - `TemplateGenerationStrategy.java` —— 不变
  - `UrnGenerationStrategy.java` —— 不变
  - `UuidGenerationStrategy.java` —— 不变
- `metadata-registry-common-facilities/src/test/java/.../identification/`
  - `IdentifierTypeTest.java` —— **删除**
  - `ScopedIdentifierTest.java` —— 改：适配新字段集
  - `IdentifierStrategyConfigTest.java` —— 改：去 type
  - `IdentifierStrategyConfigServiceTest.java` —— 改：去 type
  - `IdentifierGenerationStrategyRegistryTest.java` —— 改：去 type
  - `SequenceRegistryTest.java` —— 改：去 type
- `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/`
  - `service/ItemAdministrationService.java` —— 改：去 type 引用
  - `config/IdentifierStrategyLoader.java` —— 改：去 type 路由
  - `config/IdentifierConfig.java` —— 检查是否引用
  - `service/SequenceRegistryService.java` —— 改：去 type 参数
  - `repository/IdentifierStrategyConfigRepository.java` —— 改：查询条件去 type
  - `repository/SequenceRegistryRepository.java` —— 改：查询条件去 type
- `metadata-registry-web/src/main/resources/db/migration/`
  - `sqlserver/V20260527001__Add_identifier_type_and_strategy_tables.sql` —— **保留不动**（历史）
  - `sqlserver/V20260727001__Remove_identifier_type_columns.sql` —— **新增**
  - `sqlite/V20260727001__Remove_identifier_type_columns.sql` —— **新增**
  - `dm/V20260727001__Remove_identifier_type_columns.sql` —— **新增**
- `openspec/changes/archive/2026-06-01-auto-generate-scoped-identifiers/`
  - `proposal.md` / `design.md` / `tasks.md` —— 顶部加注释

## Risks / Trade-offs

- **[Risk] 现有 URN 类型 SI 数据无 type 区分** → **Mitigation**：URN 类型 SI 的 `identifier` 字符串值本身（`urn:xxx:uuid`）保留完整，type 只是元数据，删除不影响解析逻辑。
- **[Risk] `IdentifierStrategyConfig.effectiveStrategyConfig` 删除可能影响历史 SI 的策略追溯** → **Mitigation**：策略版本化机制保留，SI 创建时如果未来需要追溯策略，可通过 `(namespace_id, created_at)` 关联到对应版本的 `IdentifierStrategyConfig`。
- **[Risk] 改动 SQL migration 列结构需要 DBA 协作** → **Mitigation**：迁移脚本幂等（`IF EXISTS` 保护），三方言可执行，文档化回退步骤。
- **[Risk] 多处 service 签名变化导致编译错误** → **Mitigation**：分阶段提交，每个 commit 自包含可编译。

## 后续 PR 关联

- **PR-2**（路线图）：策略路由架构进一步简化（如 `IdentifierGenerationStrategyRegistry` 改为 Spring `@Service` 注入而非单例）
- **PR-3**（路线图）：版本自动生成接入 `IdentifierGenerationStrategy` 体系
- **PR-4**（路线图）：标识编辑模式通过 Namespace Slot 配置
- **PR-5**（路线图）：DTO 统一 `primaryIdentifier` 字段，前端 prop 统一
