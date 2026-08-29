## ADDED Requirements

### Requirement: 关联 picker 默认显示业务标识符而非 UUID

任何跨 Item 关联的 picker（数据元 / 数据元概念 / 概念域 / 值域 4 个表单中所有 Item-to-Item 关联字段） MUST NOT 在候选项 trigger 位置展示 UUID。候选项 MUST 优先展示业务标识符（即 `scoped_identifier.identifier` 或同义字段，形如 `VD.00001`、`CD.00012`、`MD.DE.00001`、`NS.VD.00001`），主标识符缺失时退化为 item 的 preferredDesignation，UUID 仅作为内部 value 字段存在。

#### Scenario: 候选项包含完整业务标识符

- **WHEN** `/metadata/search` 返回一条 `identifier="VD.00001"`、`itemLabel="身高"` 的 ValueDomain
- **THEN** picker 下拉项 MUST 渲染为：`<TYPE>=VD>  VD.00001  ──  身高（cm）` 三段式布局
- **AND** picker trigger 位置（已选项） MUST 展示完全相同的布局

#### Scenario: 标识符缺失时降级到名称

- **WHEN** 后端返回一条无 `identifier` 但有 `itemLabel="Unnamed"` 的行
- **THEN** picker trigger MUST 展示 `Unnamed`（label），MUST NOT 展示 UUID

#### Scenario: 标识符与名称都缺失

- **WHEN** 后端返回一条 `identifier=null` 且 `itemLabel=null` 的行
- **THEN** picker MUST 显示一个本地化 "（无标题）"占位，MUST NOT 展示 UUID

### Requirement: intellisense 输入联想

关联 picker MUST 在用户停止输入后 ≥250ms 自动触发远程搜索；上一次未完成的请求 MUST 被取消；空字符串 MUST 显示空态占位。debounce MUST 在本地完成（不依赖后端节流）。

#### Scenario: 用户输入触发搜索

- **WHEN** picker 获得焦点且用户键入字符 `身高`
- **THEN** 在 250ms 内不再输入后，picker MUST 触发一次 `/metadata/search?q=%E8%BA%AB%E9%AB%98&...` 调用
- **AND** 在响应到达前 MUST 显示 loading 状态

#### Scenario: 连续输入防抖

- **WHEN** 用户在 250ms 内连续输入 `身`、`身高`、`身高c`
- **THEN** picker MUST 仅在最后一次输入后 250ms 触发一次搜索（而非三次）
- **AND** 期间前两次的 AbortController MUST 被取消

#### Scenario: 空字符串空态

- **WHEN** 用户清空输入框
- **THEN** picker MUST 立即清空候选列表并显示"无匹配项"占位，MUST NOT 触发远程搜索

### Requirement: 类型 chip 多选过滤

关联 picker MUST 提供一组类型 chip（多选），缺省未选时等价于"全部类型"；chip 切换 MUST 立即重查；与 `itemTypes` prop 交集外的类型 MUST NOT 出现在候选 chip 上。

#### Scenario: chip 切换重查

- **WHEN** 候选类型 chip 当前为 ["全部"]，用户点击 chip "值域"
- **THEN** picker MUST 立即触发 `type=ValueDomain` 的远程搜索
- **AND** 结果 MUST 仅包含 ValueDomain 类型

#### Scenario: 多 chip 多选

- **WHEN** 用户同时选中 chip "值域" 与 "概念域"
- **THEN** picker MUST 触发 `type=ValueDomain,ConceptualDomain` 搜索（后端接受逗号分隔 type 列表）

#### Scenario: itemTypes prop 限制候选 chip

- **WHEN** picker prop `itemTypes=["ValueDomain"]`
- **THEN** chip 面板 MUST NOT 显示 "数据元" / "数据元概念" / "概念域" 等 chip；仅显示 ValueDomain（与"全部"互斥）

### Requirement: 命名空间范围过滤

关联 picker MUST 提供命名空间范围下拉（默认"当前活动命名空间"，fallback "全部命名空间"）；切换 MUST 立即重查；选择"全部命名空间"时 MUST 关闭精确匹配分支（D3 / D7 规则）。

#### Scenario: 默认 scope=current

- **WHEN** picker mount 且当前活动 Registrar 存在 namespaceId
- **THEN** picker MUST 默认按该 namespaceId 过滤

#### Scenario: 当前 namespace 不可用时降级

- **WHEN** picker mount 时活动 Registrar 无 namespaceId
- **THEN** picker MUST 自动降级到 scope="all" 并显示一次性 toast "无活动命名空间，已切换到全部命名空间"

#### Scenario: 切换到全部命名空间

- **WHEN** 用户在 namespace 下拉选择 "全部命名空间"
- **THEN** picker MUST 立即触发搜索（无 namespaceId 参数）
- **AND** 即便用户输入 `MD.VD.00001`，exactMatch 强制为 false（避免歧义 namespace 解析）

### Requirement: 智能查询解析 — NS.ID 精确匹配

当用户输入匹配 `[A-Za-z0-9]{1,12}\.[A-Za-z0-9._-]{1,32}` 模式（典型如 `MD.VD.00001`、`sys.VD.00001`）时，picker MUST 把输入拆为 `namespaceShorthand + identifier`，并以精确匹配方式触发搜索。后端 MUST 在 `exactMatch=true&identifier=...&namespaceShorthand=...` 时走精确匹配分支（按 `ai.identifier = ? AND ns.shorthand_prefix = ?` 等值查询），无结果时回退到模糊路径并按匹配度排序。

#### Scenario: NS.ID 精确匹配命中

- **WHEN** 用户输入 `MD.VD.00001` 且系统存在 shorthand_prefix=MD 的命名空间下 identifier=VD.00001 的 ValueDomain
- **THEN** picker MUST 把请求参数设为 `exactMatch=true&identifier=MD.VD.00001&namespaceShorthand=MD`
- **AND** 后端响应 MUST 仅返回该精确命中的行（至少 1 条），排在首位

#### Scenario: NS.ID 无精确命中回退

- **WHEN** 用户输入 `MD.VD.99999` 不存在精确匹配
- **THEN** 后端 MUST 回退到 LOWER LIKE 模糊路径，返回标识符或命名空间相关的最近似行
- **AND** 响应 payload 携带 `matchType=PARTIAL` 字段（可选，前端可高亮"无精确匹配"）

#### Scenario: NS.ID 解析歧义保护

- **WHEN** 用户输入 `身高`（含中文）
- **THEN** picker MUST NOT 触发 NS.ID 解析，必须走 q=身高 模糊匹配

### Requirement: 当前命名空间 identifier 前缀匹配

当用户输入纯字母数字 / dot / dash / underscore（无中文、空格、`<` 等），picker MUST 在当前活动命名空间范围内做 identifier 精确 + 前缀匹配；若输入含 `精确匹配部分`+`%`，后端 SHOULD 同时返回前缀匹配。

#### Scenario: 当前 namespace 范围内 identifier

- **WHEN** scope=current 且用户输入 `VD.00001`
- **THEN** picker MUST 触发 `exactMatch=true&identifier=VD.00001&namespaceShorthand=<currentNs.shorthandPrefix>`
- **AND** 后端仅在当前 namespace 内匹配

#### Scenario: 当前 namespace 无匹配时降级

- **WHEN** scope=current 且用户输入 `VD.99999` 在当前 namespace 无匹配
- **THEN** picker SHOULD 提示 "当前命名空间无匹配，是否切换到全部命名空间？"（提供一键切换按钮）

### Requirement: 智能查询解析 — 自由文本模糊匹配

当用户输入含中文、空格或不在精确匹配正则中的字符时，picker MUST 走 `q=` 模糊匹配路径（命中 designation / definition / identifier 三者任一）。

#### Scenario: 中文名称搜索

- **WHEN** 用户输入 `身高`
- **THEN** picker MUST 触发 `q=身高` 模糊匹配
- **AND** 返回含 designation 中带 "身高" 的所有 Item

#### Scenario: 定义文本搜索

- **WHEN** 用户输入 `数值范围`
- **THEN** picker MUST 触发 `q=数值范围` 模糊匹配
- **AND** 返回 definition 中带 "数值范围" 的所有 Item

### Requirement: 已选项回填与头部优先

picker mount 时 MUST 通过同一搜索路径按 value (UUID) 拿当前项展示在 trigger 位置；任一次搜索结果中，已选项 MUST 出现在候选列表头部（即使不匹配当前 keyword）。

#### Scenario: 编辑态回填

- **WHEN** picker mount 且 `value="abc-123"`（已存在的 ValueDomain id）
- **THEN** picker MUST 触发一次 `id=abc-123` 的精确查询并把结果作为 trigger 显示
- **AND** picker 打开下拉时，已选项 MUST 出现在候选列表头部并视觉标记

#### Scenario: 已选项不在当前搜索结果

- **WHEN** 当前 keyword 搜索结果集不含已选项
- **THEN** picker MUST 自动插入已选项到结果集头部（独立于 keyword 匹配度）

### Requirement: 错误与空态

加载中 MUST 显示 loading；空结果 MUST 显示本地化 "无匹配项" 占位 + 建议（"试试调整筛选或输入业务标识符如 VD.00001"）；错误 MUST toast + 最多 3 次指数退避重试；3 次失败后 MUST 显示错误占位 "搜索失败，请稍后再试"。

#### Scenario: 空结果占位

- **WHEN** 远程搜索返回 0 条
- **THEN** picker MUST 显示 "无匹配项 — 试试输入业务标识符如 VD.00001"

#### Scenario: 错误重试 3 次后失败

- **WHEN** 远程搜索连续 3 次失败
- **THEN** picker MUST 显示错误占位 + 一个"重试"按钮
- **AND** MUST NOT 永久循环

### Requirement: 4 个 Item 表单全部接入 SmartItemPicker

`metadata-registry-ui/src/views/{value-domain,conceptual-domain,data-element,data-element-concept}` 4 个 list 视图中的所有跨 Item 关联 picker（共 9+ 处） MUST 替换为 `<SmartItemPicker>`。

#### Scenario: 数据元表单 picker 替换

- **WHEN** 打开 DataElementList 的新建/编辑对话框
- **THEN** "数据元概念" 与 "值域" 两个 picker MUST 是 SmartItemPicker
- **AND** 不再是裸 `<el-select>` + `r.label || r.identifier || r.id` 拼 label

#### Scenario: 概念域表单 picker 替换

- **WHEN** 打开 ConceptualDomainList 的新建/编辑对话框
- **THEN** 描述型分支的"概念域"picker（`describedConceptualDomainId`） MUST 是 SmartItemPicker

#### Scenario: 值域表单 picker 替换

- **WHEN** 打开 ValueDomainList 的新建/编辑对话框
- **THEN** 描述型分支的"概念域" / "数据类型" / "计量单位" picker，以及引用枚举分支的"引用概念域" picker MUST 全部是 SmartItemPicker

#### Scenario: 数据元概念表单 picker 替换

- **WHEN** 打开 DataElementConceptList 的新建/编辑对话框
- **THEN** "对象类" / "特性" picker MUST 是 SmartItemPicker；"概念域"多选 MUST 是 SmartItemPicker（多选模式）

### Requirement: i18n 与可访问性

`smartPicker.*` 文案 MUST 双语（zh-CN + en-US）；trigger 与下拉项 MUST 可通过键盘导航（↑↓ / Enter / Esc / Tab）；ARIA `role="combobox"` + `aria-activedescendant` MUST 正确。

#### Scenario: 双语文案

- **WHEN** UI 切到英文 locale
- **THEN** picker placeholder / empty / loading 文案 MUST 为英文
- **WHEN** UI 切到中文 locale
- **THEN** MUST 为中文

#### Scenario: 键盘导航

- **WHEN** picker 获得焦点，用户按 ↓ 键
- **THEN** 选中项 MUST 下移一项并视觉高亮
- **WHEN** 用户按 Enter
- **THEN** 高亮项 MUST 被选中并填入 trigger