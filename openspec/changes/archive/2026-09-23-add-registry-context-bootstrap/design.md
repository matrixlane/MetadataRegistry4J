# Design: add-registry-context-bootstrap

## Context

- `Context extends Item`（ISO/IEC 11179-3:2023 §6.4.2.3），`Entity.id` 为 `@GeneratedValue(UUID)`，瞬态实体无 id，但可 `setId` 指定固定 UUID。
- 自指机制已存在：`ItemAdministrationService.SELF_CONTEXT_REFERENCE`（nil UUID），`loadContext(item, id)` 对 Context 条目把哨兵解析为条目自身（`ItemAdministrationService.java:847-871`）；非 Context 条目使用哨兵会被拒绝。
- 标识/版本 AUTO 生成链在 `ensureAdministeredItem` 内部：`IdentifierInputPolicyService.resolveIdentifier`（AUTO_ONLY 拒显式值、MANUAL_ONLY 拒空值、AUTO_WITH_OVERRIDE 皆可）→ 段模板引擎 → `SequenceRegistryService` 取号（Context 缩写 `CTX`）；`resolveVersion` 按 versioningPolicy 给初版（SEMVER→`1.0.0`）。
- `ensureAdministeredItem(item, req, explicitAuthority)` 支持显式归属 RA（向导阶段无登录用户，`GenericItemCreator` 已有同构先例）。
- `InitWizardService.setup()` 的 `clearBusinessDataOnly()` 清空全部业务表（含 context、identifier_sequence_registry）；随后创建 RA → 自有命名空间（`ra.setNamespace(nsSaved)` 已保存）→ 段模板策略 `refreshRegistry()` → `createDefault` 注册规范。
- `scoped_identifier` 唯一约束 `(namespace_id, identifier)`。

详见 `docs/superpowers/specs/2026-09-23-registry-context-bootstrap-design.md`（v4，已确认）。

## Goals / Non-Goals

**Goals:**

- 向导 setup 末尾（标识/版本规则就绪后）幂等创建 Registry Context：已注册条目、归属 RA、命名空间 = `ra.getNamespace()`、标识/版本由策略产出（MANUAL_ONLY 固定字符串 `registry-context`/`1.0`）。
- 自动填写自指的中/英指称与定义（忠实标准原文）。
- 回填 `RegistrySpecification.context`；既有已初始化库启动补建。
- 全程幂等；`abort`/恢复出厂后由向导重跑自然重建。

**Non-Goals:**

- 不改 `ContextService` / `ItemAdministrationService` / 实体模型 / 前端。
- 零 schema 变更、零迁移脚本；不给 Registry Context 之外的任何条目建种子。
- 不在系统未初始化时创建 Registry Context。

## Decisions

1. **核心逻辑放 `RegistryContextBootstrapService`（service 层），而非纯 seed Runner**：向导（service 层）、启动 Runner 两个调用方都要复用；命名空间由 `ensureRegistryContext(RegistrationAuthority)` 签名内部解析 `ra.getNamespace()`，为 null 抛 `IllegalStateException`——从 API 层强制"RA 自有命名空间"，杜绝 `sys:` 误用。替代方案：传 `(ns, ra)` 双参，约束只靠约定，被否。
2. **标识/版本传 null 走 `ensureAdministeredItem` AUTO 链**：与 UI AUTO 注册行为完全一致，复用 `ScopedIdentifierLifecycle` 的 AUTO 标记。仅 `identifierInputMode == MANUAL_ONLY` 时显式传 `registry-context`/`1.0`（固定值是普通字符串，仅此时可用；AUTO 失败不静默回退）。替代方案：直接调 `IdentifierStrategyConfigService.generateIdentifier` 手工组装 ScopedIdentifier（`GenericItemCreator` 做法），需要重复 ensureAdministeredItem 的归属/生命周期/状态逻辑，被否。
3. **条目主键固定 UUID `00000000-...-0002` 仅作幂等键/系统级引用**（spec.context 外键目标）：标识符本身不固定（见决策 2）。`...0000`=SELF 哨兵、`...0001`=system 组织已占用。
4. **创建序列与 `ContextService.create` 对齐**：`applyDesignations(entity, lines, ns.getId())` → `applyDefinitions` → `ensureAdministeredItem(entity, req, ra)` → `repository.save` → `recordSubmission(entity, req)`。自指通过 `TextLineContextEntry(SELF_CONTEXT_REFERENCE, PREFERRED)` 表达（瞬态实体无 id，哨兵是唯一可行路径）。
5. **启动补建 Runner 不挂 `app.seed.enabled`**：Registry Context 是系统固有补全数据而非测试种子；Runner 内先查 `systemSettingService.isInitialized()`，未初始化直接返回（时序约束）。`ContextTestDataInitializer` 补 `@Order(100)`、Runner `@Order(101)`，保证"表非空即跳过"的测试种子不被抑制。
6. **向导内失败降级**：`InitWizardService.setup()` 中调用包 try/catch，失败仅 log warn 不阻断向导；重启后补建路径兜底。`FactoryResetService`/`abort()` 不改动。
7. **默认命名规范正则允许空格（方案 1，用户确认）**：实现中发现 `applyDesignations` 对所有指称无条件执行默认规则集（长度+字符集+正则），HEAD 版默认正则禁止空格，与"默认字符集规则本就允许空格"自相矛盾，且会使含空格的 Registry Context 指称无法创建。经核对 ISO/IEC 11179-3:2023（6.2.10/3.2.10：指称符号无字符构成强制限制；3.2.22：格式约束属可选命名约定），选择放宽默认正则（加入 `\s`）而非种子绕开校验（方案 2）。**权衡**：全局指称默认允许空格（校验口径变化，已获用户确认）；若未来需收紧，应通过命名空间级命名约定实现。
8. **创建点在向导 commit 事务提交后，由 Controller 组合独立调用（冒烟暴露后两次修正）**：①放 setup() 内层 catch 挡不住事务污染——内层失败把外层标记 rollback-only，整个 setup 回滚；②改 REQUIRES_NEW 又撞 SQLite **单连接池**（total=1）死锁，且 setup 事务内未提交的数据对独立事务不可见。最终：`InitWizardController.commit` 先调 `initWizardService.commit(request)`（事务提交、连接释放），再调 `createRegistryContextAfterInitialization()`（无外层事务，bootstrap 的 `@Transactional` 独立成事务，失败仅 warn 不阻断，重启补建兜底）；`backfillIfInitialized` 经 `ObjectProvider` 自注入代理调用保证原子性。③补建 Runner（@Order 101）原在 `IdentifierStrategyLoader`（无 @Order）之前执行，策略注册表未装载、标识生成回退为 UUID——为 `IdentifierStrategyLoader` 补 `@Order(90)`，确保补建按命名空间策略生成标识。
9. **存量库 acceptability CHECK 约束迁移（冒烟暴露的既有平台缺陷）**：`AcceptabilityConverter` 按 11179-3:2023 表 20 落库**小写**，而基线 CHECK 仅允许**大写**——任何设置可接受性的写入在存量库必然失败（单测库由 Hibernate 建表无 CHECK，故测试全绿而真实库爆炸）。三方言迁移 `V20260923002` 重建 5 张表（designation/definition/designation_context_relation/definition_context_relation/designation_definition_pairing）的 CHECK 为 `lower(acceptability) in (标准小写值)`。SQLite 重建表已实测；SQL Server/达梦带守卫、未实测（沿用项目先例）。

## Risks / Trade-offs

- [RA 命名空间存在强制命名约定时种子指称校验失败] → 向导内 catch + warn 降级；补建路径同样 catch + warn，下次重启重试。
- [既有库 RA 无自有命名空间（理论外场景）] → `backfillIfInitialized` log warn 跳过，不阻止启动。
- [setup 重跑/abort 后重新创建，标识序列重新从 `CTX.00001` 分配] → 业务表含 `identifier_sequence_registry` 已被 `clearBusinessDataOnly()` 一并清空，且命名空间为全新行，(namespace, identifier) 唯一约束无冲突。
- [固定主键 UUID 与用户手工创建条目冲突] → 用户创建走 `@GeneratedValue` 随机 UUID，碰撞概率忽略；`existsById` 幂等守卫下最坏情况是复用既有记录。

## Migration Plan

0. 三方言迁移 `V20260923002__Acceptability_check_standard_lowercase`：重建 5 张表 acceptability 的 CHECK 为大小写不敏感（与 `AcceptabilityConverter` 的标准小写落库对齐；修复既有平台缺陷——存量库上设置可接受性必失败）。随应用启动自动应用。
1. 部署新版本：已初始化库下次启动时 `RegistryContextBackfillRunner` 自动补建 Registry Context 并补挂 `registry_specification.context_id`（若为空）。
2. 全新安装：向导 setup+commit 完成后创建；无额外步骤。
3. 回滚：回滚代码后 Registry Context 数据行残留即为普通上下文条目，无引用破坏（`registry_specification.context_id` 已挂记录会残留外键值但系统不依赖其非空）；CHECK 迁移回滚需手工恢复。
