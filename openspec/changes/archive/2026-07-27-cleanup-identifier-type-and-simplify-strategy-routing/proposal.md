---
status: draft
type: cleanup
author: Mavis
created: 2026-07-27
updated: 2026-07-27
---

# OpenSpec 提案：清理 IdentifierType 并简化策略路由

## Why

2026-06-01 的归档变更 `2026-06-01-auto-generate-scoped-identifiers` 引入了 `IdentifierType` 枚举与 `(Namespace, IdentifierType)` 双键路由，但 `IdentifierType` 并非 ISO/IEC 11179-3:2023 标准概念。LOINC、SNOMED CT 等外部代码集本身即是 `Namespace`，不应作为标识符类型枚举值；多键路由与 `ScopedIdentifier.effectiveStrategyConfig` 字段也引入了不必要的复杂度。

## What Changes

- 删除 `IdentifierType` 枚举及其全部引用。
- 简化 `IdentifierGenerationStrategyRegistry`：从 `(namespace, type)` 双键路由简化为 `namespace` 单键。
- 简化 `IdentifierStrategyConfig`、`identifier_sequence_registry` 表结构：去掉 `identifier_type` 列。
- 简化 `ScopedIdentifier` 实体：去掉 `identifierType` 与 `effectiveStrategyConfig` 字段。
- 新增三方言 Flyway 迁移 `V20260727001__Remove_identifier_type_columns.sql`，平滑移除相关列与约束。
- 保持自动生成、并发控制、版本管理等既有功能可用，且保留既有 ScopedIdentifier 的 `identifier` 值。

## 背景

2026-06-01 的归档变更 `2026-06-01-auto-generate-scoped-identifiers`（WI-028-B）为支持"按类型路由的自动生成策略"，引入了 `IdentifierType` 枚举（`INTERNAL/URN/OID/LOINC/SNOMED/CUSTOM`）和 `(Namespace, IdentifierType)` 双键路由。

经过对 ISO/IEC 11179-3:2023 标准的复核和实际使用情况分析，本提案认为：

1. **`IdentifierType` 不是 ISO 11179-3 标准概念**。标准中不存在"标识符类型"作为分类属性。
2. **LOINC、SNOMED CT 等不是 identifierType**，它们本身就是 `Namespace`——一套完整的外部标准代码集。
3. **多键路由（ns + type）引入不必要的复杂度**。一个 Namespace 的策略应与 Namespace 绑定，不需要按 type 再分一次。
4. **`ScopedIdentifier.effectiveStrategyConfig` 字段冗余**。策略已由 Namespace 级别配置承载，scopedIdentifier 引用其创建时的策略快照价值有限。

## 目标

1. **删除** `IdentifierType` 枚举及其所有引用。
2. **简化** `IdentifierGenerationStrategyRegistry` 路由逻辑：从 `(namespace, type)` 双键简化为 `namespace` 单键。
3. **简化** `IdentifierStrategyConfig`、`identifier_sequence_registry` 表结构：去掉 `identifier_type` 列。
4. **简化** `ScopedIdentifier` 实体：去掉 `identifierType` 和 `effectiveStrategyConfig` 字段。
5. **保持**所有已有功能可用，包括自动生成、并发控制、版本管理。
6. **数据兼容**：现有生产数据（`identifier_type='URN'` 等）平滑迁移，不破坏既有 SI 记录的 `identifier` 值。

## 范围

**包含**：
- `metadata-registry-common-facilities`：实体类、Service 接口、策略类
- `metadata-registry-web`：Service、Controller、Repository、SQL migration
- 测试：相关单测和集成测
- SQL 迁移：SQL Server、SQLite、达梦 三方言

**不包含**（后续 PR）：
- 前端 UI 改动（PR-5）
- DTO 统一 `primaryIdentifier` 字段（PR-5）
- 标识编辑模式通过 Slot 配置（PR-4）
- 版本自动生成（PR-3）
- 策略路由架构进一步简化（PR-2）

## 关键决策

### 1. 删除 `IdentifierType` 枚举及其全链路引用
- 枚举本身：`common-facilities/.../identification/IdentifierType.java`
- 引用点：21 个文件，272 处出现
- 引用方：`ScopedIdentifier`、`IdentifierStrategyConfig`、`IdentifierGenerationStrategyRegistry`、`IdentifierStrategyLoader`、`IdentifierStrategyConfigService`、`SequenceProvider`、`ItemAdministrationService` 等

### 2. `IdentifierGenerationStrategyRegistry` 路由逻辑简化
- **改前**：`resolve(Namespace, IdentifierType)` 按 (ns, type) 双键查 → ns+type 特定 → ns 默认 → global default per type
- **改后**：`resolve(Namespace)` 按 ns 查 → ns 特定策略 → global default
- 保留内置策略池：UUID、URN、SEQUENCE、TEMPLATE
- 保留 `(nsId, type)` 缓存结构 → 简化为 `nsId` 缓存

### 3. `SequenceProvider` 接口去 type 参数
- **改前**：`getNextValue(Namespace, IdentifierType, long start, int increment, int pad, String prefix)`
- **改后**：`getNextValue(Namespace, long start, int increment, int pad, String prefix)`
- `SequenceRegistry` 表的 `identifier_type` 列删除
- `identifier_sequence_registry` 的 `(namespace_id, identifier_type)` 唯一约束改为 `namespace_id` 唯一约束

### 4. `ScopedIdentifier` 实体简化
- 删除字段：`identifierType: IdentifierType`（默认值 `INTERNAL`）
- 删除字段：`effectiveStrategyConfig: IdentifierStrategyConfig`（ManyToOne）
- 删除列：`identifier_type VARCHAR(32) NOT NULL DEFAULT 'INTERNAL'`
- 删除列：`effective_strategy_id BIGINT`（外键到 `identifier_strategy_config`）
- 删除外键约束：`fk_scoped_identifier_strategy`

### 5. `IdentifierStrategyConfig` 实体简化
- 删除字段：`identifierType: IdentifierType`
- 删除列：`identifier_type VARCHAR(32) NOT NULL DEFAULT 'INTERNAL'`
- 唯一约束 `uk_strategy_namespace_type` 简化为 `uk_strategy_namespace`

### 6. SQL 迁移策略
- **不修改既有 migration**（`V20260527001__Add_identifier_type_and_strategy_tables.sql`），保留作为历史
- **新增 migration** `V20260727001__Remove_identifier_type_columns.sql`（三方言）：
  - `ALTER TABLE scoped_identifier DROP COLUMN identifier_type`
  - `ALTER TABLE scoped_identifier DROP COLUMN effective_strategy_id` + `DROP CONSTRAINT fk_scoped_identifier_strategy`
  - `ALTER TABLE identifier_strategy_config DROP COLUMN identifier_type`
  - `DROP INDEX uk_strategy_namespace_type` → 改为 `uk_strategy_namespace`
  - `ALTER TABLE identifier_sequence_registry DROP COLUMN identifier_type`
  - `DROP INDEX uk_seq_registry_namespace_type` → 改为 `uk_seq_registry_namespace`
  - 数据迁移：现有 `identifier_type='URN'` 的 SI 保留 `identifier` 字符串（值不变），但不再有 type 区分

### 7. 测试清理
- 删除 `IdentifierTypeTest.java`
- 删除 `IdentifierStrategyConfigTest.java`（重写以适配无 type 字段）
- 删除 `IdentifierStrategyConfigServiceTest.java`（重写）
- 删除 `IdentifierGenerationStrategyRegistryTest.java`（重写）
- 删除 `IdentifierSequenceRegistryTest.java`（重写）
- `ScopedIdentifierTest.java` 适配新字段集

### 8. OpenSpec 历史同步
- 保留 `archive/2026-06-01-auto-generate-scoped-identifiers` 不动
- 在该 archive 的 `proposal.md` / `design.md` / `tasks.md` 顶部加一行注释，提示"identifierType 概念已于 2026-07-27 在 `cleanup-identifier-type-and-simplify-strategy-routing` 中清理"

## 任务

详见 [tasks.md](tasks.md)

## 设计文档

详见 [design.md](design.md)

## 能力规约

详见 [specs/identification/spec.md](specs/identification/spec.md)

## 验收标准

1. 项目 Maven 编译通过：`.\mvnw.cmd clean compile -DskipTests`
2. 所有单元/集成测试通过：`.\mvnw.cmd test`
3. SQL 迁移在三种方言（SQL Server、SQLite、达梦）下可执行，无报错
4. 现有 SI 记录的 `identifier` 值保持不变（数据迁移只删列，不改值）
5. 全局默认策略 `strategy_type='UUID'` 仍生效，业务侧无感
6. `IdentifierType` 枚举在代码中无任何引用（`grep -r 'IdentifierType' --include='*.java'` 仅命中 `IdentifierType.java` 删除文件本身，0 处）
7. 归档变更 `archive/2026-06-01-auto-generate-scoped-identifiers` 顶部添加清理指向注释