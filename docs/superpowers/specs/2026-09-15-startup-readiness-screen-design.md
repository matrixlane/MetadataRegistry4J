# 应用启动就绪等待界面 — 设计文档

- 日期：2026-09-15
- 状态：已确认（auto 模式下经需求澄清与方案比选后定稿，直接进入实现）
- 关联 OpenSpec 变更：`add-startup-readiness-screen`

## 1. 背景与问题

`start.bat` 启动链路：构建/启动后端 Spring Boot（:8081）→ 启动 Vite dev server（:5173）。
后端完成启动需要数秒至数十秒，期间包含：

1. Spring 上下文刷新（Bean 装配、DDL/Flyway 迁移）——此阶段 8081 尚未监听；
2. 多个 `ApplicationRunner` / `CommandLineRunner` 数据初始化（`DataInitializer` 建 admin 账号、`LanguageInitializer`、`SystemDefaultNamespaceInitializer`、`ContextTestDataInitializer` 等）——**此阶段 8081 已可响应，但数据 seed 未完成，登录仍可能失败**。

启动期间用户立刻打开网站时：

- 路由守卫 `checkInitialized()` 请求 `/api/v1/init-wizard/status`，经 Vite 代理得到 500（后端未监听），axios 拦截器弹出"服务器内部错误"（截图顶部 2 个 toast）；
- 用户在登录页点击登录，`/auth/login` 失败，表单内 + 全局再弹 2 个错误（截图第 3 个）；
- 结果：用户面对 3 个红色报错，不知道应用"还在启动"。

## 2. 需求（澄清结论）

| # | 需求 |
| --- | --- |
| R1 | 后端就绪前，访问任何页面都显示全屏"应用启动中"等待界面，不出现"服务器内部错误"类提示 |
| R2 | 就绪后自动进入既有流程：系统未初始化 → `/init-wizard`；未登录 → `/login`；已登录 → 目标页 |
| R3 | 启动期间用户无法发起登录（等待界面在应用挂载前物理阻止表单交互） |
| R4 | 后端超过 60s 未就绪时，给出排查提示与"重试"入口 |
| R5 | 兼容旧版本后端：新端点不存在（401/403/404）时退化为探测 `/api/v1/init-wizard/status` |
| R6 | 后端已就绪时等待界面不得"闪现"（浮现前完成挂载移除） |
| R7 | 不影响既有 e2e / 单测；新增逻辑有单测覆盖 |

## 3. 方案比选

### 方案 A：后端就绪端点 + 前端全局启动屏（推荐）

- 后端：`StartupReadinessIndicator`（`@EventListener(ApplicationReadyEvent.class)` 后置位 `ready`）+ 匿名端点 `GET /api/v1/startup/status` 返回 `{ ready }`；`SecurityConfig` 放行该路径。
- 前端：`index.html` 内联静态启动屏（不依赖 JS bundle，首帧即显示；延迟淡入避免就绪场景闪现）；`src/utils/backendStartup.ts` 轮询探针；`main.ts` 挂载前 `await` 就绪。
- 优点：`ApplicationReadyEvent` = `SpringApplication.run()` 全部完成（含全部 Runner），"就绪"语义精确；改动面小；可单测。
- 代价：需重新构建后端 jar（开发日常本就要构建）。

### 方案 B：纯前端探测 `/init-wizard/status`

- 0 后端改动，但 Web 层可响应 ≠ 数据 seed 完成（admin 账号未建），登录仍有失败窗口；语义不准确，淘汰。

### 方案 C：Spring Boot Actuator readiness probe

- 原生机制，但需引入/配置 actuator probes，返回格式非项目 `ApiResponse` 约定，与现有 `/actuator/health` 放行配置耦合，淘汰。

## 4. 选定方案 A 设计细节

### 4.1 后端（metadata-registry-web）

新增 `io.github.matrixlane.metadataregistry.web.controller.StartupController`：

```
GET /api/v1/startup/status      （匿名）
→ ApiResponse<StartupStatusResponse(boolean ready)>
```

- 新组件 `StartupReadinessIndicator`：`AtomicBoolean ready`，初始 `false`；
  `@EventListener(ApplicationReadyEvent.class)` 置 `true`。不可回退（启动为单向过程）。
- `SecurityConfig` 的 permitAll 列表追加 `/api/v1/startup/status`。
- 已运行 GitNexus 影响分析：`securityFilterChain` 为 Spring Bean 回调方法，无代码调用方，新增放行行不影响任何 Java 调用方（风险可控）。
- 测试注意：`@WebMvcTest` 会把 `JwtAuthenticationFilter`（servlet Filter）扫入上下文，需 `@MockBean JwtService`（与 `InitWizardControllerTest` 同款），否则全量测试时 ApplicationContext 加载失败。

### 4.2 前端（metadata-registry-ui）

1. `index.html`：`<div id="app">` 之前内联 `<div id="boot-screen">` —— 品牌绿渐变背景 + 白色圆角卡片 + `/favicon.svg` logo + 「元数据注册中心 正在启动…」+ CSS 圆环 spinner + 说明文案 + 等待计时 + 超时重试按钮（纯 HTML/CSS，JS bundle 加载完成前即显示）。
   - **延迟淡入**：`opacity: 0` + `animation: boot-fade-in 0.4s ease-out 0.35s forwards`——后端已就绪时挂载移除发生在浮现之前，用户无感知；真正等待 >0.35s 时正常浮现。
2. 新增 `src/utils/backendStartup.ts`：
   - `waitForBackendReady()`：裸 `fetch`（不经 axios 拦截器，避免错误提示与 30s 长超时）轮询 `/api/v1/startup/status`；
     - `200 & code===0 & data.ready===true` → 就绪；
     - `200 & ready===false` → 继续轮询；
     - `401/403/404` → 旧版后端 fallback：探测 `/api/v1/init-wizard/status`，`200` 即视为就绪（R5）；
     - 网络错误/超时 → 继续轮询（每次请求 AbortController 2.5s 超时，间隔 1s）。
   - 每 500ms 更新启动屏计时；等待 > 60s 显示排查提示与「重试」按钮（`location.reload`）。
   - 就绪后由 `main.ts` 移除 `#boot-screen` 并挂载应用。
3. `src/main.ts`：`await waitForBackendReady()` 后 `app.mount('#app')`（TS ES2022/ESNext，top-level await 可行；`vite.config.ts` 追加 `build.target: 'es2022'`）。
4. 不改动 `Login.vue`、`router`、`auth store`：启动屏保证挂载时后端已就绪，既有 500 兜底文案仅在网络就绪后再次异常时生效。

### 4.3 start.bat（2026-09-15 验收事故驱动）

后端启动必须"无窗口 + 失败可排查"。实测结论：

| 方式 | 窗口 | 日志 | 结论 |
| --- | --- | --- | --- |
| `javaw -jar` | 无 | 无 | 启动失败不可见（事故根因） |
| `start ... javaw ... > log` | 无 | 0 字节（start 重定向不传给 javaw） | 不可用 |
| `cmd /c "javaw ... > log"` | Backend 窗口常驻（cmd 等 javapath shim） | 有 | 用户不接受窗口 |
| `powershell Start-Process javaw -RedirectStandardOutput/-RedirectStandardError` | 无 | 有 | **采用** |

### 4.4 测试

- 后端 `StartupControllerTest`（`@WebMvcTest + addFilters=false`）：`ready=false` / `ready=true` 两态响应；`StartupReadinessIndicatorTest`：默认 false、`ApplicationReadyEvent` 后置 true。
- 前端 `src/utils/backendStartup.spec.ts`（vitest + fake timers + mock fetch）：就绪即返回、未就绪轮询直至就绪、旧后端 401 走 fallback、网络错误继续轮询、超时显示重试提示。

### 4.5 验收

1. 重新构建后端 jar 后运行 `start.bat`，在后端未就绪窗口内打开 `http://localhost:5173`：只见启动屏，无任何错误提示；
2. 就绪后自动进入登录页（或初始化向导），可正常登录；
3. 后端已就绪时再打开网站：不闪现启动屏，直接进入；
4. `metadata-registry-web` 相关测试通过；`metadata-registry-ui` `npm run test` / `npm run build` 通过。
