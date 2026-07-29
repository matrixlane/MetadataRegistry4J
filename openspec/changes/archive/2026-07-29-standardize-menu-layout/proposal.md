## Why

当前系统的一级导航菜单虽然覆盖了元数据注册系统的核心实体，但分类较为杂乱，且命名和排列顺序未与 ISO/IEC 11179-3:2023 和 ISO/IEC 11179-31:2023 标准中的“元模型区域”（Metamodel Regions）与“数据规范”（Data Specification）严格对应。这使得熟悉标准的用户在操作时缺乏直观的合规对齐感。为了提升平台的专业度、规范性以及易用性，需要根据最新版的标准重新梳理和排列应用的一级和二级菜单。

## What Changes

重新组织前端侧边栏菜单（`AppLayout.vue`）和路由配置（`index.ts`），严格遵循 ISO/IEC 11179-31:2023 Annex A Consolidated class hierarchy 的分类定义：
- **注册与公共设施 (Registration & Common Facilities)**：将原“注册管理”大类进行对齐，包含上下文、注册机构、注册人员、命名空间，对齐标准核心公共设施和注册包。
- **相关方与文献 (Parties & Documents)**：将原“主体”大类重命名，包含组织、个人、联系人、参考文献，对齐标准的基本类（Basic Classes）和 Party/Document 包。
- **概念域 (Concept Region)**：整合所有继承自 `Concept` 基类的概念层大类，包含**对象类**、**特性**、**数据元概念**、**概念域**、**计量单位**、**计量类**。
  - 特别地，应用户要求，在此大类下新增**维度 (Dimensionality)** 作为规划中 (planned) 的菜单项，其路由指向占位页面（PlaceholderPage），以完整体现概念域元模型结构。
- **表示域 (Representation Region)**：整合直接继承自 `Item` 的数据表达/表示层大类，包含**数据元**、**值域**、**数据类型**、**数据类型方案**。
- **分类与映射 (Classification & Mapping)**：整合原分散的“分类管理”、“映射方案”、“映射网络”和“匹配结果管理”，对齐标准的分类（Clause 10 Classification）与映射（Clause 11 Mapping）两个关键区域。
- 更新中英文国际化语言包以配合新菜单组名称及新增的“维度”词条。

## Capabilities

### New Capabilities
- `standardize-menu-layout`: 根据 ISO/IEC 11179 2023 核心建模包和数据规范层重新排列和分类侧边栏导航菜单。

### Modified Capabilities
<!-- 无 -->

## Impact

- **前端导航与侧边栏渲染**：修改 `metadata-registry-ui/src/layouts/AppLayout.vue` 中的菜单配置和 Element Plus 侧边栏结构，包括加入维度的占位项。
- **国际化语言包**：修改 `metadata-registry-ui/src/i18n/zh-CN.json`，补充新增的菜单大类键值对（例如 `nav.partyAndDocument`, `nav.conceptRegion`, `nav.representationRegion`, `nav.classificationAndMapping` 以及 `nav.dimensionality`）。
- **路由表与配置**：修改 `metadata-registry-ui/src/router/index.ts` 中的 `NavigationRouteTable` 注册和配置，确保符合新菜单键的过滤和匹配。同时在 `createRouter` 路由中增加对 `/dimensionality` 的声明，指向占位组件 `PlaceholderPage.vue`。
- **自动化测试**：检查 Playwright E2E 测试中的侧边栏导航链接选择器是否受到菜单文本和层级结构调整的影响，并做出对应修正。
