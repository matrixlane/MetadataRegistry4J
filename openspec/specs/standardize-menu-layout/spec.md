# standardize-menu-layout Specification

## Purpose
定义系统侧边栏导航的分类、分级、项目与排列次序，使其与 ISO/IEC 11179-3:2023 元模型模块划分（概念 / 表示 / 基础与核心 / 标识与命名 / 注册 / 分类 / 项目映射）保持一致，并约束菜单项的中英文术语与角色可见性。

## Requirements

### Requirement: 侧边栏导航分类和顺序对齐标准

系统侧边栏导航的一级和二级菜单 MUST 按照 ISO/IEC 11179-3:2023 元模型模块划分进行分组和排列。菜单项的分类和顺序 MUST 为：
1. **概览** (Dashboard)
2. **数据字典** (Data Dictionary)
4. **概念模块** (Concept)
   - 对象类 (Object Class)
   - 特性 (Property)
   - 数据元概念 (Data Element Concept)
   - 概念域 (Conceptual Domain)
   - 值含义 (Value Meaning)
   - 维度 (Dimensionality) （状态为规划中，指向 PlaceholderPage 占位组件）
   - 计量类 (Measure Class)
   - 计量单位 (Unit of Measure)
5. **表示模块** (Representation)
   - 数据元 (Data Element)
   - 值域 (Value Domain)
   - 数据类型方案 (Data Type Scheme)
   - 数据类型 (Data Type)
6. **基础与核心模块** (Basic & Core)
   - 个人 (Individual)
   - 组织 (Organization)
   - 角色 (Role) （状态为规划中，指向 PlaceholderPage 占位组件）
   - 联系人 (Contact)
   - 文档类型 (Document Type)
   - 语言 (Language)
   - 参考文档 (Reference Document)
   - 上下文 (Context)
7. **标识与命名模块** (Identification & Naming)
   - 命名空间 (Namespace)
   - 命名规范 (Naming Convention)
8. **注册模块** (Registration)
   - 注册机构 (Registration Authority)
   - 注册规范 (Registry Specification)
   - 注册人员 (Registrar)
   - 注册流程 (Registration Process) （指向注册流程展示页 `/registration-process`）
9. **分类模块** (Classification)
   - 分类方案 (Classification Scheme)
   - 分类项 (Classification Item) （状态为规划中，指向 PlaceholderPage 占位组件）
10. **项目映射模块** (Item Mapping)
    - 项目映射 (Item Mapping) （指向项目映射管理页 `/item-mappings`）
11. **我的** (My) （固定于侧边栏底部，不在 el-menu 内）

侧边栏之外：
- 顶栏右上角（`header-right`）自左向右提供：**工作空间**入口图标（`/workspace`，带未提交数量徽标，见 workspace 能力"未提交数量徽标"需求）与**应用设置**入口图标（`/app-settings`，沿用 AI 辅助的 MagicStick 图标，任何登录用户可见，承载原系统设置中的 AI 辅助偏好）；
- 侧边栏底栏（`sidebar-footer`，"我的"项之下）仅对管理员提供一个合并的**系统**入口（`/system`，默认进入通用）；系统页在主内容区内展示**一级平面菜单**：通用（`/system/general`）、用户管理（`/system/users`）、数据源（`/system/datasource`）、危险区域（`/system/danger`）。仅用户管理列表宽度自适应撑满主内容区，其余设置页保持原内容宽度。旧路径 `/app-users`、`/system-settings*` 重定向至新路径。

#### Scenario: 菜单分组与排列显示

- **WHEN** 用户登录系统并成功加载主页面侧边栏
- **THEN** 系统侧边栏按照上述分类和顺序渲染一级及二级导航菜单项，包括规划中的“维度”、“角色”、“分类项”项

#### Scenario: 访问规划中菜单展示占位页

- **WHEN** 用户在侧边栏中点击“维度”、“角色”或“分类项”导航菜单项，或者在地址栏直接访问 `/dimensionality`、`/roles`、`/classification-items`
- **THEN** 系统展示 `PlaceholderPage.vue` 占位页面，标题分别为“维度”、“角色”、“分类项”，内容提示“功能开发中，敬请期待”

#### Scenario: 注册流程菜单导航

- **WHEN** 用户在侧边栏注册模块中点击“注册流程”
- **THEN** 系统导航到 `/registration-process` 注册流程展示页

#### Scenario: 项目映射菜单导航

- **WHEN** 用户在侧边栏项目映射模块中点击“项目映射”
- **THEN** 系统导航到 `/item-mappings` 项目映射管理页，而非已移除的映射方案工作区

#### Scenario: 工作空间入口位于顶栏右上角

- **WHEN** 登录用户查看顶栏右上角（`header-right`）
- **THEN** 可见工作空间入口图标（含徽标容器 `workspace-entry-badge`）；侧边栏导航中不存在工作空间菜单项；其余菜单项分组与顺序不变

#### Scenario: 应用设置入口位于顶栏右上角且所有用户可见

- **WHEN** 任意登录用户查看顶栏右上角
- **THEN** 可见应用设置入口（AI 辅助图标，`/app-settings`）；AI 辅助不再出现在系统设置页中

#### Scenario: 系统入口合并且一级菜单在主内容区展示

- **WHEN** 管理员点击底栏"系统"入口
- **THEN** 系统导航至 `/system`，主内容区展示一级平面菜单（用户管理、通用、数据源、危险区域）及对应内容，不再出现嵌套的二级菜单；底栏中不存在独立的系统设置/用户管理入口
- **WHEN** 非管理员登录
- **THEN** 底栏无"系统"入口，且无法通过直接输入路径访问系统路由（路由守卫拦截）

#### Scenario: 工作空间入口导航

- **WHEN** 用户点击顶栏右上角的工作空间入口图标
- **THEN** 系统导航到 `/workspace` 并展示工作空间页

### Requirement: 国际化语言包翻译规范
所有新增和修改的一级及二级导航菜单标题 MUST 支持中英文国际化，并使用 ISO/IEC 11179 规范的中文和英文术语。
新增或修改的翻译键包括：
- `nav.conceptModule`: "概念模块" / "Concept"
- `nav.representationModule`: "表示模块" / "Representation"
- `nav.basicAndCoreModule`: "基础与核心模块" / "Basic & Core"
- `nav.identificationAndNaming`: "标识与命名模块" / "Identification & Naming"
- `nav.registrationModule`: "注册模块" / "Registration"
- `nav.classificationModule`: "分类模块" / "Classification"
- `nav.itemMappingModule`: "项目映射模块" / "Item Mapping"
- `nav.role`: "角色" / "Role"
- `nav.documentType`: "文档类型" / "Document Type"
- `nav.registrationProcess`: "注册流程" / "Registration Process"
- `nav.classificationScheme`: "分类方案" / "Classification Scheme"
- `nav.classificationItem`: "分类项" / "Classification Item"
- `nav.itemMapping`: "项目映射" / "Item Mapping"
- `nav.referenceDocument`: "参考文档" / "Reference Document"

#### Scenario: 语言切换显示
- **WHEN** 切换界面语言为中文（简体）
- **THEN** 侧边栏和系统标题中显示标准的中文名，例如“概念模块”、“表示模块”、“基础与核心模块”、“标识与命名模块”、“注册模块”、“分类模块”、“项目映射模块”
- **WHEN** 切换界面语言为英文
- **THEN** 侧边栏和系统标题中显示对应的英文名，例如 “Concept”, “Representation”, “Basic & Core”, “Identification & Naming”, “Registration”, “Classification”, “Item Mapping”

### Requirement: 路由权限与菜单过滤
侧边栏的渲染和路由安全 MUST 与用户角色的权限绑定。系统管理菜单及其子路由只能对超级管理员角色（ADMIN）显示，普通用户登录后应被过滤隐藏且不能通过手动输入路径访问。

#### Scenario: 普通用户隐藏系统管理
- **WHEN** 拥有普通用户角色的账号登录并查看侧边栏
- **THEN** 系统管理（System）菜单大类及属下的“用户管理”、“系统设置”在侧边栏被完全过滤隐藏，且手动在浏览器地址栏输入路由时会拦截并重定向
