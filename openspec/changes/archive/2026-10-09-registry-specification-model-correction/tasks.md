# Tasks: registry-specification-model-correction

## 1. 实体纠偏（common-facilities）

- [x] 1.1 `RegistrySpecification.java`：`extends Item` → `extends Entity`；删除 `import ...core.Item`；确认 `@Entity`/隐式表名 `registry_specification`/`@StandardReference` 不变。验证：`mvn -pl metadata-registry-common-facilities compile` 通过（预期 ItemMapping 等其它子类不受影响）
- [x] 1.2 新增 `StandardInheritanceConformanceTest`（**metadata-registry-web** 测试目录——只有 web 的测试 classpath 覆盖全部业务模块；纯反射）：硬断言 `RegistrySpecification.getSuperclass() == Entity.class`；扫描 `io.github.matrixlane.metadataregistry` 包下全部 `@Entity` 类，"直接超类也是 @Entity"的具体类（不限于 extends Item——评审 M1 加宽，覆盖 Registrar extends Contact、AdministeredItem extends RegisteredItem 等全部实体继承边）必须命中白名单 Map（条目=类→标准条款引用；已核实 9 条含 Context §6.4.2.3、Namespace §7.3.3、NamingConvention §8.4.4、ConstraintSet §9.4.5、Registrar §9.4.7、RegistrationAuthority §9.4.6、AdministeredItem §9.4.2、AttachedItem §9.4.3、ItemMapping §11；其余条目占位引用指向全量审计 backlog），未命中即 `fail`。先写测试看它红（实体仍是 extends Item 时 3 用例全红），改完转绿。注：`ClassificationSchemeItemRelationshipType` 在本仓库无对应实体类，未登记

## 2. 后端服务与 DTO（web 模块）

- [x] 2.1 `RegistrySpecificationService`：删除 `cleanupOrphanRecords()`（含 `@EventListener` 注解与 javadoc）；删除 `ItemAdministrationService` 字段及 `applyDesignations`/`applyDefinitions`/`ensureAdministeredItem` 三处调用（`applyRequest`）；`createDefault` 删除 designations `TextLine` 构造与失效注释（`:150-151`、`:182-184`）；`toResponse` 删除 `label` 实参与 `mapDesignations`/`mapDefinitions`/`buildItemMeta` 三行
- [x] 2.2 `RegistrySpecificationService.list`：`ObjectClassService.byKeyword(keyword)`（`<T extends Item>` 泛型硬阻塞）改写为本服务内按 `name` 匹配的 `Specification<RegistrySpecification>`（关键词空→全量；匹配 `name` 列，大小写不敏感按项目既有 Specification 惯例）
- [x] 2.3 `RegistrySpecificationRequest`：删除 `designations`/`definitions`/`registration` 字段及 `TextLine`/`ItemRegistrationRequest` import
- [x] 2.4 `RegistrySpecificationResponse`：删除 `label`/`designations`/`definitions`/`meta` 字段及相关 import
- [x] 2.5 `RegistrySpecificationRepository`：删除 `findOrphanIds()` 及 `@Query`、import 与注释
- [x] 2.6 `FederationBundleService`：删除 `import RegistrySpecification` 与 `collectItems()` 中的 `instanceof RegistrySpecification` 过滤；改写 `:106-111` javadoc 中 spec 段落（不再参与迁移由"类型上不可能"保证，`Namespace` 过滤保留）
- [x] 2.7 `MetadataSearchService`：删除 `itemTypeCaseExpr()` 中 `registry_specification` CASE 分支（`:108`）与 `typeToTable()` 中 `case "RegistrySpecification"`（`:408`）
- [x] 2.8 `MetadataSearchController`：`SUPPORTED_TYPES` 删除 `"RegistrySpecification"`（`:43`）
- [x] 2.9 `RegistrationAuthorityDeletionService`：保留 `:138` 的 `UPDATE registry_specification SET context_id=NULL`；修正 `:24`/`:129` 注释中「子表行先于父 item 行」的失效前提表述

## 3. 三方言迁移 V20261009008

- [x] 3.1 SQL Server `V20261009008__Decouple_registry_specification_from_item.sql`：经 `sys.foreign_keys`/`sys.foreign_key_columns` 动态解析 `mdr.registry_specification` 上引用 `mdr.item` 的 FK（OBJECT_ID 守卫，存在才删）；按依赖序删除存量 spec 的 designation 关联子行（`designation_definition_pairing`/`designation_context`/`designation_namespace`/`naming_convention_conformance`，各带存在性守卫）→ `designation` 行（`item_id ∈ registry_specification`）→ `item` 基行；全部 `mdr.` 全限定
- [x] 3.2 SQLite 同名迁移：裸表名；存在性守卫（新库空转）；若真实 SQLite 库核实存在声明式 FK 则按重建表模式重建 `registry_specification`（不带 FK）
- [x] 3.3 达梦同名迁移：`MDR.` 全限定，逻辑同 3.1（无实例，脚本审查 + 冒烟代替实测）
- [x] 3.4 真实库验证：SQL Server 备份库应用迁移（FK 消失、基行/指称行清空、`registry_specification` 行与表 28 数据无损）；SQLite scratch 全新库全量重放 + 含 spec 数据的脏副本冒烟；重放幂等（跑两遍不报错）

## 4. 前端（metadata-registry-ui）

- [x] 4.1 `RegistrySpecificationSettings.vue`：删除「指称与定义」Divider 与 `DesignationDefinitionSection`、`「注册标识」Divider 与 `ItemRegistrationSection`；删除关联 import/ref（`regSection`/`editingRaName`）/回显（`:276`、`:279-283`）/`validateTemplateVariables` 分支（`:298-302`）；`buildRegistrySpecPayload(form)` 只传 form
- [x] 4.2 `registrySpecForm.ts`：`RegistrySpecFormModel` 删 `designations`/`definitions`/`registration`；`ARRAY_FIELDS` 删两项；空表单初始化与 `applyRowToForm` 回显删三字段；删除 `toTextLinePayload()` 整函数；`buildRegistrySpecPayload` 去 `registrationPayload` 形参与载荷三行
- [x] 4.3 `api/system.ts`：删除死代码 `registrySpecificationApi`（引用不存在的端点、全仓库无引用）；`RegistrySpecification` interface 收敛为真实响应中被消费的字段（`id`/`name`/`defaultLanguageId`/`defaultLanguageIdentifier`）
- [x] 4.4 `SmartItemPicker.vue`：删除 `RegistrySpecification` 缩写/标签/标签色三处死映射
- [x] 4.5 `registrySpecForm.spec.ts`：删 designations 相关 fixture 与断言，对齐新表单模型
- [x] 4.6 `npm run build` 后将 `dist` 同步到 `metadata-registry-web/src/main/resources/static/`（沿用项目 dist→static 产物同步惯例）

## 5. 测试更新与新增

- [x] 5.1 `RegistrySpecificationServiceTest`：删除 `ItemAdministrationService` mock 与 designations 构造（`:58-77` 等）；删除两个 `cleanupOrphanRecords_*` 用例（`:285-304`）；新增断言：响应不含 label/designations/definitions/meta、名称取自 `name` 属性；`list` 关键词过滤按 name 匹配
- [x] 5.2 `RegistrySpecificationControllerTest`：DTO 构造对齐新签名（`:83-86`、`:112-120`、`:124-128`）
- [x] 5.3 `FederationBundleServiceTest`：删除 `collectItems_excludesRegistrySpecification`（`:110-121`，新模型下无法构造）
- [x] 5.4 `MetadataSearchServiceTest`：`KNOWN_TABLES` 同步删 `registry_specification`（`:173`）
- [x] 5.5 确认零改动测试保持绿：`RegistrationAuthorityDeletionServiceIntegrationTest`、`RegistryContextBootstrapServiceTest`、`InitWizardServiceOrderTest`/`InitWizardServiceIntegrationTest`、`ItemAdministrationServiceTest`/`DesignationServiceTest`/`DefinitionServiceTest`、`DataGovernanceReadinessServiceTest`

## 6. 规则与记忆（防再发）

- [x] 6.1 `AGENTS.md` 数据模型分层规则下新增「实体继承规则」：extends 关系必须有 ISO/IEC 11179 对应部分 Direct superclass 条款依据；新增/变更继承必须同步 `StandardInheritanceConformanceTest` 白名单并附标准条款引用；依据以 Milvus/PDF 原文核对
- [x] 6.2 `.superpowers-memory/session-journal/` 新增本次会话记录（事故链：错误继承→孤儿→FK 失败→兜底→10-09 启动挂起；纠偏与防再发三层机制）
- [x] 6.3 `.superpowers-memory/LEARNING_BACKLOG.md` 登记：其余 Item 子类继承符合性全量审计（15 子类逐一按 11179-3/31/32/33/35 核对 Direct superclass）

## 7. 全量验证

- [x] 7.1 `mvn clean package -DskipTests`（编译+打包）后 `mvn -pl metadata-registry-web -am test`：全量测试，既有基线失败集（Workspace/SegmentTemplate 家族）对照 stash 实验确认零新增
- [x] 7.2 前端 `npx vitest run` + `vue-tsc` + `build`
- [x] 7.3 真实 SQL Server 库备份→应用 V20261009008→重启应用冒烟：注册规范设置页读写、默认上下文 `/api/v1/contexts/default`、向导幂等（`exists()` 守卫）、默认语言链路
- [x] 7.4 SQLite scratch：全新库全量迁移重放 + 含 spec 数据副本迁移冒烟
- [x] 7.5 `gitnexus detect-changes --scope all`（CLI 兜底亦可）无 partial/truncated；`openspec validate --all` 通过
- [x] 7.6 重启后端实测 `/api/v1/startup/status` 就绪、前端登录链路可达

## 实施注记

- 工作树当前存在并行未提交改动（Sign/Text/ContactExtensionRepository 及 10-09 的 cleanupOrphanRecords 性能修复——本变更 2.1/2.5 将删除该代码，直接在本工作树进行，提交时按变更分组。
- 多模块验证必须 `-am`（本地仓库 jar 可能被并行流程改写）。
- jar 交付全程：同步 dist→static、重打胖 jar、杀旧进程、重启实测（吸取 10-08 部署事故教训）。
