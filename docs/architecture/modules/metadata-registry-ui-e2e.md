> 本文档由 GitNexus wiki 生成于 2026-09-13（kimi-for-coding，--lang chinese）。内容变化时请重新运行 `gitnexus wiki --lang chinese` 生成，勿手工改写。

# metadata-registry-ui/e2e — 端到端测试模块

本模块基于 Playwright Test 为 `metadata-registry-ui`（Vue 3 + Element Plus 前端）提供端到端（E2E）测试。由于 E2E 环境无法依赖 Spring 后端，测试的核心策略是：**通过 `page.route` 拦截并 mock 全部 `/api/v1/*` 请求**，以真实的前端代码驱动浏览器，验证页面渲染、路由守卫、表单交互与 API 调用契约。

## 整体架构

```mermaid
flowchart TD
    A[Playwright 测试用例] -->|调用| B[auth.setup.ts<br/>setupAuthenticatedPage]
    A -->|拦截| C[page.route<br/>**/api/v1/**]
    C -->|统一响应格式| D[{ code: 0, data, message }]
    A -->|启动| E[Playwright webServer<br/>前端 dev server]
    F[Python 调试脚本<br/>check_*.py] -->|直连真实后端| G[localhost:8181]
    A -.->|调试类用例直连| G
```

- **响应契约**：所有 mock 遵循后端统一响应格式 `{ code: 0, data, message: 'success' }`。`code: 0` 表示成功，前端 axios 拦截器依赖该结构解包 `data`。
- **认证状态**：通过 `page.addInitScript` 向 `localStorage` 注入 `token`，模拟已登录会话，绕过路由守卫的登录检查。
- **初始化守卫**：路由守卫在每次受保护路由导航时调用 `/api/v1/init-wizard/status`。几乎所有测试用例都需要 mock 该接口返回 `{ initialized: true, runningMode: 'SINGLE' }`，否则会被重定向到 `/init-wizard`。

## 关键组件

### `auth.setup.ts` — 共享认证辅助函数

```ts
export async function setupAuthenticatedPage(page: Page)
```

被 `classification.spec.ts`、`factory-reset.spec.ts`、`mapping-network.spec.ts`、`menu.spec.ts` 复用。完成三件事：

1. Mock `/api/v1/init-wizard/status` 为已初始化；
2. Mock `/api/v1/auth/me` 返回 `SUPER_ADMIN` 用户（`testuser`）；
3. Mock `/api/v1/auth/login` 并注入 token 到 `localStorage`。

需要非管理员场景时（如 `menu.spec.ts` 中验证"系统菜单对非管理员隐藏"），测试会用更具体的 `page.route` 覆盖默认的 `/auth/me` mock。

### `admin-emergency-login-verify.spec.ts`

验证管理应急登录相关需求（任务 4.2 / 4.4），全部基于 mock：

- **注册开关控制登录页**：`/auth/allow-registration` 返回 `false` 时登录页隐藏"新用户注册"链接，返回 `true` 时显示。
- **系统设置持久化**：`/system-settings` 页面上切换"允许注册新用户"开关后，通过 `expect.poll` 轮询捕获到的 PUT 请求查询参数 `allowRegistration`，验证 API 真实被调用且值正确。
- **用户列表**：`/app-users` 展示"上次登录时间"列（`lastLoginAt` 为 `null` 时显示 `-`）；当前登录用户所在行禁用"删除/禁用"按钮；普通用户行提供"重置密码"按钮，点击后走 Element Plus 二次确认框 → 调用 `/accounts/{id}/reset-password` → 弹出一次性临时密码对话框（含"此密码仅能成功登录一次"提示）。
- 共享的 `ok<T>(data)` 辅助函数和 `sampleUsers`、`adminProfile` 样本数据定义在文件顶部。

### `login-flow.spec.ts`

登录流程核心场景：

- 普通用户登录成功跳转 `/dashboard`；
- 超级管理员首次登录（`forceChangePassword: true, lastLoginAt: null`）弹出"安全提示：需要修改密码"对话框，**不显示原密码输入框**；
- 应急登录（`emergencyLogin: true`）同样强制改密，且提交的 `oldPassword` 为空字符串 `''`；
- 密码错误时显示 `el-message--error` 友好提示，且不触发 ErrorBoundary 全屏错误页（`.error-boundary` 计数为 0）。

注意：该文件中 `forceChangePassword` 通过闭包变量 `passwordChanged` 在 login 与 me 两个 mock 之间共享状态，模拟"改密后强制标志消失"。

### `init-wizard.spec.ts`

版本感知初始化向导测试，由内部辅助函数 `mockWizardApis(page, opts)` 统一装配 mock，覆盖：

- `/init-wizard/status`（含 `edition` 字段，commit 前为 `null`）
- `/init-wizard/wizard-state`、`/init-wizard/setup`、`/init-wizard/commit`
- `/datasource/config`（GET 返回当前数据源，PUT 直接确认）

测试场景：
- **个人版完整流程**：版本选择 → 数据库（保持当前 SQLite）→ 治理配置（预填"注册管理员"）→ 命名空间 → 注册规范 → 设置管理员密码 → commit 后跳转 `/login`。
- **跳过命名空间**：关闭"创建基于注册机构的命名空间"开关，断言 `createNamespace: false` 随 setup 请求提交；注册规范开关必选（`is-disabled` 且保持开启）。
- **团队版流程**：可正常走完；`setup` 请求断言 `edition: 'TEAM'`、`identifierStrategy: 'UUID'`、`versionPolicy: 'SEMVER'`。
- **团队版 + SQLite 阻断**：数据库步骤点击下一步时提示"团队版不支持 SQLite"并停留在当前步骤。

### `factory-reset.spec.ts` 与 `factory-reset-init-guard.spec.ts`

- `factory-reset.spec.ts`：验证完整恢复出厂设置流程——危险区按钮 → 风险警告对话框 → 密码验证对话框 → 覆盖层"正在恢复出厂设置…" → 跳转 `/init-wizard`。mock 中通过 `initStatus` 闭包变量在重置成功后翻转为未初始化。
- `factory-reset-init-guard.spec.ts`：**回归测试**。修复前 `executeReset` 执行 `localStorage.clear()` 清除 token 后，路由守卫先命中"未登录"分支把用户弹到 `/login` 而非 `/init-wizard`。修复后初始化检查前置，测试验证离开向导再访问根路径时仍落回 `/init-wizard`。

### `classification.spec.ts`

分类方案与节点管理测试。`beforeEach` 中按 URL 路径精细分层 mock，并大量使用 `route.fallback()` 让更具体的路由规则优先处理：

- 列表/创建：`/classification-schemes**`（GET 分页、POST 回显）
- 单体操作：`/classification-schemes/scheme-001**`（GET/PUT/DELETE）
- 树形懒加载：`/nodes/children**` 根据 `parentId` 查询参数返回根节点或子节点
- 节点 CRUD、移动（`/move`）、级联删除（`/cascade`）
- 兜底路由 `**/api/v1/**` 返回空分页，防止页面初始化时 401 跳转

激活用例验证方案选择、树渲染、点击节点加载详情面板、展开懒加载子节点；创建/编辑/删除方案及节点的用例当前标记为 `test.skip`。

### `mapping-network.spec.ts`

映射网络可视化测试，依赖 `fixtures/network-data` 中的 `createMockNetworkResponse`、`createTruncatedResponse` 与 `MOCK_CENTER_ID`。mock 解析 `filterTypes`、`depth`、`centerId` 查询参数动态生成响应。覆盖：查询渲染（节点/边计数徽标）、单/多映射类型过滤、节点数超 500 时的截断警告（`.el-alert--warning`）、重置按钮、空状态、深度选择 1–3。

### `menu.spec.ts`

侧边栏菜单结构回归测试。重点包括：

- 菜单分组与子项顺序断言（概念模块、表示模块、基础与核心模块、标识与命名模块、注册模块、分类模块、项目映射模块等，见 `openspec/specs/standardize-menu-layout/spec.md`）；
- 新接入菜单项的导航断言（值含义、文档类型、注册流程、项目映射）与规划中项（维度、角色、分类项）的占位页断言；
- 启动就绪门控 mock：`/api/v1/startup/status` 必须返回 `ready: true`，否则应用停留在启动屏；
- "系统"菜单仅对管理员可见；
- 折叠按钮切换 `.collapsed` 类；
- **`"我的"` 位置守卫测试**：通过 `data-testid="nav-my"` / `data-testid="sidebar-footer"` / `data-testid="header-right"` 断言"我的"固定在侧边栏底部而非头部，防止 AI 编辑回归移动该入口；折叠后图标仍可见、文字标签隐藏。

### 调试与诊断类文件

- `debug_real.spec.ts` / `debug_namespace.spec.ts`：连接**真实后端**（`admin` / `Admin@123`），通过 `page.on('console' | 'pageerror' | 'request' | 'response')` 打印完整 API 流量，截图输出到 `e2e/` 目录。用于排查真实环境页面加载问题。
- `test_emergency.spec.ts`：真实应急登录流程验证。从 `../emergency-code.txt` 读取应急码登录，强制改密后验证 `/my-profile` 正常加载且无崩溃页，最后通过 `page.evaluate` 调用真实接口把密码恢复为 `Admin@123`。
- `test_save_designation.spec.ts`：验证带指称（designation）、上下文（context）、定义的数据元新建表单可完整填写并保存。记录全部请求负载与响应体便于诊断。
- `test_tab_validation.spec.ts`：验证表单跨标签页校验——当"注册标识"页留空而直接保存时，自动切回该标签页、标签头显示 `.tab-error-icon` 警告徽标，并弹出"表单验证失败"全局提示。
- `check_elements.py` / `check_sizes.py` / `check_sql.py`：独立的 Python 脚本，登录真实后端后按不同 `size` 参数请求 `/data-elements` 分页接口，测量响应耗时，用于后端性能/分页问题定位。不属于 Playwright 测试套件。

## Mock 编写约定

1. **路由 specificity**：Playwright 按注册顺序匹配路由，先注册的规则优先。需要多层级 mock 时（如分类模块），为具体路径注册规则并对不匹配的请求调用 `route.fallback()`，最后注册 `**/api/v1/**` 兜底。
2. **状态共享**：用例内用闭包变量（如 `initStatus`、`passwordChanged`、`resetCalledFor`）在 mock 与断言之间传递状态；跨调用暂存可挂到 `globalThis`（如 `__lastAllowRegistration`）。
3. **状态码语义**：业务错误（如密码错误）用 HTTP 401 + `code: 401`，前端按 `message` 展示 `ElMessage`，而非进入 ErrorBoundary。
4. **断言 API 契约**：mock handler 内部直接用 `expect` 校验请求体/查询参数（如 `expect(postData.edition).toBe('TEAM')`），使接口契约校验内聚在用例中。

## 运行方式

测试由 Playwright 配置（`webServer` 项）自动启动前端 dev server（默认 `localhost:5173`），无需手动起前端。常规执行：

```bash
npx playwright test                          # 运行全部 spec
npx playwright test e2e/login-flow.spec.ts   # 运行单个文件
npx playwright test -g "团队版完整初始化"     # 按用例名过滤
```

注意：调试类用例（`debug_*`、`test_emergency`）与 Python 脚本依赖真实后端（`localhost:8181`）及特定数据文件（`../emergency-code.txt`），在无后端环境下会失败，不应纳入 CI 常规套件。