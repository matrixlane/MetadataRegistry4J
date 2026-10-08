# Design: remove-system-default-namespace

## Context

见 `proposal.md - Why` 与 `docs/superpowers/specs/2026-10-04-remove-system-default-namespace-design.md`（已确认的设计稿，含完整变更清单）。

当前状态要点：`sys:` 命名空间由 `SystemDefaultNamespaceInitializer` 预置（启动期 / 恢复出厂 / 向导 setup 与 abort 兜底，经 `StaticDataPresetService.presetAll()` 统一编排），`scheme_reference = 'urn:metadata-registry:system-default'`、`shorthand_prefix = 'sys:'`；存量库中该行存在 designation 子行（中英双指称）且 NamingAuthority 挂接注册机构；`scoped_identifier.namespace_id` 对其零引用（实库核实）。遗留系统组织（固定 UUID `…0001`）的启动调和删除逻辑寄居在初始化器内。

## Goals / Non-Goals

**Goals:**

- 代码、预置编排、门槛/推导逻辑、前端、测试中彻底移除 system-default 命名空间概念。
- 三方言存量数据一次性清理，迁移幂等、可重入、带防御检查。
- OpenSpec 活跃 spec 与实现行为重新对齐。

**Non-Goals:**

- 不新增"命名空间自身被识别/发标识"能力（未来若需要，按"上一级命名空间"另行立项）。
- 不动 Registry Context（承载于注册机构自有命名空间）与外部命名空间机制。
- 不改任何 API 契约；不提交前端 dist 构建产物。

## Decisions

### D1：按 scheme_reference 定位存量 sys: 行，而非 shorthand_prefix

scheme 值 `urn:metadata-registry:system-default` 是稳定的识别特征；`shorthand_prefix` 标准上可空可改，不宜作为定位依据。迁移以 `scheme_reference = 'urn:metadata-registry:system-default'` 查找目标行。

### D2：迁移带 ScopedIdentifier 引用防御检查

若目标命名空间被 `scoped_identifier` 引用（存量库理论上不存在，但防止误删业务标识），跳过该命名空间的删除。跳过的残留行不再被代码特判，按"无关联注册机构"走外部命名空间路径，无破坏性。

### D3：删除顺序先子后父

`designation`（含 `designation_context_relation` 等以 designation 为外键的关联表，按实库外键确认）→ `namespace_naming_convention` → `namespace` 本体。遗留系统组织在 sys: 行删除后检查引用（`namespace.naming_authority_id`、`contact.organization_id`），无引用则删 `organization_name/email/phone` 及 `organization` 行。

### D4：向导状态启发式不改动代码，仅恢复语义

`getWizardState()` 以 `namespaceRepository.count()` 判 IN_PROGRESS；sys: 预置移除后该启发式自然恢复正确（nsCount=0 即 READY），无需代码变更，仅作为验收点验证。

### D5：OrganizationService 系统组织保护整体移除

系统组织自 2026-10 起已不预置，其存量清理职责由本次迁移接管（替代原启动调和）。`OrganizationService` 的列表过滤与删除保护随常量一并删除；存量库若存在被引用的系统组织行（非 sys: 场景），迁移跳过清理，行保留但不再享受特殊保护——与"系统组织不是产品概念"的裁定一致。

### D6：测试改造策略

`SystemDefaultNamespaceInitializerTest` 整文件删除；`NamespaceGateIntegrationTest` 的 system-default 用例删除（无 RA 路径已由外部命名空间用例覆盖）；`ItemAdministrationServiceTest` 中 scheme=system-default 的用例改为验证"无 RA 命名空间按外部命名空间处理"，finder mock 切换到 `findFirstByRegistrationAuthority_Id`；`InitWizardServiceIntegrationTest` 的 sys: 挂接断言改为"setup 后 namespace 表仅含向导创建的命名空间"。

## Risks / Trade-offs

- [存量库 sys: 被手工用于存放条目] → D2 防御检查：有引用则跳过；残留行走外部命名空间路径，无特判无破坏。
- [designation 关联表外键删除顺序错误导致迁移失败] → 实施任务中先按实库/JPA 映射确认引用方向，迁移在 Flyway 单事务内执行，失败整体回滚。
- [SQLite UUID 存储为 BLOB 与 SQL Server/达梦字面量差异] → 迁移脚本按各方言既有迁移的写法编写（参考 V20260929001 等三方言同构脚本）。
- [跳过删除的残留 sys: 行在前端列表可见] → 可接受：与外部命名空间同等展示，且仅影响被手工引用过的存量库。

## Migration Plan

1. 合入代码与三方言迁移脚本。
2. 应用启动时 Flyway 自动执行 `V20261004001`（单事务，条件删除天然幂等）。
3. 验证：存量库 `namespace` 表无 system-default 行；全新库向导初始化后 `namespace` 表恰好 1 行。
4. 回滚：恢复出厂设置或重建库；迁移自身不回滚数据（Flyway undo 不支持的项目惯例）。
