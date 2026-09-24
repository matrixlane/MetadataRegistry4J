# Design: add-startup-readiness-screen

## Context

- 后端为单模块 Spring Boot（`metadata-registry-web`），启动期包含：上下文刷新（8081 尚未监听）→ Web 端口监听 → 若干 `ApplicationRunner`/`CommandLineRunner` 数据 seed（`DataInitializer` 建 admin、`LanguageInitializer`、`SystemDefaultNamespaceInitializer` 等）。
- 因此"Web 可响应"≠"数据初始化完成"：仅用 `/api/v1/init-wizard/status` 可达作为就绪信号，登录仍可能撞上账号未建好的窗口。
- 前端为 Vue 3 + Vite（dev :5173 代理 `/api` → :8081）；全局 axios 拦截器对 5xx 统一弹"服务器内部错误"。
- 详见 `docs/superpowers/specs/2026-09-15-startup-readiness-screen-design.md`（Superpowers 阶段设计，本文为其 OpenSpec 化）。

## Goals / Non-Goals

**Goals:**

- 精确的就绪语义：`ready=true` 表示 `SpringApplication.run()` 全部完成（含全部 Runner）。
- 启动窗口内零错误提示、零业务请求；就绪后无缝进入既有流程（向导 / 登录 / 目标页）。
- 旧版本后端（无新端点）可平滑降级探测。
- 前后端新增逻辑均有单测。

**Non-Goals:**

- 不改动登录、初始化向导、路由守卫既有逻辑（保留其错误兜底）。
- 不做真实进度条（启动进度不可知，使用不确定态等待 UI）。
- 不引入 actuator / 新依赖；不改动数据库 schema。

## Decisions

1. **就绪信号源选择 `ApplicationReadyEvent` 而非 Web 端口可响应**。
   - 备选 B（纯前端探测 `/init-wizard/status`）：实现零后端改动，但无法区分"Web 层已起、数据 seed 未完成"，登录竞态残留，淘汰。
   - 备选 C（Actuator readiness probe）：原生但需引入 actuator 与 probes 配置，返回非 `ApiResponse` 约定格式，淘汰。
2. **前端等待为"挂载前阻塞 + 静态启动屏"**而非组件内 loading。
   - 启动屏写在 `index.html`（纯 HTML/CSS），JS bundle 未加载完成前即可显示，避免"白屏 → JS 加载 → 再显示 loading"的闪烁；`main.ts` top-level await 探针成功后 mount 并移除启动屏，期间无任何业务请求与拦截器错误提示。
   - 启动屏**延迟淡入**（0.35s 后开始 0.4s 淡入）：后端已就绪的正常打开在启动屏浮现前完成挂载与移除，避免"闪现"；真正需等待时（>0.35s）再浮现。
   - 备选（登录页内禁用表单 + 提示）：仅覆盖登录页，已登录用户刷新 / 深链场景仍会先撞守卫报错，淘汰。
3. **探测请求使用裸 `fetch` 而非 axios 实例**。
   - axios 实例有 30s 超时与统一错误提示，不适合每秒级的轮询探测；裸 fetch + `AbortController`（2.5s 请求超时、1s 轮询间隔） + 自行判定状态码，完全绕开 ElMessage。
4. **向后兼容策略**：新端点返回 401/403/404（旧版后端未注册该路径，被 security entry point 拒绝）时，退化为探测 `/api/v1/init-wizard/status` 可达即视为就绪。
5. **start.bat 后端启动方式**（2026-09-15 验收事故驱动）：必须为"启动失败可见"提供手段，同时不能引入常驻窗口。演进结论：
   - `javaw -jar`：无窗口但失败不可见（事故根因）；
   - `start` + 直接重定向：javaw 写不进日志（0 字节）；
   - `cmd /c` 中转：日志生效但 cmd 等待 javapath shim，"Backend" 窗口伴随后端整个生命周期；
   - **`powershell Start-Process javaw -RedirectStandardOutput/-RedirectStandardError`**：原生句柄重定向 + 不等待，无窗口、日志生效，采用。

## Risks / Trade-offs

- [后端启动失败（端口占用、DB 连接失败）→ 用户永远卡在启动屏] → 等待 > 60s 显示排查提示（检查 start.bat 后端日志）与「重试」按钮（刷新重探测）；提示文案明确指向"后端未就绪"。
- [启动屏内联 CSS/HTML 与登录页视觉漂移] → 启动屏复用品牌绿渐变（`#14301f/#1e4530/#2d5a3d`）、同款 favicon 与圆角白卡风格，文案与登录页头部一致。
- [Vite 代理在后端未监听时返回 500，裸 fetch 拿到 500] → 500 一律按"未就绪"继续轮询，不展示给用户。
- [top-level await 兼容性] → tsconfig 已为 ES2022/ESNext，Vite build target 显式设为 es2022；无风险。
- [backend-stdout.log 运行期增长（应用 DEBUG 日志全量）] → 每次启动截断；仅排错用途，可随手删除。

## Migration Plan

1. 后端新增端点 + 放行，重新 `mvn package`（`start.bat` 自动使用新 jar）。
2. 前端随下一次构建 / dev server 生效。
3. 回滚：还原两个前端文件与后端两个新类、一行放行即可，无数据迁移。

## Open Questions

无（遗留问题均已在上文决策中闭环）。
