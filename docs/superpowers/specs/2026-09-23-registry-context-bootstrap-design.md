# Registry Context 初始化引导设计（v4：标识随策略生成，手动策略用固定字符串）

日期：2026-09-23
状态：待确认（v4：标识符与命名空间生成策略一致，仅 MANUAL_ONLY 用固定字符串 `registry-context`）
变更名（预留）：`add-registry-context-bootstrap`

## 1. 背景与标准依据

ISO/IEC 11179-3:2023 要求所有指称（Designation）与定义（Definition）必须至少关联一个上下文，且该要求对被指称/被定义的条目本身是上下文时依然适用；标准明确说明这**不构成无限回归**，并给出两种做法（8.5.1.1 注、8.5.2.1 注）：

1. 每个上下文在其自身之内包含自己的指称/定义（自指）；
2. 设立一个 "registry context"，所有上下文的指称/定义都归入其中（包括其自身）。

附录 C 示例采用做法 2 + 做法 1 组合：`Context1` 是注册系统的默认上下文，其自身指称的语境为 `Context1` 自己（循环锚点），其他上下文的指称语境挂到 `Context1`。

此外，标准 Table 28 中 `Registry_Specification.context`（0..1）原文：

> "context which represents the registry itself. It is sometimes useful to reference a default context, rather than create a new one. This attribute is one possible choice for such a default."

## 2. 目标

在**管理员完成初始化向导、标识与版本生成规则已配置之后**，创建唯一的 **Registry Context**（注册系统默认上下文）作为**已注册条目**（AdministeredItem）：

- **所在命名空间 = 管理员在向导中创建的注册机构自有命名空间**（`ra.getNamespace()`，向导中 `ra.setNamespace(nsSaved)` 已落库）——**不是** `sys:` 系统默认命名空间，也不是任何其他命名空间；该命名空间上的段模板标识策略与版本策略即生成规则；
- **标识符（scoped identifier）与命名空间的标识生成策略严格一致，不硬编码**：AUTO 类策略（AUTO_ONLY / AUTO_WITH_OVERRIDE）由 `IdentifierInputPolicyService` → 段模板引擎生成（Context 对象缩写 `CTX`，如 `CTX.00001`）；**仅当策略为手动（MANUAL_ONLY）时才允许使用固定值**，且固定值是普通字符串 `registry-context`（不是 UUID）；version 同理走 `resolveVersion`（SEMVER 初版 `1.0.0`，随命名空间 versioningPolicy/versionInputMode 而定）；手动策略下 version 固定值 `1.0`；
- **区分两个概念**：条目主键（Entity.id）使用固定 UUID `...0002`，仅作幂等键与系统级引用（如 `registry_specification.context_id` 外键目标）；**标识符不是 UUID、不固定**（除上述手动策略回退字符串外）；
- 注册归属为该注册机构（RA）；
- 自动填写指称（英文 `Registry Context`、中文 `注册系统上下文`，preferred）与定义（中英文各一，忠实取自 11179-3:2023 8.2.2.2 原文并体现附录 C"注册系统默认上下文"定位）；
- 全部指称/定义的语境**自指**；
- 向导创建的 `RegistrySpecification.context` 回填指向该 Context（标准 Table 28 语义）。

**时序约束（用户明确要求）**：创建必须晚于向导中标识/版本规则的设置，不得在无规则配置的启动期凭空创建。

## 3. 现状关键事实（探索结论）

- `Context extends Item`（§6.4.2.3）；`Entity.id` 为 `@GeneratedValue(UUID)`，瞬态实体无 id，但可通过 `setId` 指定固定 UUID。
- 自指机制已存在：`ItemAdministrationService.SELF_CONTEXT_REFERENCE`（nil UUID），`loadContext(item, id)` 对 Context 条目把哨兵解析为条目自身（`ItemAdministrationService.java:847-871`）。
- 标识/版本 AUTO 生成链（`ensureAdministeredItem` 内部，`ItemAdministrationService.java:363` 附近）：
  `resolveIdentifier(ns, requested, itemType, variables)`（`IdentifierInputPolicyService.java:78`）→ `IdentifierStrategyConfigService.generateIdentifier` → `SegmentTemplateEngine` → `SequenceRegistryService` 取号；`resolveVersion(ns, requested)`（`:131`）：blank 且非 MANUAL_ONLY 时按 versioningPolicy 给初版（SEMVER→`1.0.0`）。`requested` 传 null 即 AUTO。
- `ensureAdministeredItem(item, req, explicitAuthority)` 支持显式传入归属 RA（向导阶段无登录用户，`resolveOwnershipForCreate` 无法推导，必须显式传 RA——`GenericItemCreator` 已有同构先例）。
- `scoped_identifier` 唯一约束为 `(namespace_id, identifier)`；`identifier_sequence_registry` 按 `(namespace_id, scope_key)` 计数。
- `InitWizardService.setup()`：`clearBusinessDataOnly()` 清空全部业务表（含 context、identifier_sequence_registry）；随后创建 RA → 命名空间（`ra.setNamespace(nsSaved)` 已保存，`InitWizardService.java:231`）→ `IdentifierStrategyConfig`（SEGMENT_TEMPLATE）+ `refreshRegistry()` → `RegistrySpecificationService.createDefault(...)`。
- `IdentifierInputMode`：`AUTO_ONLY` 拒绝显式 identifier、`MANUAL_ONLY` 拒绝空 identifier、`AUTO_WITH_OVERRIDE` 两者皆可。
- 既有已初始化库升级路径：规则早已配置，需在启动期一次性补建。
- `FactoryResetService` 恢复出厂后 `system.initialized=false`，用户必须重跑向导——向导 setup 会重建全部业务数据（含序列计数表已被清空，`CTX.00001` 可重新分配）。

## 4. 方案（v3）

### 4.1 新增 `RegistryContextBootstrapService`（web/service）

核心方法：

```java
/** 幂等创建 Registry Context（固定 UUID）。命名空间固定解析为 ra.getNamespace()；
 *  RA 无自有命名空间时抛 IllegalStateException（调用方决定降级策略）。
 *  已存在则直接返回既有记录。 */
public Context ensureRegistryContext(RegistrationAuthority ra)

/** 启动期补建入口：仅当系统已初始化且 Registry Context 缺失时执行。 */
public void backfillIfInitialized()
```

`ensureRegistryContext(ra)` 逻辑（与 `ContextService.create` 同序，但固定 id + 显式 RA + 命名空间自 RA 解析）：

0. **命名空间解析（结构性约束）**：`Namespace ns = ra.getNamespace()`；为 null 即抛 `IllegalStateException("registration authority has no namespace")`——从签名层面杜绝使用 `sys:` 或其他命名空间的可能；
1. 幂等：`contextRepository.existsById(REGISTRY_CONTEXT_ID)` 为真则返回既有实体；
2. `Context entity = new Context(); entity.setId(REGISTRY_CONTEXT_ID);`
3. 构造 4 条 `TextLine`（2 指称 + 2 定义，定义文本见 4.3），每行 `contexts = List.of(new TextLineContextEntry(SELF_CONTEXT_REFERENCE, Acceptability.PREFERRED))`；
4. `itemAdministrationService.applyDesignations(entity, designations, ns.getId())`、`applyDefinitions(entity, definitions)`；
5. `ItemRegistrationRequest req = new ItemRegistrationRequest(ns.getId(), null, null, designations, definitions)`（identifier/version 传 null → AUTO 规则生成）；
6. `itemAdministrationService.ensureAdministeredItem(entity, req, ra)`（显式归属 RA；内部生成 `CTX.00001` / `1.0.0` 并写 `ScopedIdentifierLifecycle` AUTO 标记）；
7. `contextRepository.save(entity)`；`itemAdministrationService.recordSubmission(entity, req)`（与 `ContextService.create` 对齐）；
8. 回填 `RegistrySpecification.context`：加载当前 spec（唯一），`context == null` 时置为本次实体并保存；
9. **手动策略回退（仅 MANUAL_ONLY）**：命名空间 `identifierInputMode == MANUAL_ONLY` 时，AUTO 传 null 会被 `resolveIdentifier` 拒绝，此时第 5 步的 request 改用显式字符串 identifier `registry-context`、version `1.0`（固定值是普通字符串，仅此时使用；AUTO_ONLY / AUTO_WITH_OVERRIDE 一律传 null 由策略生成，生成失败不静默回退固定值，异常向外抛）。

`backfillIfInitialized()` 逻辑：

- `systemSettingService.isInitialized()` 为假 → 直接返回（**不在未初始化系统的启动期创建**——此时标识/版本规则尚不存在，符合时序约束）；
- `contextRepository.existsById(REGISTRY_CONTEXT_ID)` 为真 → 返回；
- 取系统唯一 RA，`ra.getNamespace()` 为 null → log warn 跳过（不阻止启动）；否则调 `ensureRegistryContext(ra)`；全程 try/catch + log warn。

### 4.2 启动 Runner：`RegistryContextBackfillRunner`（web/seed）

- `ApplicationRunner`，`@Order(101)`；`ContextTestDataInitializer` 补 `@Order(100)`（保证"表非空即跳过"的测试种子不被抑制）；
- **不加 `app.seed.enabled` 条件**：Registry Context 是系统固有补全数据，不是测试种子（与 ContextTestDataInitializer 性质不同）；
- 仅委托 `bootstrapService.backfillIfInitialized()`。

### 4.3 指称/定义文本（忠实标准原文，不概括、不扩写）

- EN 指称：`Registry Context`；ZH 指称：`注册系统上下文`
- EN 定义：`The default context for this registry: the setting in which designations are used to designate and definitions are used to define registry items.`
- ZH 定义：`本注册系统的默认上下文：指称用于指称注册项、定义用于定义注册项的语境。`

### 4.4 接线点

| 接线点 | 动作 |
|---|---|
| `InitWizardService.setup()` | `createDefault(...)` 之后：`bootstrapService.ensureRegistryContext(ra)`——此时 RA/其自有命名空间/段模板策略/版本策略均已创建且策略注册表已刷新，标识与版本由规则产出；spec.context 由 4.1 第 8 步回填。调用点包 try/catch：创建失败仅 log warn，不阻断向导（重启后由补建路径兜底）。setup 重跑/abort 后重跑：业务表已清空，幂等键不存在 → 重新创建（序列计数表已清空，`CTX.00001` 重新分配无冲突） |
| 启动 Runner（4.2） | 仅服务**既有已初始化库**的升级补建 |
| `InitWizardService.abort()` | 无需改动——清空后由下一次 setup 重建 |
| `FactoryResetService` | **无需改动**——恢复出厂后系统未初始化，须经向导重跑，setup 内自然重建 |
| `ContextTestDataInitializer` | 仅新增 `@Order(100)`，守卫逻辑不动（测试种子仍在全新未初始化库播种，向导 setup 时被清空的既有行为不变） |

### 4.5 明确不做

- 不硬编码 Registry Context 的 identifier/version——一律走 RA 自有命名空间的已配置规则；唯一例外：命名空间为 MANUAL_ONLY 手动策略时，identifier 用固定字符串 `registry-context`、version `1.0`（固定值不是 UUID）；
- 不使用 `sys:` 系统默认命名空间承载 Registry Context——命名空间由 `ensureRegistryContext(RegistrationAuthority)` 签名结构性强约束为 `ra.getNamespace()`；
- 不在系统未初始化时创建（启动 Runner 对已初始化系统才生效）；
- 不改 `ContextService` / `ItemAdministrationService` / 实体模型 / 前端；
- 零 schema 变更、零迁移脚本；
- 不改 `RegistrySpecificationService.createDefault` 签名（spec.context 在创建后由 bootstrap 服务回填）。

## 5. 影响面与风险

- 编辑前按 AGENTS.md 对 `InitWizardService.setup`、`ContextTestDataInitializer.run` 运行 GitNexus `impact`（upstream）；（`FactoryResetService` 本次不改。）
- `applyDesignations(..., ns.getId())` 会触发 RA 命名空间的命名规范校验：向导新建命名空间默认无强制命名约定，无影响；若部署方预置了命名约定，种子文本（`Registry Context` 等）需通过该校验。
- 既有库补建依赖 `ra.getNamespace()` 非空（向导必建，理论上恒成立）；异常时跳过并 log warn，下次重启再试。
- 固定 UUID 与 (namespace, identifier) 唯一约束不冲突：幂等键是主键，identifier 唯一性在 RA 命名空间内首次分配。

## 6. 验证计划

- 单测（TDD）：
  - `RegistryContextBootstrapServiceTest`：首次创建（1 个 Context、固定 UUID、2+2 行、4 条自指 relation、AdministeredItem 归属 RA、identifier/version 由 RA 命名空间规则产出（真实段模板：`CTX.00001`/`1.0.0`）、spec.context 回填、二次调用幂等）；`ra.getNamespace()` 为 null 抛 `IllegalStateException`；
  - `backfillIfInitialized`：未初始化跳过；已初始化+缺失→经 `ra.getNamespace()` 补建；已存在→跳过；MANUAL_ONLY 命名空间回退路径；
  - `InitWizardServiceTest` 增补：setup 后 Registry Context 存在、其 scoped identifier 的 namespace = RA 命名空间、spec.context 非空；abort 再 setup 可重建。
- 冒烟：`scratch/registry-context-smoke/smoke.sh`——全新临时库：启动（未初始化，无 Registry Context）→ 向导 setup+commit → 断言 context 表固定 UUID 记录、`scoped_identifier` identifier=`CTX.00001`/version=`1.0.0`/namespace_id=RA 命名空间、4 条自指 relation、`registry_specification.context_id`=固定 UUID → 重启断言幂等；另用**已初始化副本库**（清掉 context 表模拟旧库）验证启动补建路径。
- 收尾：`detect_changes --scope all` 无 partial/truncated；README 默认数据说明同步。

## 7. 决策记录

| 决策 | 选择 | 理由 |
|---|---|---|
| 所在命名空间 | RA 自有命名空间（`ra.getNamespace()`），签名层强制 | 用户明确要求：管理员创建的注册机构的命名空间；杜绝 `sys:` 或其他命名空间被误用 |
| 创建时机 | 向导 setup 末尾（规则配置完成后）；启动 Runner 仅补建已初始化库 | 用户明确要求：晚于标识/版本规则设置，标识/版本由规则生成 |
| 标识/版本来源 | 与命名空间策略严格一致：AUTO 类策略传 null 走 AUTO 链生成；**仅 MANUAL_ONLY 使用固定字符串** `registry-context` / `1.0` | 用户明确要求：标识随生成策略，固定值仅手动策略可用且为普通字符串 |
| 归属 RA | `ensureAdministeredItem(item, req, ra)` 显式传入 | 向导阶段无登录用户，无法推导；`GenericItemCreator` 同构先例 |
| 幂等键 | 条目主键用固定 UUID `00000000-...-0002`（仅幂等键/系统级引用，**不是标识符**） | 跨环境稳定、系统级引用（spec.context）可行；`...0000`=SELF 哨兵、`...0001`=system 组织；标识符本身见上一行，不固定 |
| 自指实现 | `TextLineContextEntry(SELF_CONTEXT_REFERENCE, PREFERRED)` | 既有哨兵机制；瞬态实体无 id 时唯一可行路径 |
| 种子开关 | 启动补建 Runner 不挂 `app.seed.enabled` | Registry Context 是系统固有补全数据而非测试种子 |
| 向导内失败策略 | setup 中调用包 try/catch，失败 log warn | Registry Context 缺失不阻断初始化；重启后补建路径兜底 |
| 恢复出厂 | 不改动 FactoryResetService | 恢复出厂必重跑向导，setup 内自然重建 |
