# Dimensionality（维度）管理功能设计

- 日期：2026-09-21
- 变更：`add-dimensionality-management`
- 状态：已确认（用户指令"完成维度 Dimensionality 部分的开发"，auto 模式按完整交付推进）

## 背景与依据

`Dimensionality`/`MeasureClass` 实体早已落地（11179-31 审计 dc5a3da），`ConceptualDomain.dimensionality`（0..1）属性与 FK 也已补齐，但：

- 后端没有 Dimensionality 的 Repository/Service/Controller——无任何管理 API；
- 前端 `/dimensionality` 路由指向 `PlaceholderPage`，注册状态 `planned`，菜单项已存在但不可达内容；
- `ConceptualDomain` 的 Request/Response DTO 未暴露 dimensionality，页面上无法选择——维度建出来无处挂接。

标准依据（经 Milvus 检索 ISO/IEC 11179-31:2023 原文核实）：

- §7.5.2.3 Dimensionality 是 Concept 子类；Table 22 唯一附加属性 `coordinate_indicator`（0..1 Boolean，条件属性）：TRUE=坐标测量（物理单位时必须指定）、FALSE=区间测量、NULL=非物理单位（解释为 Not applicable）；
- §7.5.3.1 `dimensionality_measure_class`：Dimensionality 0..\* ↔ Measure_Class 0..\*；
- §7.5.2.3.5：Dimensionality 须被 designated/defined（由 Constraint_Set 指示器强制，与 MeasureClass 现状一致，不硬编码）；
- Table 2：`Conceptual_Domain.dimensionality` 0..1（"expression of measurement without units"）；
- §7.4.2.1.5.2 + §7.4.5.3 一致性：概念域指定维度后，基于它的值域所指定的计量单位必须与该维度一致（经 measure class 判定）。

## 方案

采用与 MeasureClass 完全同构的三层交付（MeasureClassController/Service 为模板），外加 ConceptualDomain 接线与一致性校验。

### 1. 后端 Dimensionality CRUD

- `web/repository/DimensionalityRepository`：`JpaRepository<Dimensionality, UUID>` + JpaSpecificationExecutor。
- `web/dto/dimensionality/DimensionalityRequest`：`sourceUri, designations, definitions, registration, coordinateIndicator(Boolean), measureClassIds(List<UUID>)`。
- `web/dto/dimensionality/DimensionalityResponse`：Concept 通用字段（同 ConceptResponse 形态：id/label/sourceUri/designations/definitions/meta）+ `coordinateIndicator` + `measureClasses`（`List<MeasureClassRef>`，元素为 `record MeasureClassRef(UUID id, String label)`）。
- `web/service/DimensionalityService`：list（关键词按指称 sign 忽略大小写包含，复用 MeasureClassService.byKeyword 形态）/get/create/update/delete；create/update 用 `ConceptContentApplier.applyConcept` 写入公共内容，再应用 coordinateIndicator 与 measureClasses（按 id 批量加载，不存在则 ResourceNotFoundException）。
- `web/controller/DimensionalityController`：`/api/v1/dimensionalities`，Tag「9. 域 - 维度」，端点形态与 MeasureClassController 一致。
- 删除保护：被 `conceptual_domain.dimensionality_id` 引用时拒绝删除并返回含引用数的业务错误（`ConceptualDomainRepository` 增加 `long countByDimensionalityId(UUID)` 派生查询）；dimensionality_measure_class 由拥有方 JPA 管理随删清理。

### 2. ConceptualDomain 接线

- `ConceptualDomainRequest` + `UUID dimensionalityId`（可空=清除）；`ConceptualDomainResponse` + `UUID dimensionalityId, String dimensionalityLabel`。
- `ConceptualDomainService`：create/update 时 `applyDimensionality`（dimensionalityId 非空则校验存在并挂载；空则置 null）；`toResponse` 回填；`rehydrate` 已拷贝，无需改。
- 影响分析：ConceptualDomainService 上游仅 ConceptualDomainController（GitNexus impact：LOW，epistemic exact）。

### 3. 一致性校验（§7.4.2.1.5.2 / §7.4.5.3）

`ValueDomainAssociationValidator` 扩展：

- `VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN`：target CD 有 dimensionality 且 source VD 已挂 unitOfMeasure 时，UOM 的 measureClasses 与 dimensionality 的 measureClasses 交集必须非空，否则拒绝并给出标准条款提示；
- `VALUE_DOMAIN_TO_UNIT_OF_MEASURE`：对称校验（VD 已挂 CD.dimensionality 时，新 UOM 必须一致）；
- CD.dimensionality 或 VD.unitOfMeasure 任一侧缺失时不校验（标准约束以"指定时"为前提）。

### 4. 前端

- `views/dimensionality/DimensionalityList.vue`：DataTablePage + ItemFormDialog（`item-type="dimensionality"`）；`#fields` 槽：coordinateIndicator 三态 select（是/否/不适用）、measureClasses 多选（SmartItemPicker，`item-types=['MeasureClass']`）；列：标识符/名称/坐标指示/计量类数。
- `api/resources.ts`：`dimensionalityApi = crud('/dimensionalities')`。
- `router/index.ts`：路由指向真实页面；路由注册表 `dimensionality` 状态 `planned` → `implemented`。
- `i18n`：`meta.itemType.dimensionality`（zh 维度/en Dimensionality）；`nav.dimensionality` 已存在无需新增。
- `ConceptualDomainList.vue` `#fields` 槽新增维度单选（SmartItemPicker，`item-types=['Dimensionality']`），提交透传 `dimensionalityId`；编辑回填 id+label。

### 5. 测试与验证

- 新增 `DimensionalityServiceTest`（CRUD、三态、measureClasses 挂载/移除、删除保护、引用不存在报错）。
- `ConceptualDomainServiceTest` 增补 dimensionality 挂载/清除/非法 id 用例。
- `ValueDomainAssociationValidator` 对应一致性校验用例（现有测试类增补）。
- `mvn -pl metadata-registry-web -am test` 全量回归；前端 `npm run build` + 相关 vitest；`-Dopenapi.export=true` 再生成 `docs/technical/api/openapi.json`。

### 6. 迁移修正（验证期发现）

- 存量库与 sqlite 基线中 `dimensionality.coordinate_indicator` 仍为 `NOT NULL`（审计 #7 只改了实体），与 Table 22 三态（NULL=Not applicable）相悖，"不适用"维度无法落库。
- 新增三方言迁移 `V20260921001__Dimensionality_coordinate_indicator_nullable`（sqlite 重建表，sqlserver/dm 守卫式 ALTER）。SQLite 已手工验证生效（notnull=0、数据保留）；sqlserver/dm 待真实环境实测（KNOWN_FAILURES 惯例）。
- 另核实：概念域 PUT 在 SQLite 上触发 64 表 join 上限（无维度参与亦复现）为既有平台限制，与本变更无关。

## 决策记录

- D1 coordinateIndicator 用 Boolean 三态（null=不适用），与实体一致，不用枚举——标准即如此定义。
- D2 删除保护仅加 Dimensionality→ConceptualDomain 入向引用；MeasureClass 删除保护不在本次范围（既有行为不动）。
- D3 一致性校验放应用层关联校验器（项目既定模式：valid_period/datatype 先例），不改 DB 约束。
- D4 CD 响应用扁平 `dimensionalityId/dimensionalityLabel` 而非嵌套 DTO——表单回显仅需二者，列表无展开需求。
- D5 dimensionalityId 全量语义：null=清除（与 sourceUri 等一致）；类型切换保留维度需请求重发同一 id（前端表单天然满足）。
