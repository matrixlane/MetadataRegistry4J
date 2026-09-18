# Individual 标识符补全 — 设计文档

- 日期：2026-09-18
- 状态：已实施（补录需求工件；实现已在工作区完成并通过测试）
- 涉及模块：`metadata-registry-common-facilities`（实体）、`metadata-registry-web`（DTO/服务/迁移）、`metadata-registry-ui`（IndividualList、RegistrarList）
- 标准依据：`identifier` 为**应用扩展属性**——ISO/IEC 11179-3:2023 中 Individual（6.3.2）无 identifier 属性，不添加 `@StandardReference` 标注

## 背景与问题

1. `IndividualList.vue` 自 WI-004 起就有"标识符"列与必填表单项，但后端 `Individual` 实体与 `IndividualResponse` 从未提供该字段——列表列恒为空、必填校验形同虚设。
2. `RegistrarList.vue` 名称列只显示 `identifier`，而 `createRegistrarForUser` 创建的 registrar 其 individual 为空、identifier 亦无展示价值，导致注册人员列表名称空白。

## 方案

**D1：`Individual` 新增可空 `identifier` 列（varchar），应用层"留空自动生成"。**
理由：不破坏既有数据（全部存量行为空 → 惰性回填）；列表搜索/排序只需简单字符串列。三方言迁移 `V20260917001`（SQLite/SQL Server `varchar(255)`、达梦 `varchar(255)`；Hibernate ddl-auto 在 SQLite 下亦会补建）。

**D2：生成规则统一在应用层。**
- 管理端 `IndividualService` 创建/更新：请求带非空 identifier 用之，否则生成 `individual-{8位随机hex}`（`generateIdentifier`）。
- 资料页 `AuthService.updateMyIndividual`：用 `{username}-individual`，便于追溯来源；保存时若已有个体但 identifier 为空也补写。
- 不建唯一索引：避免与既有数据冲突；唯一性不在本期保证。

**D3：Registrar 回填。**
保存个人资料（updateMyIndividual）后，将账号绑定的、individual 为空的 Registrar 回填到该 Individual，使注册人员列表能显示姓名。仅首次/空时回填，不覆盖已有引用。

**D4：UI 配套。**
IndividualList 标识符改为选填（placeholder"留空自动生成"）、名称渲染由 `row.name?.sign` 修正为 `row.name`（后端返回扁平字符串）；RegistrarList 名称列改为 `individualName || organizationName || identifier` 的兜底链。

## 测试

- `AuthServiceTest`：首次保存回填账号绑定的 Registrar；Registrar 已关联其他 Individual 时不覆盖。
- `IndividualServiceIntegrationTest`：identifier 随请求/响应透传。
- 既有 IndividualService 列表/CRUD 测试无回归。

## 不做的事

- 不加唯一约束、不做 identifier 编辑审计、不做 scoped identifier 式的全注册项统一标识（属于既有 administered item 体系）。
- 不迁移/清洗存量数据（惰性回填即可）。
