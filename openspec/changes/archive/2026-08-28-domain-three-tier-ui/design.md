# Design: 概念域 / 值域三层 UI + 数据元 / 数据元概念 kind 选择器（ISO/IEC 11179-31:2023 §7.4.2 对齐）

## Context

现状（代码事实）：

- 数据模型层（`metadata-registry-data-specification`）已经按 ISO/IEC 11179-31:2023 §7.4.2 / §7.6 / §7.3.2 建好 4 个相关实体类：

| 实体类 | 三层结构 | 现有子类 |
|------|---------|---------|
| `ValueDomain` | §7.4.2.8 → §7.4.2.9 ∥ §7.4.2.10 → §7.4.2.11 ∥ §7.4.2.13 | `DescribedValueDomain` / `LocalEnumeratedValueDomain` / `ReferenceEnumeratedValueDomain` |
| `ConceptualDomain` | §7.4.2.1 → §7.4.2.2 ∥ §7.4.2.3 → §7.4.2.4 ∥ §7.4.2.6 | `DescribedConceptualDomain` / `LocalEnumeratedConceptualDomain` / `ReferenceEnumeratedConceptualDomain` |
| `DataElement` (§7.6.2.1) | flat item | — |
| `DataElementConcept` (§7.3.2.3) | flat item | — |

`ReferenceEnumeratedValueDomain` 与 `ReferenceEnumeratedConceptualDomain` 当前均为空类，未承接标准强制属性 `permitted_values_fieldname`（§7.4.2.13 / §7.4.2.6）。

- 后端 DTO 层（`metadata-registry-web/dto`）：

| DTO | 枚举值缺 | DTO 缺字段 |
|------|---------|-----------|
| `ValueDomainType` | 缺 `REFERENCE_ENUMERATED` | `permittedValuesFieldname` / `referencedConceptualDomainId` |
| `ConceptualDomainType` | 缺 `REFERENCE_ENUMERATED` | `permittedValuesFieldname` |

`ValueDomainService.create/update` / `ConceptualDomainService.create/update` 的 switch 均只覆盖 `DESCRIBED` / `LOCAL_ENUMERATED`，未覆盖 `REFERENCE_ENUMERATED`。

- 前端（`metadata-registry-ui/src/views`）：

| 页面 | 现有缺口 |
|------|---------|
| `value-domain/ValueDomainList.vue` | 仅 2 选项（描述型 / 局部枚举）；引用枚举分支缺失 |
| `conceptual-domain/ConceptualDomainList.vue` | 同上 |
| `data-element/DataElementList.vue` | "值域"picker 无 kind 标签与过滤 |
| `data-element-concept/DataElementConceptList.vue` | "概念域"未提供选择器（目前只是 objectClassId / propertyId 两个 UUID 输入框，概念域绑定缺失）；picker 需重建 |

约束：

- `AGENTS.md` 数据库可移植性：`@Table(schema = ...)` 不得硬编码；Flyway 仅承载 Hibernate 无法表达的内容；新增列由 `ddl-auto` 表达即可。
- AGENTS.md 强制 OpenSpec：本期命中 4 个实体变更，必须先 OpenSpec 再实现。
- `tech-stack.md` 编码规范：实体 `@Getter @Setter @NoArgsConstructor`；DTO record；前端 `<script setup lang="ts">` + Element Plus 自动导入；测试沿用 `@ExtendWith(MockitoExtension.class)`。

## Goals / Non-Goals

**Goals：**

- 4 个前端 list 页面对齐 ISO/IEC 11179-31:2023 §7.4.2 与 §7.6：
  - `ConceptualDomainList.vue` / `ValueDomainList.vue`：两级选择器 `kind` + `enumKind`，三互斥分支（描述型 / 本地枚举型 / 引用枚举型），每个分支有不同表单 UI。
  - `DataElementList.vue` / `DataElementConceptList.vue`：关联选择器（值域 / 概念域）下拉项必须显示 kind 标签；picker 头部加 kind 多选过滤；DEC 必须补"概念域"选择器（取代现状 UUID 输入框）。
- 后端打通 `REFERENCE_ENUMERATED`：
  - `ConceptualDomainType` 与 `ValueDomainType` 各加 `REFERENCE_ENUMERATED`。
  - 对应 request / response record 增 `permittedValuesFieldname` 字段。
  - `ConceptualDomainService` / `ValueDomainService` create / update / applyTypeSpecific / toResponse 增加 `REFERENCE_ENUMERATED` 分支。
  - 校验 `permittedValuesFieldname` 在引用枚举下必填；VD 还校验 `referencedConceptualDomainId` 指向合法的 `ReferenceEnumeratedConceptualDomain`。
- 数据模型补齐：`ReferenceEnumeratedConceptualDomain` 加 `permittedValues_fieldname`；`ReferenceEnumeratedValueDomain` 加 `permitted_values_fieldname` 与 `referenced_conceptual_domain_id`（v1 已设计）。
- list 接口增 `type` 过滤：`GET /api/v1/conceptual-domains` 与 `GET /api/v1/value-domains` 都接受 `type` query param，按枚举过滤。
- 编辑态双向反查：CD / VD 打开编辑对话框时从 `type` 反推 `kind` / `enumKind` 初值；切换两级选择器时实时联动 `form.type` 与分支展示。
- DE / DEC 编辑态联动：若 VD / CD 的 type 切换（只读 label），picker 中相应项的 kind 标签必须同步更新（label 在选项内渲染，复用现有 `loadFormOptions`）。
- 既有 `DESCRIBED` / `LOCAL_ENUMERATED` 行为不破坏：`api`、路由、字段保持兼容；老数据 `type` 字段值不需要迁移。

**Non-Goals：**

- 不引入 DE / DEC 子类型（标准中 DE / DEC 是 flat item；其 kind 由关联 VD / CD 决定）。
- 不引入 `permittedValuesFieldname` 之外的外部源 URI 字段（标准仅要求字段名）。
- 不实现"运行时从外部源拉取并缓存许可值 / 值含义"机制；本期仅承载外部源字段元数据。
- 不调整命名空间前置门槛、不引入新配置开关、不改既有权限评估。
- 不做完整 i18n key 抽离（沿用中文直字符串）。

## Decisions

### D1：CD 与 VD 共享同构 UI 模式

`ConceptualDomainList.vue` 与 `ValueDomainList.vue` 用完全同构的两级选择器 + 三互斥分支模式：

| 一级 `kind` | 二级 `enumKind` | 提交 `form.type` | 表单分支 |
|------------|------------------|-----------------|----------|
| `DESCRIBED` | _（无关）_       | `DESCRIBED`         | 概念域选择器（VD）/ 来源 URI（CD） |
| `ENUMERATED` | `LOCAL`         | `LOCAL_ENUMERATED`  | 许可值编辑卡片（VD）/ 值含义编辑卡片（CD） |
| `ENUMERATED` | `REFERENCE`     | `REFERENCE_ENUMERATED` | 外部源卡片（VD 含 `permittedValuesFieldname` + `referencedConceptualDomainId`；CD 含 `permittedValuesFieldname`） |

理由：CD / VD 在 ISO 11179-31 §7.4.2 中是同构模型；UI 模式完全相同，仅各分支的字段不同（PV vs VM、外部源引用）。两份 list 代码可拆出共享 composable `useDomainKindForm`（本期先不强制抽离，但代码风格保持同构以便后人抽取；v2 tasks 中加可选项）。

### D2：DEC 选择器从 UUID 输入框升级为下拉选择器

`DataElementConceptList.vue` 当前是 `<el-input v-model="form.objectClassId">` 与 `<el-input v-model="form.propertyId">` 两行 UUID 输入框；但 DEC 在标准里有多对多 `conceptualDomains` 关联，前端必须能"选"概念域，否则关系无法落地。本期调整为：
- "对象类" `<el-select>` 拉 `objectClassApi.list({ size: 1000 })` 选项，按 label 搜索。
- "特性" `<el-select>` 拉 `propertyApi.list({ size: 1000 })` 选项。
- 新增"概念域" `<el-select multiple>` 拉 `conceptualDomainApi.list({ size: 1000 })`，每项标签旁显示 kind `el-tag`，picker 头部加 kind 多选过滤。
- 提交时把 `objectClassId` / `propertyId` / `conceptualDomainIds`（多值）传给后端。后端 `DataElementConceptRequest` 增 `conceptualDomainIds` 字段；service 层 `applyConcept` 多对多关联重建。
- 既有 UUID 输入框作为 `objectClassId` / `propertyId` 临时入口保留 fallback（如果 `objectClassApi` / `propertyApi` 拉取失败，不阻塞；本期在 picker 加载完前仍显示输入框，picker 就绪后切换为下拉）。

### D3：DE 选择器加 kind 标签 + 过滤

`DataElementList.vue` 已有 `valueDomainId` 下拉；本期：
- 把 `vdOptions` 元素从 `{ id, label }` 扩展为 `{ id, label, type }`（type 是后端响应里的 `DESCRIBED`/`LOCAL_ENUMERATED`/`REFERENCE_ENUMERATED`）。
- `<el-option>` 的 label 渲染为 `${label} [kind]`（kind 用 `el-tag size="small"` 渲染"描述型/本地枚举型/引用枚举型"）。
- 在 picker 顶部加 `kindFilter: string[]` 多选；过滤逻辑在 `el-select` 的 `:filter-method` 中按 kind 过滤。
- 编辑态：保持现有 `valueDomainId` 字段语义不变。

### D4：picker kind 标签样式

- `el-tag` 三种 kind 的 `type` 颜色：
  - `DESCRIBED` → `info`（蓝灰）
  - `LOCAL_ENUMERATED` → `success`（绿）
  - `REFERENCE_ENUMERATED` → `warning`（橙）
- 文本：沿用 v1 三向文案"描述型"/"本地枚举型"/"引用枚举型"。
- 字号 `size="small"`，避免与主 label 抢视觉焦点。

### D5：list 接口 `type` 过滤参数

- `GET /api/v1/value-domains?type=...` 与 `GET /api/v1/conceptual-domains?type=...` 同时支持：
  - `type` 取值：`DESCRIBED` / `LOCAL_ENUMERATED` / `REFERENCE_ENUMERATED` / `ENUMERATED`（后两个语义在 CD / VD 一致：`ENUMERATED` 等价于"任何枚举子类型"）。
  - 不传 `type`：维持既有行为（不过滤）。
- 后端 JPA Specification 工厂方法 `byType(String)`：
  - 对 VD：`byType` 用 `cd.getClass().getSimpleName()` 模式判断；与 v1 一致。
  - 对 CD：同样基于类名匹配（`DescribedConceptualDomain` / `LocalEnumeratedConceptualDomain` / `ReferenceEnumeratedConceptualDomain` / 任何 `EnumeratedConceptualDomain` 子类）。
- 前端 `valueDomainApi.list` / `conceptualDomainApi.list` 透传 `type` query param（与 v1 一致）。

### D6：CD 的 `REFERENCE_ENUMERATED` 字段语义

CD 的引用枚举对应 ISO §7.4.2.6 `Reference_Enumerated_Conceptual_Domain`：
- 必填属性 `permitted_values_fieldname`（按 §7.4.2.6：`fieldname_for_value_meanings`）→ 后端 DTO `permittedValuesFieldname`，实体字段 `permittedValuesFieldname`。
- 引用枚举 CD 在前端 UI 中只展示"外部源"卡片（含 `permittedValuesFieldname` 输入框）；不要求选"外部源 VD"（CD 与 VD 的交叉引用本期不在范围内，留作后续扩展）。
- 删除分支清理：CD 删除时若属于引用枚举，无额外需清理（无 VM、PV）；`ConceptualDomainService.delete()` 维持当前 `LocalEnumeratedConceptualDomain` 清理分支即可。

### D7：VD 的 `REFERENCE_ENUMERATED` 沿用 v1

VD 的引用枚举字段语义与 v1 完全一致：
- 必填 `permittedValuesFieldname`。
- 可空 `referencedConceptualDomainId`，校验指向 `ReferenceEnumeratedConceptualDomain`。
- 本期 UI 在 VD picker 中按 kind 标签化（同 D3 / D4）。

### D8：测试覆盖范围

后端：
1. `ConceptualDomainServiceTest`（新建）：创建/更新引用枚举 CD 的成功与校验拒绝。
2. `ConceptualDomainControllerTest`（新建或扩）：`type` 过滤参数。
3. `ValueDomainServiceTest`（v1 已建）：沿用。
4. `ValueDomainControllerTest`（v1 已建）：沿用；本期加 `type` 过滤参数测试。

前端：
1. Playwright `tests/e2e/conceptual-domain.spec.ts`（新建）：CD 两级选择器与三种分支；引用枚举分支；编辑反查。
2. Playwright `tests/e2e/value-domain.spec.ts`（沿用 v1）。
3. Playwright `tests/e2e/data-element-concept.spec.ts`（新建）：DEC 选择器升级；CD picker kind 标签与过滤。
4. Playwright `tests/e2e/data-element.spec.ts`（新建）：DE VD picker kind 标签与过滤。

### D9：既有 test 不动

`DataElementServiceTest`、`ItemAdministrationServiceServiceTest` 等不依赖 VD/CD kind 的现有 test 不修改；本期仅在它们之外新增 case。

### D10：DEC 选择器重建的兼容性

- 既有 DEC 行的 `conceptualDomains` 关联若存在，前端编辑态必须回显：本期在 `open(row)` 之后从 `row.conceptualDomainIds`（后端 response 增字段）或从 `conceptualDomains`（既有字段）回填。
- `DataElementConceptResponse` 新增 `conceptualDomainIds: List<UUID>` 与 `conceptualDomainLabels: List<String>`（含 kind）。
- DEC `create/update` 在后端 service 中重建 `dataElementConcepts` 多对多关联（参考 `ItemAdministrationService.applyDesignations` 风格）。

### D11：ValueDomainService 的"类型变更"分支

VD 编辑态若 `type` 从 `LOCAL_ENUMERATED` 切换到 `REFERENCE_ENUMERATED`（或反向），后端 MUST 清理旧类型专属字段：
- `LOCAL → REFERENCE`：调用现有 `cleanupPermissibleValues(levd)` 清空 PV。
- `REFERENCE → LOCAL`：清空 `permittedValuesFieldname` 与 `referencedConceptualDomain`（如非 null）。
- `* → DESCRIBED`：清空 PV、外部源字段；保留 designation / definition。
- `DESCRIBED → *`：清空 `describedConceptualDomain` 引用。

CD 同理（v1 共享的 `applyTypeSpecific` 清理逻辑）。本期在 v1 设计中已铺垫"类型变更分支"；本期扩展到 CD。

## Risks / Trade-offs

- [R1 DEC 多对多概念域接口新增，与既有 `objectClassId` / `propertyId` 输入框并行] → 后端 response 增 `conceptualDomainIds`；编辑态从 response 回填；既有 UUID 输入框保留作 fallback。
- [R2 VD / CD picker 加 kind 多选过滤，picker 加载延迟增加] → 既有 `loadFormOptions()` 已并行拉取；加 type 过滤是 query param，不增加并发；前端过滤在前端完成（不发起新请求）。
- [R3 引用枚举 CD 不带"外部源 VD"关联，可能导致 VD 找不到匹配 CD] → 本期显式 Non-Goal；CD 引用枚举字段与 VD 引用枚举字段独立；跨层引用留待后续。
- [R4 老 DEC 行 `conceptualDomains` 关联为多对多，前端需正确渲染] → 后端 response 增 `conceptualDomainIds`；前端在编辑态用其回填。
- [R5 DEC 的 `objectClass` / `property` 改用 picker 后，picker 加载失败时不能阻塞创建] → 保留 `<el-input>` fallback 入口，picker 加载完才切换。
- [R6 picker 选项数量随 kind 过滤可能为空] → UI 显示"无匹配选项"提示，与既有 el-select 空态一致。
- [R7 列表 type 过滤参数实现需新加 JPA Specification，且类型间 OR 关系复杂] → 用 `getClass().getSimpleName()` 谓词；新增 `byType()` 工厂方法；本期为 2 个 service 各加 1 个方法。
- [R8 VD / CD 共享 composable 抽取（如 `useDomainKindForm`）本期不抽离] → 显式 Non-Goal；后续若再扩可抽。
- [R9 DEC 选择器升级会改变既有 DEC 创建/编辑路径] → 后端 service 既有 `applyConcept` 不含概念域关联；本期在 service 内增 `applyConceptualDomains()`；既有单测不动，新测覆盖。

## Migration Plan

- **部署顺序**：
  1. 部署后端：`ReferenceEnumeratedConceptualDomain` 字段加列；`ConceptualDomainType` 扩展；CD / VD service 分支与 list 接口 `type` 过滤参数；DEC request / response 增 `conceptualDomainIds` 字段；DEC service 增多对多重建逻辑。
  2. 部署前端：CD / VD 两级选择器；DEC / DE picker kind 标签与过滤。
  3. 不需要数据迁移；既有 type 字段值不变。
- **回退策略**：
  - 后端 DEC 的 `conceptualDomainIds` 字段处理失败时，回退到不处理多对多（保留现状无关联）。本期保留向后兼容路径。
  - 前端 picker kind 标签渲染失败时，回退到无标签渲染（仅 label）。
- **向后兼容**：
  - API 增枚举值（`REFERENCE_ENUMERATED`）不破坏既有调用方。
  - record 字段在末尾新增；老 payload 反序列化兼容。

## Open Questions

- DEC `conceptualDomains` 多对多在标准里是 0..*；本期是否限制最多 1 个？**当前决定**：本期不限制（多值），与标准一致。
- 引用枚举 CD 是否需要"指向外部源 URI"字段？标准未要求，本期不引入。
- DEC picker 升级后是否保留 `<el-input>` fallback？**当前决定**：保留（picker 加载完才替换）。

## Resolved Questions（已在 proposal 评审中确认）

- 4 个实体一并处理：CD / VD 完整 3 层；DE / DEC picker kind 标签化 + 过滤；DE / DEC 不引入自身 kind（标准里是 flat item）。
- `referencedConceptualDomainId` 可空（标准未强制）；本期 VD 沿用 v1 决定。
- 不引入 i18n key；UI 文案沿用中文直字符串。
- 列表列文案：`描述型` / `本地枚举型` / `引用枚举型`。
- 一级与二级使用两个虚拟字段 `kind` / `enumKind`；提交 `form.type` 保持与后端枚举一对一映射。
- DEC `objectClass` / `property` 改用 picker；保留 UUID 输入框 fallback。
- 不引入运行时配置开关；本期不写 Flyway 迁移。