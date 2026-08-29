# Tasks

## 1. 后端

- [x] 1.1 `ReferenceEnumeratedConceptualDomain` 移除 `permittedValuesFieldname` 字段与 `Column` 映射，javadoc 改为引用 §7.4.2.6.4
- [x] 1.2 `ConceptualDomainRequest` / `ConceptualDomainResponse` 移除 `permittedValuesFieldname`
- [x] 1.3 `ConceptualDomainService` 移除必填校验（create/update）、`applyTypeSpecific`、`cleanupCrossTypeFields` 与 `toResponse` 中的字段处理
- [x] 1.4 `ConceptualDomainController` OpenAPI 描述去掉"permittedValuesFieldname 必填"
- [x] 1.5 `ValueDomainService.applyCommon` 删除 ReferenceEnumeratedValueDomain 特判，公共字段统一赋值

## 2. 前端

- [x] 2.1 `ConceptualDomainList.vue` 移除引用枚举型"外部源"卡片及 `onKindChange` / `onEnumKindChange` / `submitFn` 中的字段引用
- [x] 2.2 `ValueDomainList.vue` 数据类型/计量单位/最大字符数/格式移出 DESCRIBED-only 模板，对所有类型渲染
- [x] 2.3 `ValueDomainList.vue` submitFn 删除 REFERENCE 清空 maxChars/format 的特判

## 3. 测试与验证

- [x] 3.1 `ConceptualDomainServiceTest`：删除 missingFieldname 用例，改造 3 个用例（7→6 构造参数）
- [x] 3.2 `ValueDomainServiceTest`：REFERENCE 用例改为断言 maxChars/format 按请求保留
- [x] 3.3 `mvnw -pl metadata-registry-web test` 全量通过（1175 tests, 0 failures）
- [x] 3.4 前端 `npm run build` 并部署到 `metadata-registry-web/src/main/resources/static/`
