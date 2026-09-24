# 设计:Dimensionality(维度)管理功能

详细设计见 `docs/superpowers/specs/2026-09-21-dimensionality-management-design.md`(Superpowers 设计稿,含标准依据与决策记录 D1–D4)。本文档为 OpenSpec 变更级设计摘要。

## 标准依据(Milvus 检索 ISO/IEC 11179-31:2023 原文核实)

| 条目 | 内容 | 落地 |
|---|---|---|
| §7.5.2.3.1 | Dimensionality 是 Concept 子类 | 实体已继承 Concept;管理功能复用 ConceptContentApplier/ItemAdministrationService |
| Table 22 | `coordinate_indicator` 0..1 Boolean:TRUE=坐标/FALSE=区间/NULL=不适用(非物理单位) | 请求/响应透传 Boolean 三态,前端三态 select |
| §7.5.3.1 | `dimensionality_measure_class` 0..\* ↔ 0..\* | 请求 `measureClassIds`,响应 `measureClasses[{id,label}]`,按 id 批量加载校验 |
| §7.5.2.3.5 | 须 designated/defined | 不硬编码,与 MeasureClass 一致交由 Constraint_Set |
| Table 2 | `Conceptual_Domain.dimensionality` 0..1 | DTO/Service/页面接线 |
| §7.4.2.1.5.2 / §7.4.5.3 | CD 指定维度后 VD 的 UOM 必须一致 | ValueDomainAssociationValidator 应用层校验(交集非空) |

## 技术设计

### 后端(模板:MeasureClassController/Service)

```
web/repository/DimensionalityRepository      JpaRepository + JpaSpecificationExecutor
web/dto/dimensionality/DimensionalityRequest sourceUri/designations/definitions/registration/coordinateIndicator/measureClassIds
web/dto/dimensionality/DimensionalityResponse 同 ConceptResponse 形态 + coordinateIndicator + measureClasses[MeasureClassRef(id,label)]
web/service/DimensionalityService            list(q,pageable)/get/create/update/delete + 删除保护(countByDimensionalityId)
web/controller/DimensionalityController      /api/v1/dimensionalities,Tag「9. 域 - 维度」
```

- 关键词搜索复用 MeasureClassService.byKeyword 形态(指称 sign 忽略大小写包含)。
- 删除保护:`ConceptualDomainRepository` 增加 `long countByDimensionality_Id(UUID)`,被引用时抛 BusinessException(含引用数);`dimensionality_measure_class` 由拥有方随删清理。
- 迁移 `V20260921001__Dimensionality_coordinate_indicator_nullable`(sqlite 重建表/sqlserver、dm 守卫式 ALTER):存量列 `coordinate_indicator NOT NULL` 与 Table 22 三态相悖,"不适用"维度无法落库;sqlite 已验证,sqlserver/dm 待实测。

### ConceptualDomain 接线

- Request 末尾追加 `UUID dimensionalityId`;Response 末尾追加 `UUID dimensionalityId, String dimensionalityLabel`(扁平,决策 D4)。
- Service:`applyDimensionality` 于 create/update 中调用(空=置 null;非空=校验存在后挂载);`toResponse` 回填;`rehydrate` 已拷贝维度,天然保留。

### 一致性校验

`ValueDomainAssociationValidator`:

- `VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN`:CD.dimensionality 与 VD.unitOfMeasure 均非空时,`uom.measureClasses ∩ dimensionality.measureClasses` 必须非空。
- `VALUE_DOMAIN_TO_UNIT_OF_MEASURE`:对称校验。
- 任一侧缺失不校验("指定时"才约束)。

### 前端

- `views/dimensionality/DimensionalityList.vue`:DataTablePage + ItemFormDialog(`item-type="dimensionality"`),`#fields` 槽放坐标指示三态 select 与计量类多选(SmartItemPicker `item-types=['MeasureClass']`);列:标识符/名称/坐标指示/计量类数。
- `api/resources.ts` 增加 `dimensionalityApi = crud('/dimensionalities')`。
- `router/index.ts`:路由 component 换为真实页面;注册表 status `implemented`。
- i18n:`meta.itemType.dimensionality`(zh 维度 / en Dimensionality)。
- `ConceptualDomainList.vue` `#fields` 槽新增维度 SmartItemPicker 单选,提交透传 `dimensionalityId`,编辑回填 id+label。

### 决策

- D1 coordinateIndicator 保持 Boolean 三态(标准原文如此),不引入枚举。
- D2 删除保护仅 Dimensionality→ConceptualDomain 方向;MeasureClass 既有行为不动。
- D3 一致性校验走应用层关联校验器(项目既定模式),不加 DB 约束。
- D4 CD 响应用扁平 dimensionalityId/dimensionalityLabel。
