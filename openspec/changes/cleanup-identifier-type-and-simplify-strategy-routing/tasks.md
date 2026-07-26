## Task Breakdown

### 1. 清理 common-facilities 实体
- [x] 1.1 删除 `IdentifierType.java` 枚举
- [x] 1.2 删除 `ScopedIdentifier.identifierType` 字段（含 Javadoc）
- [x] 1.3 删除 `ScopedIdentifier.effectiveStrategyConfig` 字段（含 Javadoc）
- [x] 1.4 删除 `IdentifierStrategyConfig.identifierType` 字段（含 Javadoc）
- [x] 1.5 更新 `ScopedIdentifier.java` 的 Javadoc，移除 WI-028-B 标识，引用新设计
- [x] 1.6 更新 `IdentifierStrategyConfig.java` 的 Javadoc，移除 WI-028-B 标识

### 2. 简化策略生成器
- [x] 2.1 简化 `IdentifierGenerationStrategyRegistry`：
  - 删除 `EnumMap<IdentifierType, IdentifierStrategyConfig> globalDefaults` → 改为 `IdentifierStrategyConfig globalDefault`
  - 删除 `Map<CompositeKey, IdentifierStrategyConfig> namespaceTypeConfigs` → 删除
  - 删除 `setGlobalDefault(IdentifierType, ...)` → 改为 `setGlobalDefault(IdentifierStrategyConfig)`
  - 删除 `setNamespaceTypeConfig(UUID, IdentifierType, ...)` → 删除
  - 删除 `resolve(Namespace, IdentifierType)` → 改为 `resolve(Namespace)`
  - 删除 `resolveGlobalDefault(IdentifierType)` → 改为 `resolveGlobalDefault()`
  - 删除 `resolveNamespaceTypeConfig(...)` → 删除
  - 删除内部 `CompositeKey` 类
- [x] 2.2 简化 `IdentifierStrategyConfigService`：
  - 改 `generateIdentifier(Namespace, IdentifierType, GenerationContext)` → `generateIdentifier(Namespace, GenerationContext)`
  - 改 `generateSequenceIdentifier(Namespace, IdentifierType, ctx)` → 内部调用，去 type
  - 改 `generateTemplateIdentifier(Namespace, IdentifierType, ctx)` → 内部调用，去 type
  - 改 `resolveStrategyConfig(Namespace, IdentifierType)` → 内部方法，去 type
  - 改 `sequenceProvider.getNextValue(...)` 调用，去 type
- [x] 2.3 简化 `SequenceProvider` 接口：
  - 改 `getNextValue(Namespace, IdentifierType, ...)` → `getNextValue(Namespace, ...)`
- [x] 2.4 简化 `SequenceRegistry` 实体：
  - 删除 `identifierType` 字段
  - 改 `uk_seq_registry_namespace_type` → `uk_seq_registry_namespace`
- [x] 2.5 简化 `SequenceRegistryRepository`：
  - 删除按 `identifier_type` 维度查询的接口（`findByNamespaceIdAndIdentifierType` 等）
  - 改 `existsByNamespaceIdAndIdentifierType` → `existsByNamespaceId`

### 3. 清理 web 层
- [x] 3.1 改 `ItemAdministrationService`：
  - 删除所有 `IdentifierType` 引用
  - 删除 `IdentifierType.INTERNAL` 默认值相关代码
- [x] 3.2 改 `IdentifierStrategyLoader`：
  - 删除按 `identifierType == INTERNAL` 区分 global/namespace 的逻辑
  - 改为：先注册 namespace 特定策略，再注册 global default
- [x] 3.3 改 `SequenceRegistryService`：
  - 改 `getNextValue(Namespace, IdentifierType, ...)` → `getNextValue(Namespace, ...)`
  - 改 SQL 查询：去 `identifier_type` 维度
- [x] 3.4 改 `IdentifierStrategyConfigRepository`：
  - 删除 `findByIdentifierType` 等查询
  - 改 `findAllActiveConfigs()` 返回值结构（去 type 字段）

### 4. SQL 迁移（三种方言）
- [x] 4.1 创建 `sqlserver/V20260727001__Remove_identifier_type_columns.sql`：
  - 幂等保护（`IF COL_LENGTH(...) IS NOT NULL`）
  - `ALTER TABLE scoped_identifier DROP COLUMN identifier_type`
  - `ALTER TABLE scoped_identifier DROP CONSTRAINT fk_scoped_identifier_strategy`
  - `ALTER TABLE scoped_identifier DROP COLUMN effective_strategy_id`
  - `ALTER TABLE identifier_strategy_config DROP CONSTRAINT uk_strategy_namespace_type`
  - `ALTER TABLE identifier_strategy_config ADD CONSTRAINT uk_strategy_namespace UNIQUE (namespace_id, version)`
  - `ALTER TABLE identifier_strategy_config DROP COLUMN identifier_type`
  - `ALTER TABLE identifier_sequence_registry DROP CONSTRAINT uk_seq_registry_namespace_type`
  - `ALTER TABLE identifier_sequence_registry ADD CONSTRAINT uk_seq_registry_namespace UNIQUE (namespace_id)`
  - `ALTER TABLE identifier_sequence_registry DROP COLUMN identifier_type`
- [x] 4.2 创建 `sqlite/V20260727001__Remove_identifier_type_columns.sql`（SQLite 语法不同，需用 `ALTER TABLE ... DROP COLUMN` 1.4+ 支持）
- [x] 4.3 创建 `dm/V20260727001__Remove_identifier_type_columns.sql`（达梦语法）
- [x] 4.4 验证 `db-portability` 规则：JPA 实体注解与 SQL migration 一致
- [ ] 4.5 测试三方言迁移从空库和已有库都能跑通

### 5. 测试清理
- [x] 5.1 删除 `IdentifierTypeTest.java`
- [x] 5.2 重写 `ScopedIdentifierTest.java`：去除 `shouldSetIdentifierType` 等测试，保留 `shouldSetFields`、`shouldSetEffectiveStrategyConfig` 等需重命名
- [x] 5.3 重写 `IdentifierStrategyConfigTest.java`：去除 type 相关测试
- [x] 5.4 重写 `IdentifierStrategyConfigServiceTest.java`：所有 `generateIdentifier(Namespace, IdentifierType, ctx)` 调用改为新签名
- [x] 5.5 重写 `IdentifierGenerationStrategyRegistryTest.java`：所有 `(ns, type)` 双键测试改为 `ns` 单键
- [x] 5.6 重写 `SequenceRegistryTest.java`：去除 type 维度测试
- [x] 5.7 重写 `ItemAdministrationServiceTest.java`：去除 type 引用
- [ ] 5.8 运行 `.\mvnw.cmd test` 确保全绿

### 6. OpenSpec 历史同步
- [x] 6.1 在 `archive/2026-06-01-auto-generate-scoped-identifiers/proposal.md` 顶部加：
  > **Note (2026-07-27)**: `IdentifierType` 概念已在本变更 `cleanup-identifier-type-and-simplify-strategy-routing` 中清理。LOINC/SNOMED CT 等代码集应以 `Namespace` 表达，而非 `IdentifierType` 枚举值。
- [x] 6.2 在 `design.md` 和 `tasks.md` 顶部加同样注释
- [x] 6.3 不动 archive 目录结构（保留历史）

### 7. 文档与索引
- [x] 7.1 在 `docs/domain-modeling.md` 中添加本 PR 的"已清理项"条目（已包含在初版）
- [x] 7.2 验证 `AGENTS.md` 中 `gitnexus` 索引仍然准确（`npx gitnexus analyze`）
- [x] 7.3 验证 `ISO_11179-3_2023_Comparison_Report.md`（如有）无需更新（本 PR 移除的是非标概念）

### 8. 端到端验证
- [ ] 8.1 启动后端服务，验证：
  - 创建 Item：identifier 自动生成（UUID fallback）正常
  - 创建 Item：identifier 手动输入正常
  - 创建 Item：version 自动生成 `1.0.0` 正常
- [ ] 8.2 启动前端，验证：
  - List 页面 `row.identifier` 显示正常
  - 详情页正常
  - 创建/编辑流程无报错
- [ ] 8.3 数据库迁移验证（开发环境）：
  - 旧库升级：新 migration 跑通，旧 SI 的 `identifier` 值不变
  - 新库初始化：所有表创建正常