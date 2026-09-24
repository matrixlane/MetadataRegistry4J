# Tasks：移除映射方案并按第 11 章重建 Item_Mapping

## 1. 后端移除 MappingScheme

- [x] 1.1 删除 item-mapping 模块 scheme 专属代码（`entity/MappingScheme`、`repository/MappingSchemeRepository`、`enums/MappingSchemeStatus`、`quality/MappingQualityService` 及其测试），验证 `./mvnw -pl metadata-registry-item-mapping compile` 通过
- [x] 1.2 删除 web 模块 scheme 专属代码（`MappingSchemeController`、`MappingQualityController`、`MappingSchemeService`、`MappingDiffService`、`web/dto/mapping/` 下 7 个 scheme/quality/diff DTO 及其测试），验证 `./mvnw -pl metadata-registry-web compile` 通过
- [x] 1.3 `ItemMapping` 删除 `mappingScheme` 字段与构造器参数；`ItemMappingRepository` 删除 7 个 scheme 查询方法；`FhirElementMapping` 删除 `mappingSchemeId` 及 Repository/ConflictDetector 的 scheme 方法；**整个映射订阅功能移除**（`notification/` 包：Service/Controller/实体/Repository/`SubscriptionType` 及全部测试，用户决策 2026-09-20），验证两模块 `./mvnw -pl metadata-registry-item-mapping,metadata-registry-web compile` 通过且关联测试已同步删除
- [x] 1.4 清理配置引用：`CaseInsensitiveEnumConfig` 移除 `MappingSchemeStatus`、`DatabaseEnvironment` ext 表清单移除 `mapping_scheme`/`mapping_subscription`、`orm-team.xml` 移除两行；验证 `OrmTeamXmlTest` 已通过（EXPECTED 清单同步更新）

## 2. 数据库迁移：移除 mapping_scheme（三方言）

- [x] 2.1 编写 sqlite `V20260920001__Drop_mapping_scheme.sql`（DROP TABLE IF EXISTS mapping_scheme / mapping_subscription；DROP INDEX idx_fem_scheme；ALTER TABLE item_mapping / fhir_element_mapping DROP COLUMN mapping_scheme_id）
- [x] 2.2 编写 sqlserver 同名脚本（schema 限定 + INFORMATION_SCHEMA/sys.indexes 存在性守卫）
- [x] 2.3 编写 dm 同名脚本（PL/SQL 块守卫 + 动态 SQL，标注待达梦实例实测）
- [x] 2.4 验证：SQLite 现有库迁移 PASS（Flyway 应用 V20260920001/20002，schema 检查通过）；SQL Server 真实库 PASS（含 Hibernate FK 预清理）；全新库全量重放被既有 V20260919002 阻塞（非本次引入），以 baseline+两脚本模拟重放验证目标 schema

## 3. Item_Mapping 标准重建（后端，TDD）

- [x] 3.1 新增 `enums/MappingDegree`（标准 14 值 + `isSymmetric()`）并注册进 `CaseInsensitiveEnumConfig`；删除 `MappingType`；枚举测试通过
- [x] 3.2 `ItemMapping extends Item`（JOINED，degree 必填 + description + transformationRule + transformationRuleNotation，subject/object `@OneToOne` 关联侧写）；`ItemMappingSubject`/`ItemMappingObject` 重建为关联类（ owning FK + `@ManyToOne(LAZY) Item` + mappingVariableName，item_mapping_id 唯一约束）；新增 `MappingHierarchy` 实体与 Repository
- [x] 3.3 `ItemMappingService` 重写（createMapping/updateMapping/deleteMapping 含层级清理/网络图），删除 external URI API 与非标准的互斥矩阵/环检测；适配 `ImpactAnalysisService`（投影查询 + degree 方向判定）；模块测试 322 全绿
- [x] 3.4 web 模块新增 `ItemMappingCrudService` + `ItemMappingController`（`/api/v1/item-mappings` 分页过滤 GET/POST/PUT/DELETE + 层级 GET/PUT）+ `ItemMappingRequest`/`ItemMappingResponse`/`ItemMappingHierarchyRequest` DTO；`ItemRepository.existsItemById` 轻量存在性检查；`IdentifiedItemRepository` 批量注册状态查询
- [x] 3.5 应用层校验与测试：subject/object 存在性（404）、transformationRule 存在时 notation 必填（400）、hierarchy 防自引用/防环；创建可选携带注册请求走 `ensureAdministeredItem`；新增 `ItemMappingControllerTest`/`ItemMappingCrudServiceTest`

## 4. 数据库迁移：重建 item_mapping（三方言）

- [x] 4.1 编写 sqlite `V20260920002__Rebuild_item_mapping_clause11.sql`（DROP 旧三表；重建 item_mapping(degree check 14 值)/subject/object + mapping_hierarchy；唯一索引 uk_ims_mapping/uk_imo_mapping/uk_mh_subset 与查询索引）
- [x] 4.2 编写 sqlserver 同名脚本（mdr schema + 内联 UNIQUE 约束）
- [x] 4.3 编写 dm 同名脚本（PL/SQL 块守卫 + 动态 SQL，标注待达梦实例实测）
- [x] 4.4 验证：SQLite 模拟全新库重放 PASS；SQL Server 真实库重建 PASS（先动态删 FK 再幂等重建）

## 5. 前端

- [x] 5.1 删除 `MappingSchemeList.vue`、`MappingDiffViewer.vue`、`mappingSchemeApi`、两条路由与清单行、`mappingScheme` i18n 块；`npm run build` 通过
- [x] 5.2 新增 `itemMappingApi` + `ItemMappingList.vue`（SmartItemPicker 条目选择、degree 14 值下拉、层级对话框）+ `/item-mappings` 路由 + 菜单指向 + `itemMapping` 文案块；`MappingNetworkView` 适配 degree 字段与 14 值过滤/配色；`npm run build` 通过
- [x] 5.3 `e2e/menu.spec.ts` 更新，`npx playwright test e2e/menu.spec.ts` 8 passed

## 6. 文档与整体验证

- [x] 6.1 `docs/technical/api/openapi.json` 从运行实例 /v3/api-docs 重新生成（197 路径，含 `/api/v1/item-mappings` 全套，无 `mapping-schemes*` 残留）
- [x] 6.2 更新 `README.md` 页面清单与模块注释；`docs/architecture/ARCHITECTURE.md` 待 GitNexus 重建索引后重新生成（随提交阶段执行，避免用过期索引产出误导内容）
- [x] 6.3 整体验证：**item-mapping 322 tests 0 失败；web 1151 tests 0 失败（clean 构建）**；前端 build + e2e（menu.spec 8 passed）通过；SQLite 现有库迁移+API smoke PASS（建/查/层级/校验 400/删）；SQLite 全新库模拟重放 PASS（baseline+两脚本→目标 schema）；SQL Server 真实库 V20260920001/20002 应用成功、schema 精确收敛、应用启动于 8081；GitNexus detect-changes 无 partial/truncated（workspace 含多个未提交变更，risk=critical 为合并预期）
- [x] 6.4 过程中发现并修复：SQL Server/达梦上 Hibernate 会生成不可预测命名的 FK（迁移脚本先动态删 FK）；SQLite 上 Flyway DDL 回滚不可靠（脚本全幂等，任何半执行残留态可重跑收敛）；全新库全量重放被**既有** V20260919002 阻塞（语言扩展 change，非本次引入，已记录遗留）
- [x] 6.5 评审修正（用户 2026-09-20）：界面"条目"统一改回"项目"（与"项目映射模块"一致）；"映射度"改"项目映射程度"；degree 取值全链路对齐 ISO/IEC 11179-3 Table 35 标准小写值（JPA 转换器落库、JSON/查询参数标准值、迁移 CHECK 小写）。重验证：item-mapping 324 / web 1151 tests 全绿，前端 build+静态同步，SQL Server 库重放修正后迁移，openapi.json 重生成
- [x] 6.6 评审修正二（用户 2026-09-20）：subject/object 按标准 11.4 放开为 1..* 多对多关联类（每绑定携带 mapping_variable_name）。实体/仓储/服务/DTO/前端/测试全改；新迁移 V20260920003 三方言去唯一约束；修复 @Enumerated 覆盖 autoApply 转换器问题。重验证：item-mapping 337 / web 1153 全绿；SQLite 现有库迁移+多主体 API smoke 全通；SQL Server 唯一约束已除、8081 运行新代码；openapi.json 重生成
- [x] 6.7 评审修正三（用户 2026-09-20）：界面禁显 UUID 原则全应用彻查修复——共享 useItemLabelCache（业务标识+指称反查，占位不回退 UUID）；16 个前端文件修复；后端 interop 响应补数据集元素显示名；SmartItemPicker 回填失败占位。验证：337+1154 后端全绿、前端 build+vitest 198/198、grep 无残留、8081 部署、openapi 重生成、SQLite smoke PASS
- [x] 6.8 评审修正四（用户 2026-09-20）：编辑回填"未解析"根因修复（fetchById AbortController 并发互相取消→串行回填）；标签全局精简「标识 | 指称」；措辞"条目"→"项目"；每所选项目行红色✕快捷移除；+N 折叠清单 tooltip。验证：build+vitest 198/198、8081 部署新包、openapi 重生成
- [x] 6.9 评审修正五~七（用户 2026-09-20）：映射表格单元格只显示指称（结构化标签缓存 designationFor）；两列统一纵列悬浮层「标识 | 指称」；操作列 fixed 表头底色不一致根因（EP fixed 克隆表头未被套用全局表头色）全局根治（layout.scss + DataTablePage/InteropMappingList 对齐）。验证：build+vitest 198/198、8081 部署、openapi 重生成
