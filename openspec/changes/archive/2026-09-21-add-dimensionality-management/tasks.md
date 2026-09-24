# Tasks: add-dimensionality-management

## 1. 后端 Dimensionality CRUD

- [x] 1.1 `DimensionalityRepository`(JpaRepository + JpaSpecificationExecutor)
- [x] 1.2 `DimensionalityRequest`/`DimensionalityResponse`(+`MeasureClassRef`),`web/dto/dimensionality/`
- [x] 1.3 `DimensionalityService`:list(q 指称包含)/get/create/update/delete;measureClassIds 批量加载校验;删除保护(`ConceptualDomainRepository.countByDimensionality_Id`,含引用数业务错误)
- [x] 1.4 `DimensionalityController` `/api/v1/dimensionalities`,Tag「9. 域 - 维度」
- [x] 1.5 三方言迁移 `V20260921001__Dimensionality_coordinate_indicator_nullable`(sqlite 重建表/sqlserver+dm ALTER 放宽):存量 `coordinate_indicator NOT NULL` 与 Table 22 三态(NULL=Not applicable)相悖,SQLite 已手工验证生效(notnull=0、数据保留);sqlserver/dm 守卫式脚本待真实环境实测(同 KNOWN_FAILURES 惯例)

## 2. ConceptualDomain 接线

- [x] 2.1 `ConceptualDomainRequest` + `dimensionalityId`;`ConceptualDomainResponse` + `dimensionalityId/dimensionalityLabel`
- [x] 2.2 `ConceptualDomainService.applyDimensionality`(create/update 挂载或清除,存在性校验);`toResponse` 回填;rehydrate 保留(已有)

## 3. 一致性校验(§7.4.2.1.5.2 / §7.4.5.3)

- [x] 3.1 `ValueDomainAssociationValidator`:VD→CD 与 VD→UOM 关联时,CD.dimensionality 与 VD.unitOfMeasure 均存在则 measureClasses 交集必须非空;错误提示含条款号

## 4. 前端

- [x] 4.1 `api/resources.ts` `dimensionalityApi = crud('/dimensionalities')`
- [x] 4.2 `views/dimensionality/DimensionalityList.vue`:列表 + ItemFormDialog(#fields:坐标指示三态 select + 计量类 SmartItemPicker 多选);列:标识符/名称/坐标指示/计量类数
- [x] 4.3 `router/index.ts`:路由指向真实页面;注册表 `dimensionality` → `implemented`
- [x] 4.4 i18n:`meta.itemType.dimensionality`(zh 维度 / en Dimensionality)
- [x] 4.5 `ConceptualDomainList.vue` #fields 槽:维度 SmartItemPicker 单选,提交 `dimensionalityId`,编辑回填

## 5. 测试与验证

- [x] 5.1 `DimensionalityServiceTest` 新建:CRUD、三态透传、measureClasses 挂载/移除、删除保护、非法 measureClassId
- [x] 5.2 `ConceptualDomainServiceTest` 增补:dimensionality 挂载/清除/非法 id/类型切换保留
- [x] 5.3 一致性校验测试增补(交集为空拒绝/非空放行/单侧缺失不校验)
- [x] 5.4 `mvn -pl metadata-registry-web -am test` 全量无回归(1184 tests 0 failures)
- [x] 5.5 前端 `npm run build` + vitest 215/215 通过
- [x] 5.6 `-Dopenapi.export=true` 再生成 `docs/technical/api/openapi.json`(含 dimensionalities)
- [x] 5.7 `detect_changes --scope all` 无 partial/truncated(风险 critical 源自工作区大量既有未提交变更,详见 CURRENT_STATE)
- [x] 5.8 冒烟 `scratch/e2e_dimensionality_api.py` 对临时实例 :8099:28 断言 PASS(CRUD/三态/搜索/CD 接线/删除保护/迁移生效);CD 更新步骤因 SQLite 64 表 join 上限(既有平台限制,无维度更新同样复现)SKIP 并走 SQL 兜底
