## Context

目前系统的侧边栏导航菜单由 `metadata-registry-ui/src/layouts/AppLayout.vue` 直接硬编码渲染，使用的是 Element Plus 的 `<el-menu>`。
路由注册和路由过滤则依赖于 `metadata-registry-ui/src/router/index.ts` 中声明的路由元信息（`meta.routeKey`, `meta.minRole` 等）以及 `NavigationRouteTable` 的静态注册项。
由于一二级菜单结构和命名不够标准，不符合 ISO/IEC 11179 的最新划分，导致导航栏可读性较差，熟悉标准的用户难以在操作时建立标准与菜单项的直接映射。需要对导航结构重新设计和调整。

## Goals / Non-Goals

**Goals:**
- 将侧边栏菜单分组和排列，重构为以下一级大类和二级子项（完全对齐 ISO/IEC 11179-31:2023 Annex A Consolidated class hierarchy 定义的继承关系）：
  - **工作台** (Dashboard)
  - **数据字典** (Data Dictionary)
  - **注册与公共设施** (Common Facilities) -> 上下文、注册机构、注册人员、命名空间（公共/注册设施类，继承自 `Item` 或 `IdentifiedItem`）
  - **相关方与文献** (Parties & Documents) -> 组织、个人、联系人、参考文献（基本类，对应 Clause 8）
  - **概念域** (Concept Region) -> 对象类、特性、数据元概念、概念域、计量单位、计量类、维度（全部继承自 `Concept` 基类）
  - **表示域** (Representation Region) -> 数据元、值域、数据类型、数据类型方案（直接继承自 `Item`，作为具体表示设施）
  - **分类与映射** (Classification & Mapping) -> 分类体系、映射方案、映射网络、匹配结果管理（ Clause 10 / 11 对应区域）
  - **系统管理** (System) -> 用户管理、系统设置
- 重构 `AppLayout.vue` 侧边栏的 Element Plus 模板，按照新的分组逻辑进行多级菜单布局渲染。
- 修改 `zh-CN.json` 和 `index.ts` (路由表及 NavigationRouteTable) 中的中英文翻译与注册路径，使翻译词条、路由元信息及导航显示一致。

**Non-Goals:**
- 不涉及后台管理系统（如 `/app-users` 视图逻辑或 API 的修改），仅限于前端路由表声明和 UI 侧边栏的重新布局排布。
- 不会增加任何新的业务页面，也不会变更已有页面的内部业务逻辑。

## Decisions

### Decision 1: 重构 Element Plus 侧边栏模板结构
我们决定在 `AppLayout.vue` 中对菜单结构进行静态分组排布：
- **方案 A**: 使用动态配置数组循环渲染。
- **方案 B**: 保持 Element Plus 模板 of 静态声明，但重构折叠式子菜单 `<el-sub-menu>` 的嵌套结构以实现对齐标准的五大模块。
- **选择**: **方案 B**。静态声明对于该系统而言更加直观、开发周期短，且能直接应用 Element Plus 的图标和权限过滤逻辑。

### Decision 2: 国际化键名整合
新增以下国际化键名到 `i18n/zh-CN.json` 及相应英文语言包（如果存在）：
- `nav.commonFacilities`: "注册与公共设施"
- `nav.partyAndDocument`: "相关方与文献"
- `nav.conceptRegion`: "概念域"
- `nav.representationRegion`: "表示域"
- `nav.classificationAndMapping`: "分类与映射"
- `nav.dimensionality`: "维度"

### Decision 3: 划分概念类与表示设施并处理“维度”占位页
依据 ISO/IEC 11179-31:2023 Annex A，我们将菜单项划分为两大标准分类：
- **概念域 (Concept Region)**：包含所有继承自 `Concept` 的大类。
  - 整合对象类 (`/object-classes`)、特性 (`/properties`)、数据元概念 (`/data-element-concepts`)、概念域 (`/conceptual-domains`)、计量单位 (`/units-of-measure`) 和计量类 (`/measure-classes`)。
  - 特别地，为了支持标准的完整性，在概念域中增加 **维度 (Dimensionality)**。由于目前前端和后端缺乏对其具体页面与 API 的实现，我们决定在 `router/index.ts` 路由列表中将 `/dimensionality` 的 `component` 声明指向占位组件 `PlaceholderPage.vue`，并在 `routeTable` 注册为 `status: 'planned'`，使其在侧边栏渲染为规划中的可用占位。
- **表示域 (Representation Region)**：包含所有直接继承自 `Item` 且不属于 `Concept` 的表示层设施。
  - 整合数据元 (`/data-elements`)、值域 (`/value-domains`)、数据类型 (`/data-types`) 和数据类型方案 (`/data-type-schemes`)。

## Risks / Trade-offs

- **[Risk]** 菜单项层级加深导致页面跳转较原先需要多一次点击（如寻找数据元现在需要展开“表示域”二级菜单）。
  - **[Mitigation]** 侧边栏支持折叠/展开；并且“数据字典”和“数据元”为最高频使用的入口，将“数据字典”作为独立的一级入口保留；对于“数据元”，在“表示域”展开后非常直观，也支持通过页面搜索快速定位。
- **[Risk]** E2E 测试因为菜单路径及层级改变而失败。
  - **[Mitigation]** 检查 Playwright 测试中的元素定位方式。如果使用了类似 `.el-menu-item:has-text("数据元")` 或 `page.click('text=数据元')`，则由于文本未发生根本变化，且 Element Plus 会自动展开被点击项 of 子菜单，测试仍会通过。如有使用特定层级路径的选择器则针对性修正。
