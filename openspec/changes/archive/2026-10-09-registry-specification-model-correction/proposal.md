# Proposal: registry-specification-model-correction

## Why

`RegistrySpecification` 实体当前 `extends Item`（JPA JOINED 继承，`mdr.registry_specification.id` 的主键兼作指向 `mdr.item(id)` 的外键，FK 由 Hibernate `ddl-auto=update` 启动时生成）。经 Milvus 检索 ISO/IEC 11179-3:2023 原文逐条复核（2026-10-09）：

- 9.4.10 Registry_Specification class 无 "Direct superclass" 小节——Description / Associations（"No associations are specified in this metamodel region."）/ Attributes（表 28）；
- 标准中所有 Item 子类均显式声明 Direct superclass（Namespace §7.3.3.1、Context §6.4.2.3、Naming_Convention §8.4.4.1、Constraint_Set §9.4.5.1 等，固定句式 "X is a subclass of Item (6.4.2.1)…"），Registry_Specification 不在其中；
- 表 28 属性全部为类自有（含 `name 1 Sign`），不继承 Item 的指称/定义/槽/标识/注册机制。

即 `extends Item` 无标准依据，属元模型级偏差。由此派生的一系列问题本可避免：孤儿行（子表有、基表无）→ Hibernate 启动加 FK 失败 → `V20260728001` 一次性迁移 + `cleanupOrphanRecords` 启动兜底 → 2026-10-09 WS 363 全量数据恢复后兜底查询全表扫描阻塞 ApplicationReadyEvent 13+ 分钟、前端启动页永久等待的生产事故。

用户裁定：严重标准偏差，必须及时改正并防再发（沿用"标准没有的不保留"原则）。

## What Changes

- **实体纠偏**：`RegistrySpecification` 由 `extends Item` 改为 `extends Entity`（对齐 `Contact`/`Organization`/`LanguageIdentification` 独立根实体范式），表列集合不变，`mdr.registry_specification` 不再产生 `mdr.item` 基行、不再有指向 `mdr.item` 的 FK。
- **移除非标准 Item 能力**：Request/Response DTO 删 `designations`/`definitions`/`registration`/`label`/`meta`；服务删 `applyDesignations`/`applyDefinitions`/`ensureAdministeredItem`/命名规范符合性链路与 `ObjectClassService.byKeyword` 依赖（改写为按 `name` 的 Specification）；前端设置页删「指称与定义」「注册标识」两个区块。
- **退役孤儿清理**：删 `cleanupOrphanRecords()` 启动监听与 `RegistrySpecificationRepository.findOrphanIds()`（孤儿语义随 FK 根除）；历史迁移 `V20260728001` 保持原样（已应用库历史记录，新模型下自然空转）。
- **联邦与搜索去 special-casing**：`FederationBundleService` 删 `instanceof RegistrySpecification` 过滤；全局搜索删 `RegistrySpecification` 类型分支与白名单条目；`SmartItemPicker` 删死映射。
- **三方言迁移 V20261009008**：动态解析并删除 `registry_specification→item` 的 Hibernate 历史 FK（存在才执行），按 FK 依赖序清理存量 spec 的 `designation` 子行与 `item` 基行；对全新库幂等无感。
- **防再发**：新增 `StandardInheritanceConformanceTest` 反射守护测试（硬断言 RegistrySpecification 超类为 Entity；`extends Item` 继承边白名单制，新增未登记继承边即失败）；AGENTS.md 增补"实体继承规则"；记忆库记录事故链与全量审计 backlog。
- **既有能力保持**：表 28 全量标量 CRUD、默认语言（ItemAdministrationService/DesignationService/DefinitionService 的 `loadDefaultLanguage` 链路）、默认上下文（`ContextService.getDefault`、Registry Context 引导/回填、`RegistrationAuthorityDeletionService` 的 `context_id=NULL` 清理）全部不变。

非目标：

- 其余 14 个 `Item` 子类的标准符合性全面审计（另立变更，记入 backlog）；
- `api/system.ts` 过时 interface 的整体重构（仅删除无引用死代码 `registrySpecificationApi` 并收敛被消费字段）。

## Capabilities

### New Capabilities

- `standard-inheritance-conformance`：实体继承符合性守护——`@StandardReference` 实体的继承关系必须有 ISO/IEC 11179 "Direct superclass" 条款依据并以白名单测试锁定，新增未登记继承边即构建失败。

### Modified Capabilities

- `registry-specification`：①"Registry_Specification class exists"需求由"是 Item 的子类"反转为"独立于 Item 的类（标准无 Direct superclass 声明）"；②"Registry Specification Full Attribute Editing in Settings"删除指称/定义编辑能力及相关场景；③初始化向导/设置页需求中"指称/定义于设置页维护"的表述删除（该能力整体不存在）。
- `identification`：注册区段 UI 需求（命名空间/标识符/版本输入）的 Item 子类清单移除 `RegistrySpecification`（其不再具有 identified_item/注册区段）。

## Impact

- **代码**：common-facilities 1 实体；web 模块服务/DTO/仓库/联邦/搜索/控制器约 8 文件；前端 3 文件 + 构建产物同步；测试 4 类改写 + 1 类删除 + 1 新增守护测试。
- **API**：`POST/PUT /api/v1/registry-specifications` 请求体删 `designations`/`definitions`/`registration` 字段；响应删 `label`/`designations`/`definitions`/`meta`（破坏性契约变更，前端同步发布）。
- **数据**：三方言迁移 V20261009008（真实 SQL Server 库备份后验证；SQLite 全新库重放 + 脏数据副本冒烟；达梦脚本审查）。
- **兼容**：注册规范的默认语言/默认上下文消费方零改动；初始化向导 `createDefault` 签名不变。
- **依赖**：无新增依赖。
