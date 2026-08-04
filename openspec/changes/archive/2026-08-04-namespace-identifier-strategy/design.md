## Context

现状（代码事实）：

- 命名空间标识治理仅有一维：`IdentifierStrategyConfig`（UUID/SEQUENCE/TEMPLATE + JSON 参数），只能在初始化向导创建默认命名空间时设定一次；命名空间设置页（`NamespaceService` / `NamespaceList.vue`）完全没有策略字段，创建后不可调整。
- 标识符入口逻辑分裂：`ItemAdministrationService` 直接调用 `IdentifierStrategyConfigService.generateIdentifier`（留空即生成，无模式概念）；`ScopedIdentifierService` 走旧 `IdentifierGenerationService`，版本号硬编码缺省 `1.0.0` 且只认 SemVer。
- `Namespace.versioningPolicy`（上一变更引入）已落库但无消费方——没有任何代码依据它生成或校验版本号。
- 模板策略占位符只有日期/序列/前缀，无法表达"命名空间简写 + 项类型 + 序列"的分段模式；`{seq}` 依赖调用方在 `GenerationContext` 中预分配序列号，而现有入口都不分配。

约束：新列必须方言中立（JPA 注解管理，禁止迁移脚本承载表结构）；弱类型字符串列须容忍脏数据；缺省行为必须与现状兼容（既有命名空间升级后行为不变）。

## Goals / Non-Goals

**Goals:**

- 命名空间级"标识符输入模式"与"版本输入模式"两个独立治理维度（AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY），服务端统一裁决。
- 手动标识符可配置正则校验；版本号按 `versioningPolicy` 生成初始版本并校验手动值格式。
- 命名空间设置页可查看/修改全部策略（含生成策略的版本化 upsert），初始化向导同步采集。
- 模板/序列策略支持 `{ns}`、`{type}` 占位符；模板可自分配序列号。
- 前端表单按命名空间策略联动（切换控件显隐、禁用态、占位提示、预校验）。

**Non-Goals:**

- 不做版本号自动递增（如 1.0.0 → 1.0.1）：`versioningPolicy` 仅用于初始版本生成与格式校验，版本演进仍由用户显式指定。
- 不改动既有命名空间的存量标识符/版本号数据。
- 不引入新的生成策略类型（URN 等留待后续）。
- 不改动 federation/merge 中标识符的处理逻辑。

## Decisions

### D1：输入模式独立于生成策略，作为命名空间的两列持久化

"能否手动填写"与"自动生成用什么算法"是正交的治理决策：MANUAL_ONLY 命名空间根本不需要生成策略（外部标准导入场景），AUTO_ONLY 命名空间禁止一切手动值。因此 `identifierInputMode` / `versionInputMode` 作为 `Namespace` 的独立列（`VARCHAR(32)`，实体默认值 `AUTO_WITH_OVERRIDE`），而不是塞进 `IdentifierStrategyConfig` 的 JSON 参数。版本号同理独立于标识符：`versionInputMode` 单列。

备选：把模式并入策略配置 JSON——否决，JSON 参数无 Schema 约束、无法参与响应 DTO 的稳定契约，且 MANUAL_ONLY 时配置行本就不存在。

### D2：策略解析经 `NamespaceIdentifierPolicyResolver`，脏数据回退默认

策略列为弱类型字符串，`NamespaceIdentifierPolicyResolver` 统一解析为强类型 `NamespaceIdentifierPolicy` record：无法解析的值记警告并回退默认（AUTO_WITH_OVERRIDE / SEMVER），空白的 `manualIdentifierPattern` 归一为 null。所有消费方（服务端裁决、DTO 映射、前端响应）共享同一解析结果，避免各处重复解析逻辑不一致。

### D3：单一策略执行入口 `IdentifierInputPolicyService`，取代 `IdentifierGenerationService`

旧 `IdentifierGenerationService` 只是"生成"的薄封装，没有模式裁决位置。新服务提供三个语义明确的操作：`resolveIdentifier`（创建时裁决：AUTO_ONLY 拒绝手动值、MANUAL_ONLY 拒绝空值、其余按"空则生成、非空则校验"）、`resolveVersion`（创建时版本裁决：AUTO 模式空值按 `versioningPolicy` 生成初始版本 1.0.0 / 1 / 当天日期；手动值按策略校验格式）、`validateManualVersion`（版本变更时复用同一校验）。`ItemAdministrationService` 与 `ScopedIdentifierService` 两条入口都改走它，行为收敛、旧服务连同其测试一并删除。

### D4：命名空间设置页对策略配置做版本化 upsert

`NamespaceService.create/update` 在请求携带 `identifierStrategy` 时：禁用该命名空间既有 PUBLISHED 配置行，写入新版本行（version 递增、PUBLISHED、createdBy="namespace-settings"），随后 `IdentifierStrategyLoader.refreshRegistry()` 使内存注册表立即生效。选择 UUID 时只禁用旧配置、不写新行（UUID 为内置兜底，无需配置行）。请求省略策略字段时一律不动既有配置——保证部分更新语义。响应 DTO 回读当前生效配置的类型与参数，供设置页回填。

理由：复用 `IdentifierStrategyConfig` 既有的版本/状态机（PUBLISHED → disabled），保留审计轨迹，避免"原地改 JSON"丢失历史。

### D5：`{ns}` / `{type}` 占位符在策略层解析，类型缩写取类名大写字母

`{ns}` 取自 `Namespace.shorthandPrefix`；`{type}` 由注册项类简单名提取大写字母（DataElement → DE、DataElementConcept → DEC、ValueDomain → VD），入口在裁决时把项类型放入 `GenerationContext`。SEQUENCE 的 prefix 与 TEMPLATE 的 prefix 字段同样支持这两个令牌（`resolvePrefixTokens` 统一替换）。模板含 `{seq}` 而上下文未提供序列号时，由 `IdentifierStrategyConfigService` 从命名空间序列注册表以悲观锁自分配（pad 取模板配置），消除"模板策略必须外部预分配序列"的隐式契约。模板白名单放行 `.`，使 `{ns}.{type}.{seq:4}` 成为合法模板。无上下文时 `{ns}`/`{type}` 渲染为空串，保持既有降级语义。

### D6：缺省值全部向后兼容

`identifierInputMode` / `versionInputMode` 默认 `AUTO_WITH_OVERRIDE`（等价于现状：留空生成、手填放行）、`versioningPolicy` 默认 `SEMVER`（初始版本 1.0.0，与旧硬编码一致）。既有数据库升级后，所有存量命名空间行为不变。命名空间接口的策略字段全部可空，省略即不触碰既有设置。

### D7：初始化向导 MANUAL_ONLY 时不创建策略配置

向导命名空间步骤新增输入模式控件与 `strategySeqStart`/`strategySeqPad` 参数；`identifierInputMode=MANUAL_ONLY` 时跳过 `IdentifierStrategyConfig` 创建（前端同步禁用策略类型控件）。前缀缺省改为空串（由 `{ns}`/`{type}` 令牌组合出有意义的前缀），不再硬编码 `ITEM`。非法模式值经 `normalizeInputMode` 归一为 `AUTO_WITH_OVERRIDE`。

## Risks / Trade-offs

- [弱类型策略列存在脏数据可能] → 解析器统一回退默认并记警告；前端设置页只写合法枚举值。
- [手动标识正则配置错误（非法正则）会导致校验静默跳过] → 编译失败时记警告放行，并在设计文档与界面提示中说明正则合法性由配置者负责；前端表单预校验同样 try/catch 兜底。
- [模板自分配序列与调用方预分配序列并存，语义上存在两条分配路径] → 以"上下文序列号 > 0 优先，否则自分配"的明确顺序裁决；无 `{seq}` 占位符的模板不分配，避免空耗序列。
- [策略 upsert 与初始化向导都写 `IdentifierStrategyConfig`，两处 JSON 构造逻辑] → 字段语义保持一致（prefix/start/incrementBy/pad/template），响应 DTO 回读容忍脏 JSON（只暴露类型）。
- [`{type}` 缩写依赖类名大写规则，新增项类型自动获得缩写但不可自定义] → 规则简单可预期；如需定制缩写，后续可作为命名空间/类型级配置扩展。

## Migration Plan

- 三列由 Hibernate ddl-auto 自动建出，既有行取实体默认值（AUTO_WITH_OVERRIDE / SEMVER），无需迁移脚本；三方言（SQLite / SQL Server / 达梦）行为一致。
- 删除 `IdentifierGenerationService` 为内部重构，无 API 契约变化（REST 路径与 DTO 字段仅新增、无删除）；旧前端不传策略字段时服务端按默认策略裁决，行为兼容。
- 回滚：还原代码即可，新列保留在库中无害（默认值为兼容语义）。
