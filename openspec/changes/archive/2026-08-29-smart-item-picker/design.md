# Design: SmartItemPicker — 跨 Item 关联 picker 升级

## Context

现状（代码事实）：

- 4 个 Item 表单（ConceptualDomainList / ValueDomainList / DataElementList / DataElementConceptList）有 9+ 处 `<el-select filterable>` 关联 picker，候选项 `{ id, label, identifier, type }` 通过前端 `r.label || r.identifier || r.id` 拼 label，identifier 缺失时**直接退化为 UUID**。
- 全量选项拉取（`api.list({ size: 1000 })`）后做前端字符串包含匹配，跨实体类型、namespace 范围、精确 identifier 匹配全不支持。
- 类型 / kind 过滤是各表单 ad-hoc `<el-checkbox-group>`，4 处写法不一，体验分裂。
- 后端已有 `GET /api/v1/metadata/search` 跨实体原生 SQL 搜索（`MetadataSearchService`），按 `LOWER(value) LIKE ?` 同时匹配 `designation.value` / `definition.value` / `scoped_identifier.identifier`；返回 `AdministeredItemResponse`（含 `identifier` / `itemLabel` / `itemType` / `namespaceLabel` / `namespaceId`）。
- 既有搜索**不**支持 `namespaceId` 限定、不支持精确 identifier 匹配（只走模糊 LIKE）。

约束：

- AGENTS.md 数据库可移植性：本项目三方言（SQL Server / SQLite / 达梦），新增 SQL 必须走方言分支（沿用 `env.vendor()` 分派）。
- AGENTS.md OpenSpec 强制：跨 4 个实体的统一 UI 改动命中"新增业务功能 + 跨模块 UI 一致性"，必须 OpenSpec 先于实现。
- tech-stack.md：`<script setup lang="ts">` + Element Plus 自动导入 + i18n 双语；前端与 `useRegistryDefaultLanguage` 等 composable 同风格。
- 复用既有 `/api/v1/metadata/search` 是关键：避免新建搜索端点带来的方言分支重复维护；现状的 `metadataSearchApi`（resources.ts line 171）已存在 `search(params)` 方法。

## Goals / Non-Goals

**Goals**：

- 4 个 Item 表单的关联 picker 全部统一为 `<SmartItemPicker>`。
- 候选项默认显示 `<TYPE>.<IDENTIFIER>`（如 `MD.VD.00001`）作主、`<label>`（如 `身高`）作副、`namespace` 作最次标签；UUID 仅作为内部 id 绝不展示。
- intellisense：用户输入停止 ≥250ms 自动触发 `/metadata/search`；上一次结果展示到下次响应之间显示 loading；空结果显示"无匹配"占位。
- 类型 chip 过滤（多选）：数据元 / 数据元概念 / 概念域 / 值域 / 对象类 / 特性 / 数据类型 / 单位 等；可选空（=全部）；type 改变立即重查。
- 命名空间范围下拉：默认"全部命名空间"，可选"当前活动命名空间"（来自 `activeRegistrar.current().namespaceId`，若不存在则禁用该项）或具体某条命名空间；改值立即重查。
- Smart-query 解析（前端）：
  - 输入 `NS.ID`（如 `MD.VD.00001`、`NS.VD.00001`、`sys.VD.00001`）→ 拆 namespace shorthand prefix + identifier，**精确匹配**优先。
  - 输入纯字母数字（不含中文 / 空格 / `-` 之外的字符）→ 视为 identifier 候选，在当前 namespace 范围内精确匹配 + 前缀匹配。
  - 其它（含中文、空格、`-`、`<` 等）→ 走自由文本模糊匹配（命中 designation / definition / identifier）。
  - 解析结果以 query param 形式下发：`exactMatch=true&identifier=...&namespaceShorthand=...`，并保留 `q=` 备用（双轨；后端据 `exactMatch` 决定主索引）。
- 后端精确匹配分支：基于现有 `metadata/search` JdbcTemplate 路径扩展 `exactMatch=true` 分支，按 `(namespace.shorthand_prefix, scoped_identifier.identifier)` 联合精确查询；找不到时回退到模糊路径并标注 `matchType=PARTIAL`。
- 4 个表单的所有跨实体关联 picker 全部接入 SmartItemPicker；非跨实体（enum 字符串值）维持 el-select fallback。
- 选中项回填：picker 接受 `value: string`（itemId），通过同一查询路径预取当前项并展示在触发器位置。
- 国际化：`smartPicker.*` zh-CN + en-US。
- 编辑态：从响应行 `value*Id` 读出，picker mount 后按 id 走同一查询路径拿到详情回填。

**Non-Goals**：

- 不引入新标识符算法 / 不改 TemplateGenerationStrategy / 不改 SequenceRegistry。
- 不实现"实时输入流 intellisense"（输入到一半就触发），仅"停止输入后单次搜索"标准防抖。
- 不持久化最近使用 / 收藏 / 标签。
- 不动 OpenSpec `domain-three-tier-ui` 已落地的 kind 选择器、引用枚举外部源字段。
- 不改 Spring Security / 权限评估路径（沿用 `/metadata/search` 的既有匿名-允许查询）。
- 不实现 PK-less 的兜底创建（picker 仅选已有项）。

## Decisions

### D1：复用 `/metadata/search` 而非新建 search 端点

`MetadataSearchService.search(q, type, status, contextId, acceptability, pageable)` 已有：
- 跨实体原生 SQL JOIN
- 在 `designation` / `definition` / `scoped_identifier` 上 LOWER LIKE 模糊匹配
- `type` 过滤已生效（按 `itemType` 字符串）
- 返回 `AdministeredItemResponse`（含 identifier / itemLabel / itemType / namespaceLabel / namespaceId / registrationStatus 等所有 picker 需要的字段）

只**新增两个 query param**：`namespaceId`（限定到具体命名空间）与 `exactMatch` 标志 + `identifier` + `namespaceShorthand`（精确匹配）。不新建端点、不新增 controller、不破坏既有调用方（`DataDictionaryBrowserController` 仍可走模糊路径）。

### D2：SmartItemPicker 组件 API

```vue
<SmartItemPicker
  v-model="form.dataElementConceptId"
  :item-types="['DataElementConcept']"
  :scope="'current' | 'all' | { namespaceId: 'uuid' }"
  :placeholder="t('smartPicker.placeholder.dataElementConcept')"
  :required="true"
  @change="onChange"
/>
```

Props：
- `v-model: string | string[]`（单选 / 多选；多选用于 `conceptualDomainIds`）
- `itemTypes: string[]`（限制可选项类型；空数组 = 全部类型）
- `scope: 'current' | 'all' | { namespaceId: UUID }`（默认 `'current'`，自动读 `activeRegistrar.current().namespaceId`）
- `placeholder / required / disabled / clearable` 沿用 el-select 语义
- `allowMultiple: boolean`（DEC 多选概念域时为 true）

Behavior：
- mount 时按 `value` 调 `/metadata/search?size=1` 拿当前项回填（cache 命中即可跳过网络）
- 用户输入 → debounce 250ms → 调 `/metadata/search?q=...&type=...&namespaceId=...&size=20&exactMatch=...`
- 输入框旁加 type chip 多选 + namespace select（折叠为"高级"展开）
- 已选项永远在候选列表头部（命中优先）

### D3：智能查询解析（前端）

```
parseSmartQuery(input, currentNamespace):
  if input matches /^[A-Za-z0-9]{1,12}\.([A-Za-z0-9._-]{1,32})$/:
    [ns, identifier] = split on first '.'
    return { exactMatch: true, identifier, namespaceShorthand: ns }
  if input matches /^[A-Za-z0-9._-]{1,32}$/ (pure alphanum, no Chinese):
    return { exactMatch: true, identifier: input, namespaceShorthand: currentNamespace.shorthandPrefix }
  else:
    return { exactMatch: false, q: input }
```

后端接到 `exactMatch=true&identifier=...&namespaceShorthand=...` 时：
- 主路径：按 `scoped_identifier.identifier = ? AND ns.shorthand_prefix = ?` 精确查
- 回退：`exactMatch=false`（仍保留 q 模糊路径，UI 不感知）

### D4：候选项展示格式

```
[MD] VD.00001  ── 身高（cm）
[--] sys:mc-namespace
```

HTML（element-plus `<el-option>` 自定义模板）：
```vue
<el-option>
  <div class="smart-picker-option">
    <el-tag size="small">{{ itemType }}</el-tag>
    <span class="identifier">{{ identifier }}</span>
    <span class="separator">──</span>
    <span class="label">{{ label }}</span>
    <span class="namespace">{{ namespaceLabel }}</span>
  </div>
</el-option>
```

`identifier` 加粗等宽；`label` 折行省略；`namespace` 灰色小字。已选项 trigger 同样布局。

### D5：后端 SQL 扩展（方言中立）

新增 3 个 query param：
- `namespaceId`：UUID 可选；非空时 WHERE 加 `ai.namespace_id = ?`
- `exactMatch`：boolean；true 时切换主查询分支
- `identifier`：String；精确路径的 identifier 值
- `namespaceShorthand`：String；精确路径的命名空间简写

精确匹配 SQL 模板（沿用现有 `t("table")` / `pagingClause()` 等方言工具）：
```sql
SELECT i.id, i.item_type, ai.identifier, ...
FROM administered_item ai
JOIN item i ON i.id = ai.item_id
LEFT JOIN scoped_identifier si ON si.identified_item_id = ai.id
LEFT JOIN namespace ns ON ns.id = ai.namespace_id
WHERE ai.identifier = ?              -- 上层传来的标识符（含 shorthand_prefix）
  AND (? IS NULL OR ns.shorthand_prefix = ?)
  AND (? IS NULL OR ai.namespace_id = ?)  -- 备选：按 UUID 过滤
ORDER BY
  CASE WHEN ai.identifier = ? THEN 0
       WHEN ai.identifier LIKE ? THEN 1
       ELSE 2 END
LIMIT ? OFFSET ?
```

`scoped_identifier` 表实际存储 `identifier`（含 shorthand_prefix）；为简化后端，本期直接用 `ai.identifier` 精确匹配（`AdministeredItem.identifier` 已含 shorthand prefix）；该字段在创建时同步维护，不需要额外解析。`ns.shorthand_prefix` 仅作为后置过滤 / 模糊回退使用。

### D6：type chip 过滤 UI

- 顶部一行 chip 多选：`全部 / 数据元 / 数据元概念 / 概念域 / 值域 / 对象类 / 特性 / 数据类型 / 单位 / 命名空间`
- 缺省：未选任何项 = "全部"（与全选等价）；UI 文案"全部类型"
- chip 切换 → 立即重查
- 单值 picker：仅显示与 `itemTypes` 交集可选项；预选中交集（防呆）

### D7：命名空间范围 UI

- 默认 "当前活动命名空间"（来自 `activeRegistrar.current()` 的 `namespaceId`）
- 下拉选项："全部命名空间" / "当前命名空间：<namespace.label>" / 列出所有命名空间（按 `shorthandPrefix - label`）
- 切换 → 立即重查；range="all" 时关闭精确匹配（exactMatch 强制为 false）

### D8：缓存策略

- 组件内 `Map<queryKey, AdministeredItemResponse[]>` 缓存最近 50 次查询结果，TTL 60s
- 命中已选项 id → 跳过网络
- 已选项 id 在任一次查询结果中 → 头部优先展示

### D9：替换策略

- 旧 `<el-select filterable>` 逐步替换为 `<SmartItemPicker>`，**仅** Item-to-Item 关联；其它场景（enum 字符串、namespace 选择器等）维持原状
- 替换列表：见 proposal "涉及 4 个实体一览"
- 旧 fallback：保留 `<el-input>` UUID 输入兜底（在 picker 加载失败时使用，与 DEC 当前处理一致）

### D10：错误与空态

- 加载中：el-option 内嵌 `<el-icon class="is-loading">` spinner
- 空结果：自定义空态 "无匹配项，试试调整筛选或输入业务标识符（如 VD.00001）"
- 错误：toast + 重试按钮（最多 3 次指数退避）
- 字段禁用：disabled 透传，组件内隐藏 trigger

## Risks / Trade-offs

- [R1 输入解析歧义] → 用正则严格判定：仅 `[A-Za-z0-9]{1,12}\.[A-Za-z0-9._-]{1,32}` 才解析为 NS.ID；其它都走模糊；前端 dry-run 测试覆盖 30+ case。
- [R2 大量并发搜索请求] → debounce 250ms + AbortController 取消上一次；服务端无状态查询 O(毫秒)，无下游影响。
- [R3 模糊匹配结果集过大] → 限定 `size=20` 默认；UI 提示"还有更多结果，缩小范围"。
- [R4 多语言 identifier 解析不一致] → 限定 ASCII 字母数字 / dot / dash / underscore；中文 / 拉丁字符一律走模糊。
- [R5 命名空间下拉 1000+ 项时性能] → 复用既有 `namespaceApi.list({ size: 200 })`；命名空间数量通常 < 100，性能可接受。
- [R6 SQL 注入] → 沿用既有 JdbcTemplate `?` 占位符；新增字段全部参数化，不拼接字符串。
- [R7 /metadata/search 同时支持精确 + 模糊导致 query 复杂度上升] → 在 SQL 上以 `exactMatch` 分支选择主路径，`OR` 不混用；性能可控。
- [R8 多选 picker（DEC 概念域）的交互一致性] → 复用 el-select multiple + collapse-tags；显示逻辑与单选一致。
- [R9 后端无 namespaceId UUID 字段时 `scope=current` 解析] → fallback 到 "全部命名空间"，并 toast 提示用户。

## Migration Plan

- **部署顺序**：后端先行（不破坏既有 `/metadata/search` 调用方）→ 前端组件就绪 → 4 个表单逐步替换。
- **回退策略**：替换出错时回退到原 `<el-select>` 版本（提交前快照原文件）。
- **向后兼容**：既有 `metadataSearchApi.search(params)` 调用完全不变；新增 param 全部 optional。

## Open Questions

- type chip 默认选中的全集范围（全集 vs 按 `itemTypes` 自动交集）— 当前决策 D6：单值 picker 预选交集，多值 picker 全集（用户可手动筛）。可调整。
- 多选 picker 的"已选"项是否仍参与精确匹配排序 — 当前决策 D8：所有项已选都进头部优先区。
- SmartItemPicker 是否要支持按 `namespace` shorthand 简写直接精确（输入 `sys` 就跳到 sys 命名空间）— 当前决策 D3：仅在 NS.ID 模式下触发，不扩展到单段 shorthand。

## Resolved Questions（已在评审中确认）

- 是否复用 `/metadata/search`：是（关键复用，避免新建方言分支）
- 是否引入新依赖（lodash.debounce 等）：否（手写防抖）
- 是否抽离 `useSmartPicker` composable：是（封装 debounce + AbortController + cache）
- 后端精确匹配是否新建表 / 列 / 触发器：否（沿用 `ai.identifier` + `ns.shorthand_prefix`，零 schema 变更）
- 是否把 picker 改造纳入 OpenSpec `domain-three-tier-ui` 后续 change：否（新开 `smart-item-picker`）