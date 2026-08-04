# Design: 全版本治理初始化与命名空间准入门槛

## 背景

现状（代码事实）：

- 前端 `InitWizard.vue`：`PERSONAL` 的步骤指示器为「版本选择 → 数据库 → 管理员密码」，数据库步骤"下一步"直接调用 `setup` 并跳到密码步骤；治理步骤对个人版不可见。
- 后端 `InitWizardService.setup`：`PERSONAL` 强制 `selfAsRegistrar=true`、`createNamespace=true`、`createRegistrySpecification=true`，并默认注册机构名"个人注册机构"、规范名"个人元数据注册表规范"。
- `registry-specification` 能力已有准入门槛：注册规范创建前不允许创建项目/注册元数据项。本变更为其补充平行的"命名空间"门槛。

## 决策

### D1：两个版本统一治理步骤序列，个人版以"预填默认值"保持精简

治理步骤序列统一为 注册机构 → 命名空间 → 注册规范（随后是管理员密码）。版本差异仅体现在：

- `PERSONAL`：各治理字段预填默认值（注册机构名"个人注册机构"、规范名随注册机构名联动生成等），管理员可"一路下一步"完成，也可修改任何字段。
- `TEAM`：字段不预填（或仅预填联动项），必填项需显式填写。

理由：用户明确要求个人版"包含这一系列初始化设置"，同时不牺牲个人版的精简体验；统一步骤序列也让前端导航逻辑只需维护一份。

### D2：命名空间步骤可跳过，注册机构与注册规范必选

- 命名空间步骤提供显式"跳过"选项，适用于仅希望导入/访问外部组织数据标准的用户。跳过时 `createNamespace=false`。
- 注册机构与注册规范不引入跳过选项：二者是数据治理的根基（注册规范缺失时系统已有全局锁定语义），保持必选。

### D3：准入门槛落点——注册/标识符生成入口校验 + 前端引导

后端在"创建自有数据标准"的入口（注册受管项、在注册机构关联命名空间下生成 `Scoped_Identifier`，当前汇聚于 `ItemAdministrationService` 等）校验：系统内存在至少一个 `registrationAuthority != null` 的命名空间；否则返回业务校验错误（400），错误信息引导用户先创建命名空间。

- 外部命名空间（`registrationAuthority == null`，命名机构为外部组织）不参与该校验，导入外部数据标准不受限。
- 系统内部命名空间（`system-default`，`sys:` 前缀）不满足该条件——它仅供系统内部对象与试用阶段使用，不属于"基于注册机构的命名空间"。
- 前端在数据标准创建入口检测该条件不满足时禁用操作并展示引导（跳转命名空间创建页）。

门槛归入 `identification` 能力：`Scoped_Identifier` 与命名空间的归属规则是该能力的既有领域。

### D4：`setup` 请求布尔开关的缺省语义保持向后兼容

`PERSONAL` 下 `selfAsRegistrar` / `createNamespace` / `createRegistrySpecification` 缺省（null）时维持 `true`（与现行静默行为一致）；仅当显式传 `false` 时才跳过对应创建。这样旧前端/旧脚本在未升级时不会得到意外结果。

### D5：与既有注册规范门槛的关系

两道门槛串联，语义互补：

1. 注册规范门槛（既有）：无注册规范 → 锁定全部数据治理功能。
2. 命名空间门槛（新增）：有注册规范但无注册机构关联命名空间 → 允许浏览/导入外部标准，阻止创建自有数据标准。

由于本变更中注册规范保持初始化必选，门槛 1 在新装系统上总是已满足；门槛 2 仅在用户初始化时跳过命名空间后才会触发。

### D6：标识/版本生成策略归入命名空间步骤

代码事实：`RegistrySpecification` 实体不持有 `identifierPrefix`/`autoGenerateIdentifier`/`identifierPattern`/`versioningPolicy`/`mandatoryClassification` 任何字段，向导原注册规范步骤中的这些控件提交后被 `createDefault` 静默丢弃；而 `IdentifierStrategyConfig` 本就是按命名空间维度绑定的策略配置（UUID/URN/SEQUENCE/TEMPLATE + JSON 参数）。因此：

- 标识生成策略在命名空间步骤采集，初始化创建默认命名空间时同步创建命名空间级 `IdentifierStrategyConfig`（PUBLISHED 即生效，version=1），并调用 `IdentifierStrategyLoader.refreshRegistry()` 使其立即可用。
- 版本生成策略暂无独立引擎消费（`ScopedIdentifier.version` 为自由字符串），作为命名空间级设置持久化在 `Namespace.versioningPolicy`（默认 `SEMVER`，候选 `SEMVER`/`SEQUENTIAL`/`DATE`），供后续版本自动递增生效。
- `RegistrySpecificationService.createDefault` 移除五个死参数；`InitWizardSetupRequest` 中相应死字段以命名空间作用域字段（`namespaceName`、`identifierStrategy`、`identifierPrefix`、`identifierTemplate`、`versionPolicy`）替换。
- 角色分配（`selfAsRegistrar`）与注册机构同属一层，并入注册机构步骤；命名空间独占一个步骤。

### D7：向导与全局界面自适应滚动

向导与登录页共享的 `.login-layout` 采用视口高度 + 居中布局，内容超过一屏时上方被裁切且无法滚动。修复为最小视口高度 + 内容可滚动（`min-height: 100vh`、`overflow-y: auto`、顶部对齐 + 自动外边距居中）；并核查 `AppLayout` 主内容区滚动能力，保证所有界面在内容超过满屏时均可滚动。

## 影响面

- `InitWizardService.setup`：`PERSONAL` 分支去除强制 `true`，改为"缺省 true、显式 false 跳过"；`createRegistrySpecification` 对显式 `false` 视为校验错误（必选）。
- 新增命名空间存在性校验：建议在 `NamespaceRepository` 增加"存在关联注册机构的命名空间"查询，在校验服务中复用。
- 前端 `InitWizard.vue`：个人版 `displaySteps`/`displayActive`/`prevStep`/`nextStep` 去掉治理步骤裁剪；命名空间步骤增加"跳过"选项。
- 测试：`InitWizardServiceIntegrationTest`、`InitWizardControllerTest`、前端向导交互测试；新增门槛的服务级与 API 级测试（三方言下行为一致）。
