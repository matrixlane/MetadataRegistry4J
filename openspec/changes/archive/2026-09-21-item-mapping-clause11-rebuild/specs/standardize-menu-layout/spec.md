# standardize-menu-layout Delta

## MODIFIED Requirements

### Requirement: 侧边栏导航分类和顺序对齐标准

系统侧边栏导航的一级和二级菜单 MUST 按照 ISO/IEC 11179-3:2023 元模型模块划分进行分组和排列。菜单项的分类和顺序 MUST 为：
1. **概览** (Dashboard)
2. **数据字典** (Data Dictionary)
3. **概念模块** (Concept)
   - 对象类 (Object Class)
   - 特性 (Property)
   - 数据元概念 (Data Element Concept)
   - 概念域 (Conceptual Domain)
   - 值含义 (Value Meaning)
   - 维度 (Dimensionality) （状态为规划中，指向 PlaceholderPage 占位组件）
   - 计量类 (Measure Class)
   - 计量单位 (Unit of Measure)
4. **表示模块** (Representation)
   - 数据元 (Data Element)
   - 值域 (Value Domain)
   - 数据类型方案 (Data Type Scheme)
   - 数据类型 (Data Type)
5. **基础与核心模块** (Basic & Core)
   - 个人 (Individual)
   - 组织 (Organization)
   - 角色 (Role) （状态为规划中，指向 PlaceholderPage 占位组件）
   - 联系人 (Contact)
   - 文档类型 (Document Type)
   - 语言 (Language)
   - 参考文档 (Reference Document)
   - 上下文 (Context)
6. **标识与命名模块** (Identification & Naming)
   - 命名空间 (Namespace)
   - 命名规范 (Naming Convention)
7. **注册模块** (Registration)
   - 注册机构 (Registration Authority)
   - 注册规范 (Registry Specification)
   - 注册人员 (Registrar)
   - 注册流程 (Registration Process) （指向注册流程展示页 `/registration-process`）
8. **分类模块** (Classification)
   - 分类方案 (Classification Scheme)
   - 分类项 (Classification Item) （状态为规划中，指向 PlaceholderPage 占位组件）
9. **项目映射模块** (Item Mapping)
   - 项目映射 (Item Mapping) （指向项目映射管理页 `/item-mappings`）
10. **系统** (System) （仅管理员可见）
    - 用户管理 (User Management)
    - 系统设置 (System Settings)
11. **我的** (My) （固定于侧边栏底部，不在 el-menu 内）

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
