# Tasks

## 1. 数据模型补齐

- [ ] 1.1 在 `ReferenceEnumeratedValueDomain` 实体新增 `String permittedValuesFieldname` 字段，配 `@Column(name = "permitted_values_fieldname", nullable = false, length = 255)`
- [ ] 1.2 在 `ReferenceEnumeratedValueDomain` 实体新增 `ReferenceEnumeratedConceptualDomain referencedConceptualDomain` 字段（ManyToOne, 可空）
- [ ] 1.3 在 `ReferenceEnumeratedConceptualDomain` 实体新增 `String permittedValuesFieldname` 字段，配 `@Column(name = "permitted_values_fieldname", nullable = false, length = 255)`
- [ ] 1.4 不写 Flyway 迁移；由 Hibernate `ddl-auto:update` 在两张表自动新增列；启动日志验证 `alter table ... add column` 输出

## 2. 后端枚举与 DTO 扩展（值域）

- [ ] 2.1 在 `ValueDomainType` 枚举新增 `REFERENCE_ENUMERATED`
- [ ] 2.2 在 `ValueDomainRequest` record 末尾新增 `String permittedValuesFieldname` 与 `UUID referencedConceptualDomainId`
- [ ] 2.3 在 `ValueDomainResponse` record 末尾新增 `permittedValuesFieldname` / `referencedConceptualDomainId` / `referencedConceptualDomainLabel`
- [ ] 2.4 `ValueDomainController` Swagger `@Tag` 描述追加 Reference 引用枚举子类型

## 3. ValueDomainService 分支扩展

- [ ] 3.1 `create()` switch 增 `case REFERENCE_ENUMERATED -> new ReferenceEnumeratedValueDomain()`
- [ ] 3.2 `update()` 引入类型变更分支：跨类型切换时清理旧类型专属字段（沿用 v1 思路并扩展到 `applyTypeSpecific`）
- [ ] 3.3 `applyTypeSpecific()` 末尾增加 `else if (entity instanceof ReferenceEnumeratedValueDomain revd)` 分支
- [ ] 3.4 `create()` 入口前置校验：`type == REFERENCE_ENUMERATED` 且 `permittedValuesFieldname` 为空 → `BusinessException(400)`
- [ ] 3.5 `toResponse()` 增 `instanceof ReferenceEnumeratedValueDomain revd` 分支：映射字段
- [ ] 3.6 `delete()` 沿用现有清理分支；引用枚举本期无专属清理逻辑

## 4. 后端枚举与 DTO 扩展（概念域）

- [ ] 4.1 在 `ConceptualDomainType` 枚举新增 `REFERENCE_ENUMERATED`
- [ ] 4.2 在 `ConceptualDomainRequest` record 末尾新增 `String permittedValuesFieldname`（`@Size(max=255)`）
- [ ] 4.3 在 `ConceptualDomainResponse` record 末尾新增 `permittedValuesFieldname`
- [ ] 4.4 `ConceptualDomainController` Swagger `@Tag` 描述追加 Reference 引用枚举子类型

## 5. ConceptualDomainService 分支扩展

- [ ] 5.1 `create()` switch 增 `case REFERENCE_ENUMERATED -> new ReferenceEnumeratedConceptualDomain()`
- [ ] 5.2 `applyTypeSpecific()` 增加引用枚举 CD 分支（写入 `permittedValuesFieldname`）
- [ ] 5.3 `create()` 入口前置校验：`type == REFERENCE_ENUMERATED` 且 `permittedValuesFieldname` 为空 → `BusinessException(400)`
- [ ] 5.4 `update()` 引入类型变更分支（CD 类型切换清理 VM 列表 / `sourceUri` / 外部源字段）
- [ ] 5.5 `toResponse()` 增 `instanceof ReferenceEnumeratedConceptualDomain recd` 分支：映射字段
- [ ] 5.6 `delete()` 引用枚举 CD 无额外清理（与描述型 CD 一致）

## 6. list 接口 `type` 过滤参数

- [ ] 6.1 `ConceptualDomainController.list` 增加可选 `@RequestParam String type`
- [ ] 6.2 `ValueDomainController.list` 沿用 v1 的 `type` 参数；本期补齐后端过滤实现
- [ ] 6.3 在 `ConceptualDomainService` 与 `ValueDomainService` 各新增 JPA Specification 工厂方法 `byType(String)`（基于 `getClass().getSimpleName()` 谓词，方言中立）
- [ ] 6.4 service 层 `list(q, pageable, type)` 把 `type` 与既有 `q` 谓词 AND 组合

## 7. DataElementConcept 多对多概念域支持

- [ ] 7.1 `DataElementConceptRequest` record 新增 `List<UUID> conceptualDomainIds` 字段
- [ ] 7.2 `DataElementConceptResponse` record 新增 `List<UUID> conceptualDomainIds` 与 `List<ConceptualDomainKindLabel> conceptualDomainLabels`（含 kind）
- [ ] 7.3 `DataElementConceptService.create/update` 增加 `applyConceptualDomains(dec, conceptualDomainIds)`：删除既有 `dataElementConcepts` 关联，按新 IDs 重建
- [ ] 7.4 `toResponse()` 增 `conceptualDomainIds` 与 label 列表映射
- [ ] 7.5 编辑态从 response 回填 `conceptualDomainIds`（在 ItemFormDialog 或 DataElementConceptList 内处理）

## 8. 前端 API 扩展

- [ ] 8.1 `metadata-registry-ui/src/api/resources.ts` 的 `conceptualDomainApi.list` 与 `valueDomainApi.list` 透传 `type` query
- [ ] 8.2 新增 `objectClassApi` / `propertyApi`（若尚不存在）：复用既有 crud 模式
- [ ] 8.3 在 `metadata-registry-ui/src/types/common.ts` 或对应 `.d.ts` 内扩展类型：
  - `ValueDomainKind = 'DESCRIBED' | 'ENUMERATED'`
  - `ValueDomainEnumerationKind = 'LOCAL' | 'REFERENCE'`
  - `DomainType = 'DESCRIBED' | 'LOCAL_ENUMERATED' | 'REFERENCE_ENUMERATED'`

## 9. 前端 ConceptualDomainList UI 重构（镜像 ValueDomainList）

- [ ] 9.1 替换 type 选项为两级选择器（kind + enumKind）
- [ ] 9.2 `kindFromType` / `enumKindFromType` 反查函数
- [ ] 9.3 `handleEdit` 在 `open(row)` 前注入 `kind` / `enumKind` 初值
- [ ] 9.4 `watch([form.kind, form.enumKind], ...)` 联动 `form.type` 与字段清理
- [ ] 9.5 三分支渲染：描述型（来源 URI + 概念域定义）、本地枚举型（值含义编辑卡片）、引用枚举型（外部源卡片含 `permittedValuesFieldname`）
- [ ] 9.6 类型列 `el-tag` 增"引用枚举型"分支
- [ ] 9.7 `submitFn` 包装层在提交前清空不适用的字段
- [ ] 9.8 `loadFormOptions()` 增加 `referenceEnumeratedConceptualDomain` 选项拉取（已有，过滤参数传递）

## 10. 前端 ValueDomainList UI 重构（沿用 v1，本期不重写）

- [ ] 10.1 沿用 v1 的两级选择器与三分支；仅做对齐 CD 的微调（kind / enumKind 字段命名）
- [ ] 10.2 VD picker kind 标签已通过 CD 改动同步（无需新增 VD 内部改动）

## 11. 前端 DataElementList UI 升级

- [ ] 11.1 `vdOptions` 元素从 `{ id, label }` 扩展为 `{ id, label, type }`
- [ ] 11.2 `<el-option>` 模板渲染 `${label} [kind tag]`
- [ ] 11.3 picker 顶部增 `kindFilter: string[]` 多选过滤 UI（多选 `el-checkbox-group`）
- [ ] 11.4 过滤逻辑在 picker 内 `:filter-method` 中按 kind 过滤

## 12. 前端 DataElementConceptList UI 升级

- [ ] 12.1 "对象类" `<el-input>` 升级为 `<el-select>`（picker 加载完才替换；picker 失败保留输入框 fallback）
- [ ] 12.2 "特性" 同上
- [ ] 12.3 新增"概念域"多选 `<el-select multiple>`，每项 label 旁显示 kind `el-tag`
- [ ] 12.4 picker 头部增 kind 多选过滤
- [ ] 12.5 提交时把 `objectClassId` / `propertyId` / `conceptualDomainIds` 提交到后端
- [ ] 12.6 编辑态从 `row.conceptualDomainIds` 回填

## 13. 共享 composable 抽取（可选项）

- [ ] 13.1 把 `kind` / `enumKind` / `watch` 联动逻辑抽取到 `metadata-registry-ui/src/composables/useDomainKindForm.ts`（本期可选项；如不抽离，CD / VD 各自保留重复代码，后续重构时再抽）

## 14. 测试

- [ ] 14.1 后端：`ValueDomainServiceTest`（v1 已建）—— 沿用
- [ ] 14.2 后端：`ValueDomainControllerTest` —— `type` 过滤参数（4 个枚举值）
- [ ] 14.3 后端：`ConceptualDomainServiceTest`（新建）—— 引用枚举 CD 的创建/更新/校验拒绝
- [ ] 14.4 后端：`ConceptualDomainControllerTest` —— `type` 过滤参数
- [ ] 14.5 后端：`DataElementConceptServiceTest` —— `conceptualDomainIds` 多对多重建（空数组 / 单值 / 多值 / 编辑态从原关联替换）
- [ ] 14.6 前端 Playwright：`tests/e2e/conceptual-domain.spec.ts` —— CD 两级选择器、引用枚举分支、编辑反查
- [ ] 14.7 前端 Playwright：`tests/e2e/value-domain.spec.ts` —— 沿用 v1 覆盖
- [ ] 14.8 前端 Playwright：`tests/e2e/data-element.spec.ts` —— VD picker kind 标签与过滤
- [ ] 14.9 前端 Playwright：`tests/e2e/data-element-concept.spec.ts` —— DEC picker 升级、CD 多选 + kind 过滤、编辑回填

## 15. 验证与归档

- [ ] 15.1 `mvn -pl metadata-registry-web -am test -Dtest=ValueDomainServiceTest,ValueDomainControllerTest,ConceptualDomainServiceTest,ConceptualDomainControllerTest,DataElementConceptServiceTest` 全绿
- [ ] 15.2 `mvn -pl metadata-registry-web compile` 无新增警告
- [ ] 15.3 启动 web 模块（`./mvnw spring-boot:run -pl metadata-registry-web -Dspring-boot.run.profiles=local`），手测 4 个 list 页面：
  - CD 两级选择器与三分支；引用枚举 CD 的 `permittedValuesFieldname` 必填
  - VD 沿用 v1
  - DEC 概念域 picker 多选与 kind 过滤
  - DE VD picker kind 标签与过滤
- [ ] 15.4 Hibernate SQL 日志确认两张表（`reference_enumerated_value_domain` / `reference_enumerated_conceptual_domain`）自动加列
- [ ] 15.5 启动 ui 模块（`pnpm dev` 或等价 npm 脚本），手测 4 个 list 页面
- [ ] 15.6 `npx openspec validate value-domain-kind-and-enum-kind` 通过
- [ ] 15.7 `npx openspec archive value-domain-kind-and-enum-kind --yes` 归档该 change（仅 15.1-15.6 全绿后）

## 16. 文档与回退

- [ ] 16.1 在 `ARCHITECTURE.md` 数据规范章节追加"概念域 / 值域三层 UI 与 ISO/IEC 11179-31 §7.4.2 对齐；数据元 / 数据元概念 picker kind 标签化与过滤"的说明
- [ ] 16.2 在 `openspec/changes/value-domain-kind-and-enum-kind/proposal.md` 顶部引用一行注释，标注"对应 ISO/IEC 11179-31:2023 §7.4.2 与 §7.6；覆盖 4 个实体"
- [ ] 16.3 在 `ConceptualDomainList.vue` / `ValueDomainList.vue` / `DataElementList.vue` / `DataElementConceptList.vue` 关键位置（`watch` / picker kind 渲染）上方加一行注释，链接本 change 的 proposal.md，便于后人回溯设计决策

## 17. 已确认的设计决议（来自评审）

- 4 个实体一并处理：CD / VD 完整 3 层；DE / DEC picker kind 标签化 + 过滤；DE / DEC 不引入自身 kind（标准里是 flat item）。
- `referencedConceptualDomainId`（VD 的引用枚举指向 CD 字段）可空（标准未强制）。
- 不引入 i18n key；UI 文案沿用中文直字符串。
- 列表列文案：`描述型` / `本地枚举型` / `引用枚举型`。
- 一级与二级使用两个虚拟字段 `kind` / `enumKind`；提交 `form.type` 保持与后端枚举一对一映射。
- DEC 的"对象类"与"特性"改用 picker；保留 UUID 输入框 fallback；新增"概念域"多选 picker。
- 不引入运行时配置开关；本期不写 Flyway 迁移。
- 共享 composable 抽取为可选项，不强制本期完成。
- picker kind 标签三色：`DESCRIBED` → info；`LOCAL_ENUMERATED` → success；`REFERENCE_ENUMERATED` → warning。
- list 接口 `type` 过滤参数在 CD / VD 两个 service 都补齐。