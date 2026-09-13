# UI 主题定制方案 —— 深绿 SaaS 风格

> 参考对象：Figma Make demo「EMS Control 能源管理仪表盘」（https://syrup-text-13395340.figma.site/）
> 落地目标：`metadata-registry-ui`（Vue 3 + Element Plus + ECharts）
> 原则：**只改主题与布局样式，不引入 Tailwind，不改变既有组件体系与信息密度。**

## 1. 现状与目标差距

| 维度 | 现状 | demo 风格 | 差距 |
| --- | --- | --- | --- |
| 品牌色 | 蓝色 `#2563eb` | 深绿 + 亮绿 | 需整体换色 |
| 侧边栏 | 白底浅灰字 | 深绿底、亮绿胶囊激活项 | 需反色重设计 |
| 顶部 header | 白色通栏，含品牌、身份、用户、登出 | 无 header，品牌与用户都在侧边栏 | 结构调整（可选） |
| KPI 卡片 | 白底彩条顶部线 | 深绿底白字 + 右上角胶囊徽章 | 新增深色卡片变体 |
| 图表 | 手写 SVG 占位 | ECharts 级甜甜圈图/折线图 | 用 vue-echarts 实现 |
| 圆角/阴影 | 12px 卡片、轻阴影 | 同款（二者已接近） | 无需大改 |
| 登录页 | 蓝色渐变 | —— | 跟随品牌色换绿 |

## 2. 设计令牌（Design Tokens）

### 2.1 品牌色板

| 令牌 | 色值 | 用途 |
| --- | --- | --- |
| `brand-900` | `#1e4530` | 侧边栏背景 |
| `brand-700` | `#2d5a3d` | 深色 KPI 卡片背景 |
| `brand-500` | `#3f8f5f` | hover、次要强调 |
| `primary-500` | `#16a34a` | Element Plus primary（主按钮、链接、激活态） |
| `primary-300` | `#4ade80` | 侧边栏激活胶囊底、图表主色 |
| `primary-100` | `#dcfce7` | 浅绿徽章底、选中行底 |
| `page-bg` | `#f5f8f5` | 主内容区背景（微泛绿的浅灰） |
| `text-primary` | `#1f2937` | 正文（维持现状） |
| `text-on-dark` | `#e7f0e9` | 深底上的主文字 |
| `text-on-dark-dim` | `#9db8a6` | 深底上的次要文字 |

### 2.2 功能色（Element Plus 映射）

| EP 语义 | 色值 | 说明 |
| --- | --- | --- |
| `primary` | `#16a34a` | 主操作 |
| `success` | `#059669` | 成功态（与品牌绿拉开明度差） |
| `warning` | `#d97706` | 维持琥珀 |
| `danger` | `#dc2626` | 维持红 |
| `info` | `#6b7280` | 维持灰 |

圆角维持现状（`base: 8px / small: 6px`），卡片 12px 不变。

### 2.3 注册状态色板（MDR 专有，demo 没有）

状态语义**不**套用"绿=好"，按生命周期阶段区分色相：

| 注册状态（ISO/IEC 11179） | 语义色 | el-tag type / 自定义 |
| --- | --- | --- |
| 已记录 recorded | 灰蓝 `#64748b` | info |
| 合格 qualified | 青 `#0d9488` | 自定义 |
| 首选 preferred | 品牌绿 `#16a34a` | success |
| 标准 standard | 深绿描边 `#2d5a3d` | 自定义 outline |
| 被取代 superseded | 琥珀 `#d97706` | warning |
| 已废止 retired | 灰 `#9ca3af` 描边 | info outline |
| 草稿/进行中 | 蓝灰 `#60a5fa`（小面积） | 自定义 |
| 审核驳回 | 红 `#dc2626` | danger |

## 3. 变量映射清单（要改的文件）

### 3.1 `src/styles/element-variables.scss`

```scss
@use 'element-plus/theme-chalk/src/common/var.scss' with (
  $colors: (
    'primary': ('base': #16a34a),
    'success': ('base': #059669),
    'warning': ('base': #d97706),
    'danger':  ('base': #dc2626),
    'info':    ('base': #6b7280),
  ),
  $border-radius: (
    'base': 8px,
    'small': 6px,
    'round': 20px,
  )
);
```

### 3.2 `src/styles/layout.scss` CSS 变量块（`:root`）

```scss
:root {
  --app-bg: #f5f8f5;
  --app-sidebar-bg: #1e4530;
  --app-sidebar-text: #c8dccd;
  --app-sidebar-border: rgba(255, 255, 255, 0.08);
  --app-sidebar-hover-bg: rgba(255, 255, 255, 0.08);
  --app-sidebar-active-bg: #4ade80;          /* 亮绿胶囊 */
  --app-sidebar-active-text: #14301f;        /* 胶囊上的深绿文字 */
  --app-header-bg: #ffffff;
  --app-header-border: #e5e7eb;
  /* 宽度、header 高度等尺寸变量不变 */
}
```

连带要改的硬编码色（均在 `layout.scss`）：

- `.brand-text` 颜色 `#2563eb` → `#4ade80`
- `.collapse-btn:hover` 色 `#2563eb` / 底 `#eff6ff` → `#4ade80` / `rgba(255,255,255,0.08)`
- 菜单 `is-active` 的 `box-shadow: inset 3px 0 0 #2563eb` → 去掉，改为 demo 的整颗胶囊填充（激活项 `border-radius: 8px` 已有）
- 菜单组标题色 `#9ca3af` → `#7fa38b`
- `.stat-card::before` 渐变 `#2563eb→#3b82f6` → `#16a34a→#4ade80`
- `.stat-card:hover` 阴影 `rgba(37,99,235,.1)` → `rgba(22,163,74,.12)`
- `.stat-value` 色 `#1e40af` → `#1e4530`
- `.login-layout` 渐变 `#1e3a8a/#2563eb/#3b82f6` → `#14301f/#1e4530/#2d5a3d`，光斑 `#60a5fa/#1e40af` → `#3f8f5f/#16a34a`

### 3.3 ECharts 主题（新增小工具）

新增 `src/utils/echartsTheme.ts`，注册自定义主题 `mdr-green`：

```ts
// 同色系多阶绿（甜甜圈/饼图），与 demo 一致的单色相递进
export const mdrGreenPalette = [
  '#16a34a', '#4ade80', '#2d5a3d', '#86efac', '#1e4530', '#bbf7d0',
];
```

在 `main.ts` 或 Dashboard 内 `use(...)` 前调用 `echarts.registerTheme('mdr-green', {...})`，`vue-echarts` 组件上以 `theme="mdr-green"` 使用。

## 4. 布局改造点清单

### 4.1 侧边栏（`src/layouts/AppLayout.vue`）—— 改动最大

1. 整体反色：白底 → `brand-900` 深绿底（仅改 CSS 变量与上述硬编码色，结构不动）。
2. 激活项：改为 demo 的"亮绿胶囊"（整项填充 `#4ade80`、深绿文字、右侧 4px 圆点小指示可选），替换现有的"浅蓝底 + 左侧 3px 竖条"。
3. 折叠按钮配色适配深底。
4.（可选，推荐）参照 demo 把**用户卡片移到侧边栏底部**：头像圆形绿底 + 姓名 + 角色；header 右侧只保留 Registrar 身份标签与登出。demo 的用户卡片在侧栏底部，这是其显著特征之一。
5. 品牌区保留（demo 也是品牌在侧栏顶部），`MDR` 文字色改亮绿。

### 4.2 顶部 header

- 保留（承载 Registrar 切换、登出，demo 没有这些功能），视觉不变（白底），只把其中的 `el-tag type="primary"` 自动随主题变绿，无需改代码。
- 不追求 demo 的"无 header"形态——那是监控屏形态，不适合功能型系统。

### 4.3 Dashboard（`src/views/dashboard/Dashboard.vue`）

1. **KPI 卡片深色变体**：新增 `.stat-card--dark`（`brand-700` 底、白字、右上角胶囊状态徽章），用于 4 个主统计；保留现有浅色 `.stat-card` 用于次要统计。demo 特征：大数字 + 小号单位 + 右上胶囊徽章（normal/high/optimal）+ 底部趋势箭头百分比。
2. **甜甜圈图**：把手写 SVG 占位换成 `vue-echarts` 环形图（中心显示总数），色板用 `mdrGreenPalette`；图例置底，与 demo 一致。
3. **新增趋势折线图**（可选）：注册项变更趋势，绿线 + 阈值虚线样式。
4. 欢迎语区维持现状（demo 同款 "Hello, xxx 👋" 结构我们已经有了）。

### 4.4 登录页

- 渐变与光斑换绿色系（见 3.2），卡片不变。

### 4.5 状态徽章

- 各列表页（`DataTablePage.vue` 及注册状态相关组件）的注册状态 `el-tag` 按 2.3 色板统一；超出 EP 五种 type 的用 `color` 属性自定义，集中成一个 `src/utils/statusColor.ts` 映射，避免散落。

## 5. 明确不做的事

- 不引入 Tailwind / 不重写组件库；一切通过 EP 变量 + `layout.scss` + 少量组件样式完成。
- 不降低表格/表单信息密度（demo 的宽松留白只借用于 Dashboard 与卡片头）。
- 不改路由、导航分组、i18n 文案。
- 不做暗色模式（demo 本身也是浅色 + 深侧栏的混合，不是真暗色）。

## 6. 实施顺序建议

| 步骤 | 内容 | 影响面 |
| --- | --- | --- |
| 1 | `element-variables.scss` + `layout.scss` 变量换色 | 全局，立即可见 |
| 2 | 侧边栏反色细节 + 激活胶囊 | AppLayout |
| 3 | Dashboard：ECharts 甜甜圈图 + 深色 KPI 变体 | 单页 |
| 4 | 登录页换色 | 单页 |
| 5 | 注册状态色板统一（`statusColor.ts`） | 多列表页，可分批 |

每步完成后 `npm run dev` 目检 + `npm run lint`，全部完成后跑 `npm run e2e` 回归。

## 7. 验收要点

- [ ] 全局无残留蓝色（`#2563eb/#3b82f6/#eff6ff` 在样式表中清零）
- [ ] 侧边栏深绿底 + 亮绿胶囊激活项，折叠态图标居中正常
- [ ] Dashboard 甜甜圈图为 ECharts 实现，中心总数正确
- [ ] 中英文界面下激活胶囊、徽章文字不溢出
- [ ] `npm run lint`、`npm run test`、`npm run e2e` 通过
