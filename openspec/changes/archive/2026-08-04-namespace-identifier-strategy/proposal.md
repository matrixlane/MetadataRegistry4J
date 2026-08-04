## Why

当前命名空间的标识符治理只有"生成策略"（UUID/SEQUENCE/TEMPLATE）一维配置，且只能在初始化向导一次性设定：创建后无法在命名空间设置页修改，也没有"是否允许手动填写标识符/版本号"的治理语义。治理上需要区分三类命名空间——自有标准（强制自动编号）、混合（自动为主、允许人工覆盖）、外部标准导入（纯手动，禁用自动生成）——同时版本号也应有独立于标识符的输入模式与格式校验（SEMVER/SEQUENTIAL/DATE）。此外，模板与序列策略缺乏"命名空间简写"与"注册项类型"维度的占位符，无法表达 `{ns}.{type}.{seq:4}` 这类常见的分段标识模式。

## What Changes

- **命名空间级标识/版本输入治理策略**：`Namespace` 新增 `identifierInputMode`、`versionInputMode`（AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY，默认 AUTO_WITH_OVERRIDE）与 `manualIdentifierPattern`（手动标识校验正则，可空）三列；配合既有 `versioningPolicy`（SEMVER / SEQUENTIAL / DATE）构成完整的命名空间标识策略。
- **统一的服务端入口策略执行**：新增 `IdentifierInputPolicyService`，在受管项注册（`ItemAdministrationService`）与作用域标识符创建/变更（`ScopedIdentifierService`）两条入口统一执行输入模式裁决、手动标识正则校验、初始版本生成与版本格式校验；**BREAKING**：删除旧的 `IdentifierGenerationService`（直接生成、不做模式裁决），入口语义从"留空即生成"升级为"按命名空间策略裁决"。
- **命名空间设置页策略自管理**：命名空间创建/编辑接口携带策略字段，保存时对 `IdentifierStrategyConfig` 做版本化 upsert（新版本行 PUBLISHED、旧版本 disable）并刷新内存策略注册表；前端设置页新增"标识生成策略"与"版本生成策略"两个页签。
- **模板/序列策略占位符扩展**：模板策略新增 `{ns}`（命名空间简写前缀）与 `{type}`（注册项类型大写缩写，如 DataElement → DE）占位符；SEQUENCE 前缀与模板 prefix 字段支持 `{ns}`/`{type}` 令牌替换；模板含 `{seq}` 但调用方未分配序列号时，由策略服务从命名空间序列注册表（悲观锁）自分配；模板白名单允许 `.` 分隔符。
- **初始化向导同步扩展**：命名空间步骤新增标识/版本输入模式控件与序列起始值、补零位数参数；MANUAL_ONLY 时不创建策略配置；前缀缺省为空（不再默认 `ITEM`）。
- **前端表单随策略联动**：项创建表单根据所选命名空间的输入模式动态呈现"自动生成/手动输入"切换、禁用态与占位提示；编辑态标识符恒不可改；手动模式前端预校验命名空间正则与版本必填规则。

## Capabilities

### New Capabilities

- `namespace-identifier-policy`：命名空间级标识符/版本号输入治理策略——输入模式（AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY）的裁决语义、手动标识正则校验、版本化策略（SEMVER / SEQUENTIAL / DATE）的初始版本生成与格式校验、命名空间设置页的策略配置与策略版本化管理。

### Modified Capabilities

- `identifier-auto-generation`：模板策略新增 `{ns}`/`{type}` 占位符与前缀令牌替换、模板自分配序列号、`.` 分隔符放行；前端"自动生成/手动输入"切换由固定双选改为按命名空间输入模式条件呈现。

## Impact

- **后端**：`Namespace` 实体新增三列（Hibernate ddl-auto 管理，方言中立）；新增 `web/service/identifier/` 包（`IdentifierInputPolicyService`、`NamespaceIdentifierPolicy`、`NamespaceIdentifierPolicyResolver`）；`ItemAdministrationService`、`ScopedIdentifierService`、`NamespaceService`、`InitWizardService` 接入策略执行；`IdentifierStrategyConfigService` / `TemplateGenerationStrategy` 占位符扩展；`IdentifierStrategyConfigRepository` 新增按命名空间查询；DTO（`NamespaceRequest`/`NamespaceResponse`/`NamespaceDetailResponse`/`InitWizardSetupRequest`）扩展策略字段。
- **前端**：`NamespaceList.vue`（两个新页签）、`InitWizard.vue`（命名空间步骤扩展）、`ItemFormDialog.vue`（按模式联动的标识/版本控件）、`common.ts` 类型与 `zh-CN.json` 文案。
- **删除**：`IdentifierGenerationService` 及其测试（由 `IdentifierInputPolicyService` 取代）。
- **不影响**：注册机构/注册规范门槛、外部命名空间导入路径、federation/merge 流程、数据库方言可移植性（新列均为 JPA 注解管理）。
