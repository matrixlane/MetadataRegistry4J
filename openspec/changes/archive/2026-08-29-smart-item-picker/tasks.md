# Tasks

## 1. 后端：/metadata/search 增强

- [ ] 1.1 `MetadataSearchController.search` 新增可选 `@RequestParam UUID namespaceId`
- [ ] 1.2 `MetadataSearchController.search` 新增可选 `@RequestParam boolean exactMatch`、`String identifier`、`String namespaceShorthand`
- [ ] 1.3 `MetadataSearchService.search` 接受上述参数：当 `exactMatch=true` 时切换主 SQL 分支为精确 `WHERE ai.identifier = ? AND ns.shorthand_prefix = ?` 查询；未命中回退到现有模糊路径，附带 `matchType=EXACT` / `matchType=PARTIAL` 标识
- [ ] 1.4 精确匹配 SQL 按匹配度排序（精确 = 0；前缀 LIKE = 1；模糊 = 2）
- [ ] 1.5 SQL 全部走方言中立工具 `env.qualify(...)` / `pagingClause()` / `firstOne(...)`，不引入方言专有 SQL
- [ ] 1.6 沿用 `t("table")` 限定表名以满足 db-portability（schema 限定）
- [ ] 1.7 `AdministeredItemResponse` 增 `matchType` 可选字段（EXACT / PARTIAL / null），由 service 填充

## 2. 后端：单测

- [ ] 2.1 `MetadataSearchServiceTest` 新增：`exactMatch=true` 命中 / 未命中回退 / 带 shorthand filter / 不带 shorthand filter / 排序优先级 / type 过滤 / namespaceId 过滤 / 中文 q 模糊匹配
- [ ] 2.2 现有测试不受影响（保持向后兼容）

## 3. 前端：API 封装

- [ ] 3.1 `metadata-registry-ui/src/api/resources.ts` 的 `metadataSearchApi.search` 增加命名空间限定与精确匹配参数
- [ ] 3.2 新增类型 `AdministeredItemSearchHit`（id / identifier / label / itemType / namespaceId / namespaceLabel / matchType）
- [ ] 3.3 不破坏既有 `metadataSearchApi.search(params)` 调用签名（仅追加 optional 参数）

## 4. 前端：useSmartPicker composable

- [ ] 4.1 `metadata-registry-ui/src/composables/useSmartPicker.ts`：封装 debounce（250ms）、AbortController 取消、`Map<key, hits[]>` 缓存（TTL 60s）、smart-query 解析（D3）
- [ ] 4.2 暴露 `hits: Ref<AdministeredItemSearchHit[]>`, `loading: Ref<boolean>`, `error: Ref<string | null>`, `search(input)`, `fetchById(id)`, `parse(input, currentNs)`
- [ ] 4.3 单测 `useSmartPicker.spec.ts`：防抖 / abort / 缓存命中 / NS.ID 解析 / 中文回退 / 当前 namespace identifier 解析

## 5. 前端：SmartItemPicker 组件

- [ ] 5.1 `metadata-registry-ui/src/components/SmartItemPicker.vue`：基于 el-select + remote + filterable；自定义 el-option 模板展示 `type / identifier / label / namespace`
- [ ] 5.2 头部高级筛选条（折叠）：type chip 多选（与 `itemTypes` prop 交集）+ namespace scope 下拉
- [ ] 5.3 trigger 位置渲染同 layout（type tag + identifier + label + namespace）
- [ ] 5.4 已选项永远在结果集头部；空态 / loading / 错误 三个独立 UI 状态
- [ ] 5.5 单选 / 多选 两种模式（多选用 el-select multiple + collapse-tags）
- [ ] 5.6 键盘可达性：↑↓ / Enter / Esc / Tab
- [ ] 5.7 ARIA 属性：role="combobox" / aria-activedescendant
- [ ] 5.8 失败重试 ≤3 次指数退避（1s / 2s / 4s）
- [ ] 5.9 单测 `SmartItemPicker.spec.ts`：mount / 输入触发搜索 / type 切换重查 / 选中回填 / 多选 / 错误态 / 加载态

## 6. 前端：i18n

- [ ] 6.1 `metadata-registry-ui/src/i18n/zh-CN.json` 新增 `smartPicker.*` namespace：
  - `placeholder.all`, `placeholder.dataElement`, `placeholder.dataElementConcept`, `placeholder.conceptualDomain`, `placeholder.valueDomain`, `placeholder.objectClass`, `placeholder.property`, `placeholder.dataType`, `placeholder.unitOfMeasure`
  - `empty`, `error`, `loading`
  - `typeFilter.label`, `scope.label`, `scope.all`, `scope.current`
- [ ] 6.2 `metadata-registry-ui/src/i18n/en-US.json` 同 key 英文版
- [ ] 6.3 type chip 中文映射：数据元 / 数据元概念 / 概念域 / 值域 / 对象类 / 特性 / 数据类型 / 单位

## 7. 前端：4 个 Item 表单接入

- [ ] 7.1 `ConceptualDomainList.vue`：描述型 `describedConceptualDomainId` 替换为 SmartItemPicker（itemTypes=[ConceptualDomain]）
- [ ] 7.2 `ValueDomainList.vue`：描述型 `describedConceptualDomainId` 替换为 SmartItemPicker（itemTypes=[ConceptualDomain]）
- [ ] 7.3 `ValueDomainList.vue`：`dataTypeId` 替换为 SmartItemPicker（itemTypes=[DataType]）
- [ ] 7.4 `ValueDomainList.vue`：`unitOfMeasureId` 替换为 SmartItemPicker（itemTypes=[UnitOfMeasure]）
- [ ] 7.5 `ValueDomainList.vue`：引用枚举 `referencedConceptualDomainId` 替换为 SmartItemPicker（itemTypes=[ConceptualDomain]）
- [ ] 7.6 `DataElementConceptList.vue`：`objectClassId` 替换为 SmartItemPicker（itemTypes=[ObjectClass]）
- [ ] 7.7 `DataElementConceptList.vue`：`propertyId` 替换为 SmartItemPicker（itemTypes=[Property]）
- [ ] 7.8 `DataElementConceptList.vue`：`conceptualDomainIds`（多选）替换为 SmartItemPicker 多选模式
- [ ] 7.9 `DataElementList.vue`：`dataElementConceptId` 替换为 SmartItemPicker
- [ ] 7.10 `DataElementList.vue`：`valueDomainId` 替换为 SmartItemPicker（含 kind tag 仍保留，kind 多选过滤改由 SmartItemPicker 统一处理）
- [ ] 7.11 移除 4 个表单中旧的 kind 多选过滤 UI（D3 / ValueDomainList 内的 kind chip 块），由 SmartItemPicker 的 type chip 接管
- [ ] 7.12 保留 `<el-input>` UUID fallback（DEC picker 加载失败时显示）

## 8. 前端：types / 通用类型

- [ ] 8.1 `metadata-registry-ui/src/types/common.ts` 增 `AdministeredItemSearchHit` 类型
- [ ] 8.2 `SmartItemPicker` props TypeScript 接口：`v-model`、`itemTypes`、`scope`、`allowMultiple`、`placeholder`、`required`、`disabled`、`clearable`

## 9. 前端：e2e

- [ ] 9.1 Playwright `metadata-registry-ui/tests/e2e/smart-item-picker.spec.ts`：
  - 打开 DataElement 新建 → SmartItemPicker 输入 "VD.00001" → 验证候选列表显示
  - 输入 "身高" → 验证模糊匹配
  - 切换 type chip → 验证结果集收缩
  - 切换 scope → 验证 namespace 限定
  - 输入 "MD.VD.00001" → 验证精确匹配
  - 编辑既有 DE → 验证 picker trigger 回填业务标识符（不是 UUID）

## 10. 验证

- [ ] 10.1 后端 `mvn -pl metadata-registry-web -am test -Dtest='MetadataSearchServiceTest'` 全绿
- [ ] 10.2 后端全量 `mvn -pl metadata-registry-web test` 全绿（确认未破坏 1168 个旧测）
- [ ] 10.3 前端 `npx vue-tsc --noEmit` 零错
- [ ] 10.4 前端 `npm run build` 通过
- [ ] 10.5 前端 `npm run test` 全绿（含新加的 useSmartPicker / SmartItemPicker / e2e 测试）
- [ ] 10.6 `npx openspec validate smart-item-picker` 通过
- [ ] 10.7 `npx openspec archive smart-item-picker --yes` 归档

## 11. 文档

- [ ] 11.1 `ARCHITECTURE.md` 加 "SmartItemPicker 跨实体统一搜索" 一段
- [ ] 11.2 `SmartItemPicker.vue` 头部注释链接本 change 的 proposal.md
- [ ] 11.3 在 `metadataSearchApi.search` 上方注释"扩展字段"

## 12. 已确认的设计决议（来自评审）

- 复用 `/metadata/search` 而非新建端点
- 智能解析在前端完成（仅下发 `exactMatch` / `identifier` / `namespaceShorthand` 三个 query param）
- 不引入新依赖；debounce 手写
- 后端精确匹配基于 `ai.identifier` + `ns.shorthand_prefix`，零 schema 变更
- 多选 picker 用 el-select multiple + collapse-tags，不另立组件
- 4 个 Item 表单 9+ 处 picker 全部接入 SmartItemPicker；旧 `<el-input>` UUID fallback 保留
- i18n 双语；OpenSpec 全部按 spec-driven schema
- 不动 `domain-three-tier-ui` 已落地的能力（kind 选择器 / 引用枚举外部源字段）