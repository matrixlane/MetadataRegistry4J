## 1. 国际化词条配置

- [x] 1.1 在 `metadata-registry-ui/src/i18n/zh-CN.json` 中添加新增一级大类菜单及维度的中英文国际化翻译键值对：
  - `nav.commonFacilities`: "注册与公共设施"
  - `nav.partyAndDocument`: "相关方与文献"
  - `nav.conceptRegion`: "概念域"
  - `nav.representationRegion`: "表示域"
  - `nav.classificationAndMapping`: "分类与映射"
  - `nav.dimensionality`: "维度"

## 2. 路由表扩展与侧边栏布局重构

- [x] 2.1 修改 `metadata-registry-ui/src/router/index.ts`，进行路由注册和定义：
  - 在 `routes` 列表的 `children` 数组中添加关于 `dimensionality` 的定义，使其 `component` 指向 `() => import('@/views/placeholder/PlaceholderPage.vue')`，并设置 `meta: { title: '维度', routeKey: 'dimensionality' }`。
  - 在 `routeTable.register(...)` 列表中追加对 `dimensionality` 的声明，状态设为 `planned`：`{ key: 'dimensionality', path: '/dimensionality', title: '维度', status: 'planned' }`。
- [x] 2.2 修改 `metadata-registry-ui/src/layouts/AppLayout.vue`，重构 Element Plus `<el-menu>` 的嵌套层级：
  - 重构并重新排序“工作台”和“数据字典”一级菜单项。
  - 创建并重命名“注册与公共设施”二级子菜单：上下文、注册机构、注册人员、命名空间。
  - 创建并重命名“相关方与文献”二级子菜单：组织、个人、联系人、参考文献。
  - 创建“概念域”二级子菜单，包含：对象类、特性、数据元概念、概念域，以及计量单位、计量类（按照 Annex A 属于 `Concept` 派生），并加入新引入的“维度”二级项。
  - 创建“表示域”二级子菜单，包含：数据元、值域（按照 Annex A 属于 `Item` 派生表示设施）、数据类型、数据类型方案。
  - 创建“分类与映射”二级子菜单：分类体系、映射方案、映射网络、匹配结果管理。
  - 保留“系统管理”原二级子菜单项（用户管理、系统设置），并验证 `auth.isAdmin` 过滤条件。
- [x] 2.3 验证各二级子菜单的 `index` 属性与其原路由物理路径相绑定，确保点击可正常跳转。对于新追加的“维度”，确保点击跳转到 `/dimensionality`。

## 3. 路由与测试验证

- [x] 3.1 运行前端及后端服务，在浏览器中以不同角色用户（普通用户和超级管理员）登录，手动确认新一级大类的展现、二级菜单折叠、国际化语言切换、权限控制，特别需要点击“维度”子菜单验证其成功进入“占位页”。
- [x] 3.2 运行 Playwright E2E 测试，确认没有因菜单层级变化引发测试定位失败，如有失败则对应更新 E2E 脚本中的选择器定位逻辑。
