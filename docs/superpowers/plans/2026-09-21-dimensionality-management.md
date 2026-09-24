# 实现计划:Dimensionality(维度)管理功能

依据:`docs/superpowers/specs/2026-09-21-dimensionality-management-design.md` + OpenSpec `add-dimensionality-management/tasks.md`。TDD:测试先行(服务/校验器单测),再落实现,最后全量回归 + 前端构建 + OpenAPI 再生成。

## 后端

1. `DimensionalityRepository`(新)→ `DimensionalityServiceTest` 红 → `DimensionalityService` + `DimensionalityRequest/Response` + `MeasureClassRef` + `DimensionalityController` 绿。
2. `ConceptualDomainRepository.countByDimensionality_Id` → `ConceptualDomainServiceTest` 增补红 → `ConceptualDomainRequest/Response` + `ConceptualDomainService.applyDimensionality` 绿。
3. `ValueDomainAssociationValidatorTest` 增补红 → 一致性校验实现绿。

## 前端

4. `dimensionalityApi` + `DimensionalityList.vue` + router/i18n。
5. `ConceptualDomainList.vue` 维度单选。

## 验证

6. `mvn -pl metadata-registry-web -am test`、`npm run build`、vitest、`-Dopenapi.export=true`、`detect_changes --scope all`。
