# 设计：移除"映射方案"并按 ISO/IEC 11179-3:2023 第 11 章重建 Item_Mapping

日期：2026-09-20
状态：待确认
关联审计：`docs/audits/iso11179-clause11-itemmapping-audit-2026-09-01.md`（含 2026-09-01 用户决策"归 MDR 并立项重建"）

## 1. 背景与标准依据

### 1.1 问题

用户询问"映射方案(Mapping Scheme)"是否存在于 ISO/IEC 11179-3 原文。经 Milvus 对标准全文（`anythingllm_metadata_registry` 集合，`ISO_IEC 11179-3-2023.pdf`）检索核实：

- 第 11 章正式名称为 **"Item_Mapping package"**（11.1，第 74 页），映射元模型区域只含：
  - `Item_Mapping` 类（11.3.1）——**直接超类为 Item**，属性（Table 32）：`degree`（1）、`description`（0..1）、`transformation_rule`（0..1）、`transformation_rule_notation`（0..1，transformation_rule 存在时必填）；
  - `Subject_Mapping` / `Object_Mapping` 关联类（11.4）——绑定 Item_Mapping 与 1..* 个 Item，属性 `mapping_variable_name`（0..1）；
  - `mapping_hierarchy` 自反关联（11.5）——0..1 个 superset Item_Mapping 对 0..* 个 subset Item_Mapping；
  - `Item_Mapping_Degree` 枚举（11.6，Table 35）——14 个值：`related to`、`same as`、`semantically equivalent`、`semantically similar`、`derived from`、`has derivation`、`broader than`、`narrower than`、`child of`、`parent of`、`contains`、`contained in`、`instance of`、`has instance`；标准明示实现可扩展。
- 全文检索 `Mapping_Scheme` / `mapping scheme` —— **零命中**。

结论：**"映射方案"不是标准概念**，是本项目自创的分组容器（带 version lineage 与 DRAFT/PUBLISHED/ARCHIVED 状态机，独立于注册生命周期）。

### 1.2 用户指令

> 如果不存在，那么从现有的界面中取消掉，重新回归 Item_Mapping 来设计功能。

即两步：(1) 移除"映射方案"；(2) 映射功能回归标准 Item_Mapping 模型重新设计。

### 1.3 关键现状（已核实）

- 所有映射相关表在生产/开发库中**均为空**（item_mapping、mapping_scheme、item_mapping_subject/object、mapping_subscription、fhir_element_mapping 行数均为 0）——**无数据迁移负担**。
- DB 层无任何指向 `mapping_scheme` 的外键；JPA 层 `ItemMapping.mappingScheme` 为可空 `@ManyToOne`（硬耦合，必须同步改）。
- 仅服务 MappingScheme 的功能（质量报告 `MappingQualityService`、版本 diff `MappingDiffService`、SCHEME 订阅）无任何其他下游消费者，可随方案一并移除。
- `fhir_element_mapping.mapping_scheme_id` 为裸 UUID 列（无 FK、无 Controller/DTO/前端使用），删除后仅残留孤儿值，随列一并删除。
- `interop_mapping` 与 MappingScheme 无关联，不受影响。
- 条目基础设施（`metadata-registry-common-facilities`）：`Item` 为抽象 JOINED 根（表 `item`），具体条目类型 `extends Item` 子表共享主键；注册信息由独立 `AdministeredItem` 外壳承载（`identified_item.item_id` 1:0..1）；`ItemAdministrationService.ensureAdministeredItem(Item, ItemRegistrationRequest, ...)` 为统一注册管线入口。
- `metadata-registry-item-mapping` 模块 pom **已依赖** common-facilities，重建无需改 pom。
- 已知 JPA 陷阱：`Item` 是 JOINED 根且子类众多，所有指向 `Item` 的关联必须 `FetchType.LAZY`（Hibernate 默认 EAGER 会多态连接全部子类表，超出 SQLite FROM 项上限）。

## 2. 候选方案对比

### 方案 A：彻底移除 MappingScheme 全栈 + 同 change 按第 11 章重建（推荐）

一次性删除 MappingScheme 的前后端全部代码与表，并在同一 change 内完成 Item_Mapping 标准重建与新管理界面。

- 优点：中间无"映射功能真空期"；所有映射表为空，无数据迁移风险；与 2026-09-01 审计决策（"归 MDR 并立项重建"、遵从目标 "Basic Registry with mapping"）完全一致；一次验证一次归档。
- 缺点：改动面大（需同步处理三方言迁移与前端两页面）。

### 方案 B：仅界面移除 + 后端标记废弃，重建另立 change

只删前端页面/菜单/路由，后端 MappingScheme 保留 `@Deprecated`，重建后续 change 再做。

- 优点：本次改动小。
- 缺点：非标准模型继续留在代码库与 ext schema 中；与用户"重新回归 Item_Mapping 来设计功能"的当次指令不符（用户要求现在即重建）；留下死代码与死 API。

### 方案 C：拆成"移除""重建"两个 OpenSpec change 顺序执行

- 优点：每步独立验证、独立归档，评审面小。
- 缺点：流程开销翻倍；中间态映射模块只剩无 UI 的 API；当前映射功能实际仅有方案页面可触达（映射条目本身无创建 UI），真空期无实际影响但收益也低。

**推荐方案 A**。理由：用户当次指令已涵盖两步；审计决策已预批准重建方向；空表现状使"彻底重建"代价最低；MappingScheme 的专属功能（quality/diff/SCHEME 订阅）无下游消费者，无断裂风险。

## 3. 方案 A 详细设计

### 3.1 阶段 1：移除 MappingScheme

**后端删除清单**

`metadata-registry-item-mapping` 模块：
- `entity/MappingScheme.java`、`repository/MappingSchemeRepository.java`、`enums/MappingSchemeStatus.java`
- `quality/MappingQualityService.java`（+其测试）
- `entity/FhirElementMapping.java` 的 `mappingSchemeId` 字段；`FhirElementMappingRepository.findByMappingSchemeId`；`FhirMappingConflictDetector.detectConflictsByScheme`（+相关测试）

`metadata-registry-web` 模块：
- `controller/MappingSchemeController.java`、`controller/MappingQualityController.java`
- `service/MappingSchemeService.java`、`service/MappingDiffService.java`
- `web/dto/mapping/`：`MappingSchemeRequest`、`MappingSchemeResponse`、`MappingSchemeStatusTransitionRequest`、`MappingVersionInfo`、`MappingDiffEntry`、`MappingDiffResponse`、`MappingQualityReportResponse`
- `notification/` 整个映射订阅功能：`MappingSubscriptionService`、`MappingSubscriptionController`、`MappingSubscription` 实体、`MappingSubscriptionRepository`、`SubscriptionType` 枚举及其测试（用户决策 2026-09-20：订阅功能整体移除，后续作为更全面的独立功能重新设计）
- `config/CaseInsensitiveEnumConfig` 移除 `MappingSchemeStatus` 注册
- `service/database/DatabaseEnvironment` ext 表清单移除 `mapping_scheme`
- `resources/orm/orm-team.xml` 移除 MappingScheme 实体行（`OrmTeamXmlTest` 同步）
- 测试：`MappingSchemeControllerTest`、`MappingSchemeServiceTest`、`MappingDiffServiceTest`、`MappingSchemeTest`、`MappingSubscriptionControllerTest`、`EnumValueTest` 相关断言等

连带修改：
- `ItemMapping` 实体删除 `mappingScheme` 字段与相关构造器参数
- `ItemMappingRepository` 删除 scheme 相关查询（`findByMappingSchemeId`、`countByMappingSchemeId*`、`findDistinct*BySchemeId` 等 7 个方法）

**数据库迁移（新脚本，三方言；遵循 V20260901015/V20260918001 约定）**

sqlite（`V20260920001__Drop_mapping_scheme.sql`）：
```sql
DROP TABLE IF EXISTS mapping_scheme;
ALTER TABLE item_mapping DROP COLUMN mapping_scheme_id;   -- SQLite 3.35+，不用 IF EXISTS 守卫（项目先例）
ALTER TABLE fhir_element_mapping DROP COLUMN mapping_scheme_id;
DROP INDEX IF EXISTS idx_fem_scheme;
```

sqlserver（同名脚本）：schema 限定 + 存在性守卫——
```sql
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='ext' AND TABLE_NAME='mapping_scheme') DROP TABLE ext.mapping_scheme;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='item_mapping' AND COLUMN_NAME='mapping_scheme_id') ALTER TABLE mdr.item_mapping DROP COLUMN mapping_scheme_id;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='ext' AND TABLE_NAME='fhir_element_mapping' AND COLUMN_NAME='mapping_scheme_id') ALTER TABLE ext.fhir_element_mapping DROP COLUMN mapping_scheme_id;
```

dm 方言：同 sqlserver 结构（按现有 dm 脚本方言惯例）。

**前端删除清单**
- 删除 `views/mapping/MappingSchemeList.vue`、`views/mapping/MappingDiffViewer.vue`
- `api/resources.ts` 删除 `mappingSchemeApi`（:222-244）
- `router/index.ts` 删除 `mapping-schemes` 与 `mapping-schemes/:id/diff` 两条路由及路由清单两行（:182-191、:346-347）
- i18n 删除 `mappingScheme` 文案块（zh-CN :131-148、en-US 对应块）

### 3.2 阶段 2：按第 11 章重建 Item_Mapping

**数据模型（`metadata-registry-item-mapping` 模块）**

1. 新枚举 `enums/MappingDegree.java`：14 个标准值（`RELATED_TO`、`SAME_AS`、`SEMANTICALLY_EQUIVALENT`、`SEMANTICALLY_SIMILAR`、`DERIVED_FROM`、`HAS_DERIVATION`、`BROADER_THAN`、`NARROWER_THAN`、`CHILD_OF`、`PARENT_OF`、`CONTAINS`、`CONTAINED_IN`、`INSTANCE_OF`、`HAS_INSTANCE`），`@Enumerated(STRING)`，存入 `CaseInsensitiveEnumConfig` 列表（大小写不敏感读取）。旧 `MappingType` 4 值枚举删除。
2. `ItemMapping extends Item`（JOINED，表 `item_mapping`，主键 `id` 兼 FK → `item`）：
   - `degree`（`@Enumerated(STRING)`，not null）
   - `description`（0..1，length 2000）
   - `transformationRule`（0..1，length 4000）
   - `transformationRuleNotation`（0..1，length 255；应用层校验：transformationRule 存在时必填，对应 Table 32 条件）
   - 删除 `mappingScheme`、`createdAt`（创建时间由 AdministeredItem.creationDateTime 承载）
   - 继承获得：designations / definitions / slots 集合、经 `IdentifiedItem` 配对的注册外壳能力
3. `ItemMappingSubject` 重建为标准关联类形态： `@ManyToOne(LAZY) ItemMapping itemMapping`、`@ManyToOne(LAZY) Item item`、`mappingVariableName`（0..1，length 255）。删除 `itemId` 裸 UUID 与 `externalUri` 字段。
4. `ItemMappingObject` 同上。
5. 新实体 `MappingHierarchy`（表 `mapping_hierarchy`）：`@ManyToOne(LAZY) ItemMapping supersetMapping`、`@ManyToOne(LAZY) ItemMapping subsetMapping`；应用层校验防止自引用成环（superset ≠ subset，禁止直接环）。
6. **外部 URI 能力删除**：标准 11.4 要求 subject/object 为注册 Item；外部标准（如 FHIR）的互操作映射由 ext 侧独立功能（`fhir_element_mapping`、`interop_mapping`）承担。`ItemMappingService.createItemToExternalMapping` / `createExternalToItemMapping` 及对应查询删除。
7. **subject/object 存在性校验**：应用层校验被引用 Item 存在（未强制要求 AdministeredItem 外壳——标准字面为 Item；映射本身也可在注册流程完成前创建，与标准 NOTE 1 "Item_Mapping 可被 administered" 一致）。

**服务与 API（`metadata-registry-web` 模块）**

- `ItemMappingService`（item-mapping 模块）保留网络图能力并适配新模型；条目 CRUD 服务逻辑放 web 模块 `ItemMappingCrudService`（参照现有条目服务惯例）。
- 新 `ItemMappingController`：`/api/v1/item-mappings`
  - `GET` 分页列表（过滤：subjectItemId、objectItemId、degree）
  - `POST` 创建（subjectItemId + subjectVariableName、objectItemId + objectVariableName、degree、description、transformationRule、transformationRuleNotation、可选 designation/identifier 注册字段——复用 `ItemRegistrationRequest` 走 `ensureAdministeredItem`）
  - `GET /{id}`、`PUT /{id}`、`DELETE /{id}`
  - `GET /{id}/hierarchy`、`PUT /{id}/hierarchy`（设置/替换 superset）
- 新 DTO：`ItemMappingRequest`、`ItemMappingResponse`、`ItemMappingHierarchyRequest`（放 `web/dto/mapping/`）。
- **注册生命周期**：复用现有统一条目注册管线（ItemMapping 作为 Item 子类自动纳入既有注册流程/端点），不新建映射专用注册端点。
- 不动：映射网络图（`/mappings/network`）、影响分析、interop 匹配结果管理。映射订阅功能整体移除（用户决策 2026-09-20），后续作为独立功能重新设计。

**数据库迁移（`V20260920002__Rebuild_item_mapping_clause11.sql`，三方言）**

- `item_mapping` 重建：`DROP TABLE` 后按新模型 `CREATE TABLE item_mapping (id BLOB/UNIQUEIDENTIFIER PRIMARY KEY REFERENCES item(id), degree VARCHAR(32) NOT NULL, description VARCHAR(2000), transformation_rule VARCHAR(4000), transformation_rule_notation VARCHAR(255))`（sqlserver/dm 归 `mdr` schema）。
- `item_mapping_subject` / `item_mapping_object` 重建：`(id PK, item_mapping_id NOT NULL REFERENCES item_mapping(id), item_id NOT NULL REFERENCES item(id), mapping_variable_name VARCHAR(255))` + 查询索引。
- 新表 `mapping_hierarchy (superset_mapping_id NOT NULL REFERENCES item_mapping(id), subset_mapping_id NOT NULL REFERENCES item_mapping(id), PRIMARY KEY(superset_mapping_id, subset_mapping_id))`。
- 删除旧索引（随表重建自动消失；`item_mapping` 旧索引需显式 drop 若不在表重建范围内）。

**前端重建**

- 新页面 `views/mapping/ItemMappingList.vue`：列表（subject 条目、degree、object 条目、transformation rule 摘要、注册状态）+ 筛选（条目/degree）+ 新建/编辑对话框（subject/object 条目选择器复用现有条目选择组件、degree 下拉 14 值、variable name、transformation rule + notation、description）+ hierarchy 操作入口。
- `api/resources.ts` 新增 `itemMappingApi`。
- 路由：`/item-mappings`（name `ItemMappings`，title"项目映射"）替换原两条；路由清单表同步。
- `AppLayout.vue` 菜单"项目映射模块"组保留，菜单项改指 `/item-mappings`；`nav.itemMapping` 文案改"项目映射"（en "Item Mapping"）。
- i18n：新增 `itemMapping` 文案块（zh-CN/en-US）。
- `e2e/menu.spec.ts` 菜单断言更新。

**文档同步（签入前）**
- `README.md`（页面清单）
- `docs/architecture/ARCHITECTURE.md`（映射模块结构变化大，重新生成而非手工改写）
- `docs/technical/api/openapi.json`（删除 `/mapping-schemes*` 端点，新增 `/item-mappings*`）
- `openspec/specs/standardize-menu-layout/spec.md`（菜单指向更新）

### 3.3 风险与缓解

| 风险 | 缓解 |
| --- | --- |
| JOINED 根多态查询超出 SQLite FROM 项上限 | 所有 Item 关联强制 `FetchType.LAZY`；网络图查询用定向 fetch/投影 |
| 三方言迁移语法差异（SQLite 无 schema、DROP COLUMN 无 IF EXISTS；达梦未实测） | 严格套用现有脚本先例；达梦脚本标注待实测（KNOWN_FAILURES 已有同款遗留） |
| 删除 externalUri 能力影响外部互操作预期 | FHIR/interop 走独立 ext 链路，不经过 item_mapping；设计文档明示该取舍 |
| 旧前端构建产物残留 | 重新构建 `metadata-registry-ui`，产物随签入更新 |

## 4. 验证计划

1. 后端：`./mvnw -pl metadata-registry-item-mapping,metadata-registry-web -am test`（单元/集成测试全绿；新增 ItemMapping 重建测试：degree 校验、transformation_rule_notation 条件必填、subject/object 存在性、hierarchy 防环、CRUD 端到端）。
2. 迁移重放：全新 SQLite 库从 baseline 重放到最新版本无报错；现有 `metadata_registry.db` 启动自动迁移成功。
3. 前端：`npm run build` 通过；`e2e/menu.spec.ts` 通过。
4. 端到端 smoke：启动应用，新库下创建命名空间→创建两个数据元→创建 Item_Mapping（same as）→注册状态流转→网络图可见。
5. 提交前 `detect-changes`（GitNexus）无 partial/truncated。

## 5. 决策点（请用户确认）

1. **方案选择**：推荐方案 A（一次移除 + 重建）。B/C 为备选。
2. **外部 URI 取舍**：subject/object 严格按标准只引用注册 Item，删除 externalUri 扩展（推荐）；如需保留外部 URI 能力请告知。
3. **订阅功能**：整体移除映射订阅（SCHEME + ITEM + `mapping_subscription` 表，用户 2026-09-20 已裁定：订阅功能以后作为更全面的功能单独开发）。
