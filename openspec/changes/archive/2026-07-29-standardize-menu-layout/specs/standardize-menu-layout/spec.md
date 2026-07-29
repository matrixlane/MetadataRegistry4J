## ADDED Requirements

### Requirement: 侧边栏导航分类和顺序对齐标准
系统侧边栏导航的一级和二级菜单 MUST 按照 ISO/IEC 11179-3:2023 和 ISO/IEC 11179-31:2023 Annex A Consolidated class hierarchy 定义的继承层次进行划分和排列。菜单项的分类和顺序 MUST 为：
1. **工作台** (Dashboard)
2. **数据字典** (Data Dictionary)
3. **注册与公共设施** (Registration & Common Facilities)
   - 上下文 (Context)
   - 注册机构 (Registration Authority)
   - 注册人员 (Registrar)
   - 命名空间 (Namespace)
4. **相关方与文献** (Parties & Documents)
   - 组织 (Organization)
   - 个人 (Individual)
   - 联系人 (Contact)
   - 参考文献 (Reference Document)
5. **概念域** (Concept Region) （整合继承自 `Concept` 的所有概念类大类）
   - 对象类 (Object Class)
   - 特性 (Property)
   - 数据元概念 (Data Element Concept)
   - 概念域 (Conceptual Domain)
   - 计量单位 (Unit of Measure)
   - 计量类 (Measure Class)
   - 维度 (Dimensionality) （状态为规划中，指向 PlaceholderPage 占位组件）
6. **表示域** (Representation Region) （整合直接继承自 `Item` 且代表表示层的数据大类）
   - 数据元 (Data Element)
   - 值域 (Value Domain)
   - 数据类型 (Data Type)
   - 数据类型方案 (DataType Scheme)
7. **分类与映射** (Classification & Mapping)
   - 分类体系 (Classification)
   - 映射方案 (Mapping Schemes)
   - 映射网络 (Mapping Network)
   - 匹配结果管理 (Interop Mappings)
8. **系统管理** (System)
   - 用户管理 (User Management)
   - System Settings (系统设置)

#### Scenario: 菜单分组与排列显示
- **WHEN** 用户登录系统并成功加载主页面侧边栏
- **THEN** 系统侧边栏按照上述分类和顺序渲染一级及二级导航菜单项，包括规划中的“维度”项

#### Scenario: 访问规划中菜单展示占位页
- **WHEN** 用户在侧边栏中点击“维度”导航菜单项，或者在地址栏直接访问 `/dimensionality`
- **THEN** 系统展示 `PlaceholderPage.vue` 占位页面，标题为“维度”，内容提示“功能开发中，敬请期待”

### Requirement: 国际化语言包翻译规范
所有新增和修改的一级及二级导航菜单标题 MUST 支持中英文国际化，并使用 ISO/IEC 11179 规范的中文和英文术语。
新增或修改的翻译键包括：
- `nav.commonFacilities`: "注册与公共设施" / "Common Facilities"
- `nav.partyAndDocument`: "相关方与文献" / "Parties & Documents"
- `nav.conceptRegion`: "概念域" / "Concept Region"
- `nav.representationRegion`: "表示域" / "Representation Region"
- `nav.classificationAndMapping`: "分类与映射" / "Classification & Mapping"
- `nav.dimensionality`: "维度" / "Dimensionality"

#### Scenario: 语言切换显示
- **WHEN** 切换界面语言为中文（简体）
- **THEN** 侧边栏和系统标题中显示标准的中文名，例如“注册与公共设施”、“相关方与文献”、“概念域”、“表示域”、“分类与映射”、“维度”
- **WHEN** 切换界面语言为英文
- **THEN** 侧边栏和系统标题中显示对应的英文名，例如“Common Facilities”, “Parties & Documents”, “Concept Region”, “Representation Region”, “Classification & Mapping”, “Dimensionality”

### Requirement: 路由权限与菜单过滤
侧边栏的渲染和路由安全 MUST 与用户角色的权限绑定。系统管理菜单及其子路由只能对超级管理员角色（ADMIN）显示，普通用户登录后应被过滤隐藏且不能通过手动输入路径访问。

#### Scenario: 普通用户隐藏系统管理
- **WHEN** 拥有普通用户角色的账号登录并查看侧边栏
- **THEN** 系统管理（System）菜单大类及属下的“用户管理”、“系统设置”在侧边栏被完全过滤隐藏，且手动在浏览器地址栏输入路由时会拦截并重定向
