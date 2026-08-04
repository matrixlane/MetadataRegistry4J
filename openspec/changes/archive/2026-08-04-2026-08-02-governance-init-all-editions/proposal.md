## Why

当前初始化向导中，个人版不展示注册机构、注册规范与命名空间的治理配置步骤：`InitWizardService.setup` 对 `PERSONAL` 强制 `selfAsRegistrar=true`、`createNamespace=true`、`createRegistrySpecification=true`，以默认值在后台静默创建这三者，用户对自己注册表的治理根基毫无感知；团队版虽有完整步骤，但同样缺少"命名空间可跳过、后续补建"的语义。

然而，注册机构、注册规范、注册机构关联的命名空间是个人或团队开展所在机构数据治理工作的先决条件——即使个人版也应显式包含这一系列初始化设置。同时，仅希望导入和访问外部组织数据标准的用户应允许跳过命名空间设置；但之后若希望开始创建自己的数据标准，仍必须先创建自己的（基于注册机构的）命名空间。

## What Changes

- **个人版初始化向导不再静默创建治理对象**：与团队版一样展示 注册机构 → 命名空间 → 注册规范 配置步骤；个人版为治理字段预填合理默认值以保持精简体验，管理员可直接确认或修改。
- **命名空间设置在两个版本中均可显式跳过**：面向仅导入/访问外部组织数据标准的场景；注册机构与注册规范在两个版本中保持必选。
- **新增准入门槛**：创建自有数据标准（在注册机构下注册受管项、生成 `Scoped_Identifier`）前，系统必须存在至少一个与注册机构关联的命名空间；缺失时阻止操作并引导补建。导入或访问外部组织数据标准（外部命名空间）不受此限。
- **`setup` 接口语义调整**：`PERSONAL` 不再强制 `createNamespace=true` / `createRegistrySpecification=true`，尊重向导提交的用户选择；名称类字段缺省时仍使用默认值；为兼容旧调用方，`PERSONAL` 下布尔开关缺省（null）时维持 `true`。
- 幂等、`abort`、`commit` 语义保持不变。
- **命名空间成为独立的实质配置步骤**：命名空间与注册规范拆分为两个不同步骤；命名空间步骤除创建开关（可跳过）外，同时包含**标识生成策略**（UUID/SEQUENCE/TEMPLATE 及前缀、模板参数，落地为命名空间级 `IdentifierStrategyConfig`）与**版本生成策略**（持久化在 `Namespace.versioningPolicy`）。
- **注册规范步骤去除错位字段**：`identifierPrefix`、`autoGenerateIdentifier`、`identifierPattern`、`versioningPolicy`、`mandatoryClassification` 本属命名空间范畴，且 `RegistrySpecification` 实体并不持有这些字段（此前提交后静默丢弃），从注册规范步骤移除，标识/版本策略并入命名空间步骤。
- **界面自适应滚动**：初始化向导（及全局布局）内容超过一屏时支持滚动，不再出现无法下滚的裁切。

**后续变更（不在本变更范围）**：注册表合并策略（依据 Registration_Authority_Identifier 一致性合并、Git 风格合并策略、合并后注册机构/注册规范/命名空间唯一化、Scoped_Identifier 可选重新生成）已单独立项，见变更 `2026-08-02-registry-merge-strategy`。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `system-initialization`：向导流程不再按版本裁剪治理步骤，两个版本均包含 注册机构 → 命名空间 → 注册规范 步骤；命名空间步骤在两个版本中均可跳过。
- `deployment-edition`：个人版功能约束中"初始化向导自动创建默认注册机构、治理字段使用合理默认值"的静默语义，改为"展示治理配置步骤并预填默认值"。
- `identification`：新增"自有数据标准的命名空间前置条件"准入规则；新增"初始化时命名空间级标识/版本生成策略配置"规则。

## Impact

- **后端**：`InitWizardService.setup` 的版本驱动默认值逻辑；项注册/标识符生成入口（`ItemAdministrationService` 等）新增"注册机构关联命名空间存在性"校验。
- **前端**：`InitWizard.vue` 步骤指示器与导航逻辑（个人版显示治理步骤、命名空间步骤可跳过）；数据标准创建入口在无命名空间时的禁用与引导提示。
- **不影响**：部署版本持久化与版本约束（SINGLE 策略）、数据库配置步骤、`commit`/`abort` 语义、外部命名空间的创建与导入。
