# RegistrySpecification 元模型纠偏设计（脱离 Item 继承）

日期：2026-10-09
状态：已定案（用户指令：严重标准偏差，必须及时改正并防再发）
变更名（OpenSpec）：`registry-specification-model-correction`

## 1. 背景与标准依据

`RegistrySpecification` 实体（`metadata-registry-common-facilities/.../registration/RegistrySpecification.java:37`）当前 `extends Item`（JPA JOINED 继承），物理表 `mdr.registry_specification` 的主键兼作指向 `mdr.item(id)` 的外键（该 FK 由 Hibernate `ddl-auto=update` 启动时生成，Flyway 脚本中不存在）。

经 Milvus 检索 ISO/IEC 11179-3:2023 原文复核（2026-10-09）：

- **9.4.10 Registry_Specification class** 的小节结构为 9.4.10.1 Description / 9.4.10.2 Associations（"No associations are specified in this metamodel region."）/ 9.4.10.3 Attributes（表 28），**无 "Direct superclass" 小节**。
- 标准中所有 Item 子类均显式声明 Direct superclass，固定句式为 *"X is a subclass of Item (6.4.2.1), allowing instances to be identified, registered, administered, named, defined and classified."*（Namespace 7.3.3.1、Context 6.4.2.3、Naming_Convention 8.4.4.1、Constraint_Set 9.4.5.1、Classification_Scheme_Item_Relationship_Type 10.3.4.1 等，已逐一核实）。
- 表 28 属性全部为类自有属性（`name 1 Sign`、`comment`、`web_address`、`default_language`、`alternative_language 0..*`、`standard`、`degree_of_conformance`、`conformance_level/label`、`included_features`、`excluded_features`、`default_time_zone`），即该类**自带 name**，不继承 Item 的指称/定义/槽/标识/注册机制。

结论：`extends Item` 无标准依据，属于元模型级偏差。由此产生的一系列历史问题（孤儿行 → FK 约束失败 → `cleanupOrphanRecords` 兜底 → 2026-10-09 启动挂起 13 分钟事故）均可避免。

## 2. 目标 / 非目标

### 目标

1. `RegistrySpecification` 改为独立根实体（`extends Entity`，同 `Contact`/`Organization`/`LanguageIdentification` 既有范式），不再产生 `mdr.item` 基行，不存在指向 `mdr.item` 的 FK。
2. 移除其作为 Item 获得的一切非标准能力：指称（designations）、定义（definitions）、注册记录（registration/itemMeta）、命名规范符合性校验、联邦条目流、全局搜索条目类型。
3. 既有能力保持：表 28 全量标量 CRUD、默认语言（`default_language`/`alternative_language`）、默认上下文（`context`，`ContextService.getDefault` 与 Registry Context 引导/回填链路不受影响）。
4. 存量数据库无损迁移（真实 SQL Server 库 + SQLite + 达梦三方言）。
5. 防再发：自动化继承符合性守护测试 + AGENTS.md 规则增补。

### 非目标

- 其余 14 个 `Item` 子类的标准符合性全面审计（记入 backlog，另立变更）。
- `api/system.ts` 中过时 interface 的整体重构（仅做与本次直接相关的最小清理）。

## 3. 方案比较与裁定

| 方案 | 内容 | 裁定 |
|---|---|---|
| **A. 彻底纠偏（采用）** | 实体 `extends Entity`；删 designations/definitions/registration/meta 字段与全部 Item 能力调用；三方言迁移解绑 FK 并清理存量基行/指称行；删 `cleanupOrphanRecords`/`findOrphanIds`；联邦/搜索去 special-casing；前端设置页删「指称与定义」「注册标识」区块 | 符合用户一贯裁定"标准没有的不保留"（remove-system-default-namespace 先例） |
| B. 能力保留为 ext 扩展 | 脱钩 Item 但指称/定义等经 ext 卫星表保留，UI 不变 | 否决：标准无依据，复杂度高，违背分层规则精神 |
| C. 直接关联替代继承 | 脱钩但保留 designation/definition 直接关联 | 否决：同属非标准扩展，换汤不换药 |

## 4. 详细设计

### 4.1 实体（common-facilities）

`RegistrySpecification.java`：`extends Item` → `extends Entity`；删 `import ...core.Item`。`@Entity`、隐式表名 `registry_specification`、`@StandardReference("ISO/IEC 11179-3:2023, 9.4.10")` 均不变。表列集合不变（本就自有全部表 28 列）。

### 4.2 服务 / DTO（web 模块）

- `RegistrySpecificationService`：
  - 删 `cleanupOrphanRecords()`（含 `@EventListener(ApplicationReadyEvent)`）——孤儿问题随 FK 消失而根除；
  - 删 `ItemAdministrationService` 字段及 `applyDesignations`/`applyDefinitions`/`ensureAdministeredItem` 调用（`:255-261`）；
  - `createDefault` 不再构造 designations `TextLine`（`:182`）及失效注释（`:150-151`）；
  - `toResponse`：`label` 字段删除；`mapDesignations`/`mapDefinitions`/`buildItemMeta` 删除（`:301-303`）；
  - `list` 的关键词 Specification：`ObjectClassService.byKeyword`（泛型上限 `<T extends Item>`，编译硬阻塞）改写为本服务内的按 `name` 匹配 Specification。
- `RegistrySpecificationRequest`：删 `designations`/`definitions`/`registration` 三字段及 import。
- `RegistrySpecificationResponse`：删 `label`/`designations`/`definitions`/`meta` 四字段及 import。
- `RegistrySpecificationRepository`：删 `findOrphanIds()`（昨日性能修复随孤儿语义一并退役）。
- **保留不动**（已核实仅使用 `getContext()`/`getDefaultLanguage()`/`exists()` 标量能力）：`ItemAdministrationService:1141`、`DesignationService:174`、`DefinitionService:148`、`ContextService:73`、`RegistryContextBootstrapService`、`RegistryContextBackfillRunner`、`DataGovernanceReadinessService`、`InitWizardService`（`createDefault` 签名不变）、`RegistrationAuthorityDeletionService:138`（`UPDATE registry_specification SET context_id=NULL` 必须保留；仅修正其注释中「子表行先于父 item 行」的失效前提）。

### 4.3 联邦与全局搜索

- `FederationBundleService`：删 `:122` 的 `instanceof RegistrySpecification` 过滤器（新模型下类型上恒真、语义为空）与 `:13` import；`:106-111` javadoc spec 段落改写。`Namespace` 过滤保留。
- `MetadataSearchService`：删 `:108`（itemType CASE 分支）与 `:408`（typeToTable 分支）；主查询以 `administered_item` 为驱动，spec 本就不会出现。
- `MetadataSearchController`：`SUPPORTED_TYPES` 删 `"RegistrySpecification"`（`:43`）。
- `SmartItemPicker.vue`：删 `RegistrySpecification` 的缩写/标签/标签色三处死映射。

### 4.4 前端

- `RegistrySpecificationSettings.vue`：删「指称与定义」Divider + `DesignationDefinitionSection`、`「注册标识」Divider + `ItemRegistrationSection` 及关联 import/ref/回显/校验/`buildPayload` 调用；`buildRegistrySpecPayload(form)` 只传 form。
- `registrySpecForm.ts`：`RegistrySpecFormModel` 删三字段；`ARRAY_FIELDS` 删两项；删 `toTextLinePayload()` 整函数；`buildRegistrySpecPayload` 去 `registrationPayload` 形参。
- `api/system.ts`：删除死代码 `registrySpecificationApi`（其引用的 `GET /registry-specifications`、`/has-metadata` 端点后端不存在，全仓库无引用）；`RegistrySpecification` interface 收敛为真实响应中被消费的字段（`id`/`name`/`defaultLanguageId`/`defaultLanguageIdentifier`）。
- 路由/菜单/i18n/初始化向导：零改动（已核实仅表 28 标量）。

### 4.5 数据库迁移（三方言同版本号，下一可用版本 V20261009008）

前置事实：`sqlserver/V20260601002` 建表时**没有** FK；FK 是 Hibernate 启动时按 JOINED 映射补加的（名称由 Hibernate 命名策略生成，需按实库解析）；sqlite/dm 由 Hibernate 建表，同理。**任何全新库（含 sqlite 基线）从一开始就不存在该 FK 与基行**，迁移必须对"无 FK/无基行"的新库幂等无感（守卫模式）。

- **SQL Server**：
  1. 解析并删除 `mdr.registry_specification` 上指向 `mdr.item` 的外键（查 `sys.foreign_keys`，动态 SQL，COL_LENGTH/OBJECT_ID 守卫，不存在则跳过）；
  2. 删除存量 spec 的 Item 子行与基行：`mdr.designation`（及其实体关联子表 `designation_definition_pairing`/`designation_context`/`designation_namespace`/`naming_convention_conformance`，按 FK 依赖序）中 `item_id ∈ (SELECT id FROM mdr.registry_specification)` 的行，再删 `mdr.item` 中同 id 行；
  3. 全程 schema 全限定 + 守卫，重放安全。
- **SQLite**：裸表名；仅当存在 FK/基行时执行清理（新库无 spec 数据则天然空转）；若实库核实存在声明式 FK 则按项目既有"重建表"模式重建 `registry_specification`（不带 FK），清理基行同 SQL Server 语义。
- **达梦**：`MDR.` 全限定，逻辑同 SQL Server（无实例，脚本审查 + sqlite/sqlserver 冒烟代替实测，沿用项目惯例）。
- 历史脚本 `V20260728001__Clean_registry_specification_orphans.sql` 保持原样（已应用库的历史记录；其语义在新模型下自然空转，新库重放亦无副作用）。

### 4.6 防再发机制（重点）

1. **守护测试** `StandardInheritanceConformanceTest`（**metadata-registry-web** 测试目录——只有 web 依赖全部业务模块、测试 classpath 才能扫全；纯反射、无 Spring 上下文）：
   - 硬断言 `RegistrySpecification` 的直接超类是 `Entity`（把本次偏差钉死为回归测试）；
   - 白名单断言：扫描 `io.github.matrixlane.metadataregistry` 全部 `@Entity` 类，凡"直接超类也是 `@Entity`"的具体类（即全部实体继承边，含 extends Item 与 Registrar extends Contact 等非 Item 边）必须命中白名单（`类 → 标准 Direct superclass 条款引用`）；新增未登记的继承边即测试失败。白名单含已核实条目（Context §6.4.2.3、Namespace §7.3.3、Naming_Convention §8.4.4、Constraint_Set §9.4.5、Registrar §9.4.7、Registration_Authority §9.4.6、Administered_Item §9.4.2、Attached_Item §9.4.3、Item_Mapping §11）与其余待审计条目的占位引用（指向 backlog）。
2. **AGENTS.md 规则增补**（数据模型分层规则下新增"实体继承规则"）：实体的继承（extends）关系必须有 ISO/IEC 11179 对应部分 "Direct superclass" 条款依据；新增/变更实体继承必须同步守护测试白名单并附标准条款引用，且依据须以 Milvus/本地 PDF 原文核对。
3. **记忆库**：session journal 记录本次事故链（孤儿→FK 失败→兜底→性能崩塌→根因是继承偏差）；LEARNING_BACKLOG 登记"其余 Item 子类继承符合性全面审计"。

### 4.7 测试更新

- 改写：`RegistrySpecificationServiceTest`（去 ItemAdministrationService mock/designations 构造；删两个 `cleanupOrphanRecords_*` 用例；新增「响应无 label/designations 字段、名称取自 name」断言）、`RegistrySpecificationControllerTest`（DTO 新签名）、`registrySpecForm.spec.ts`（去 designations fixture/断言）。
- 删除：`FederationBundleServiceTest.collectItems_excludesRegistrySpecification`（新模型下无法构造）。
- 保留：`RegistrationAuthorityDeletionServiceIntegrationTest`（`context_id=NULL` 回归保护）、`RegistryContextBootstrapServiceTest`、`InitWizardService*`、`ItemAdministrationServiceTest`/`DesignationServiceTest`/`DefinitionServiceTest`（默认语言链路）、`DataGovernanceReadinessServiceTest`。
- 新增：`StandardInheritanceConformanceTest`、迁移测试（scratch 真实库冒烟）。

## 5. 验证计划

1. `mvn clean package`（web 全量测试，既有基线失败集对照 stash 实验确认零新增）。
2. 前端 `vitest` + `vue-tsc` + build，dist→static 同步，重打 jar。
3. 真实 SQL Server 库：备份后应用 V20261009008，核验 FK 消失、基行/指称行清空、`registry_specification` 行与表 28 数据无损；重启应用冒烟（注册规范设置页读写、默认上下文、初始化向导幂等）。
4. SQLite scratch 全新库全量迁移重放 + 脏数据副本迁移冒烟。
5. `gitnexus detect-changes --scope all` 无 partial/truncated。
6. `openspec validate --all`。

## 6. 风险与对策

| 风险 | 对策 |
|---|---|
| Hibernate 历史 FK 名称不定 | 迁移按 `sys.foreign_keys` 动态解析，不硬编码名称 |
| sqlite 全新库与老库形态差异 | 迁移全守卫（存在才执行），新库空转 |
| 真实库 spec 的指称行被其他表引用（pairing/context 等） | 按 FK 依赖序先子后父删除；迁移在备份库上先验证 |
| 前端构建产物滞后 | 同步 dist→static 并重打 jar（吸取 10-08 部署事故教训） |
| 其余 Item 子类同类偏差 | 本次先以白名单测试锁定现状 + backlog 全量审计，防止"修一个漏一片" |
