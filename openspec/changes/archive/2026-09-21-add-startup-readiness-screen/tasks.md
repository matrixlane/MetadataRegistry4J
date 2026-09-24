# Tasks: add-startup-readiness-screen

## 1. 后端就绪探针

- [x] 1.1 编写 `StartupControllerTest`（`@WebMvcTest + addFilters=false` 范式）：`ready=false` 与 `ready=true` 两态均返回 HTTP 200 及 `data.ready`；运行确认测试失败（先红）
- [x] 1.2 实现 `StartupReadinessIndicator`（`ApplicationReadyEvent` 后置位）与 `StartupController`（`GET /api/v1/startup/status` 返回 `ApiResponse` 包装 `{ready}`），跑通 1.1 测试（后绿）
- [x] 1.3 `SecurityConfig` permitAll 列表追加 `/api/v1/startup/status`；用 `mvn -pl metadata-registry-web test -Dtest='Startup*Test'` 验证通过，并确认既有 `InitWizardControllerTest` 不受影响

## 2. 前端启动等待界面

- [x] 2.1 编写 `src/utils/backendStartup.spec.ts`（vitest + fake timers + mock `fetch`）：就绪即返回、未就绪轮询直至就绪、401/403/404 走 `/init-wizard/status` fallback、网络错误继续轮询；运行确认测试失败（先红）
- [x] 2.2 实现 `src/utils/backendStartup.ts`（裸 fetch 轮询、AbortController 2.5s 超时、1s 间隔、60s 超时提示与重试入口、启动屏计时更新与移除），跑通 2.1 测试（后绿）
- [x] 2.3 修改 `index.html` 内联启动屏（品牌绿渐变背景、白卡、favicon、启动中文案、spinner、延迟淡入避免闪现、计时与重试按钮）与 `src/main.ts`（top-level await 等待就绪后 mount 并移除启动屏）；`vite.config.ts` 追加 `build.target: 'es2022'`；`npm run build` 通过（vue-tsc + vite build）

## 3. 整体验证

- [x] 3.1 后端 `mvn -pl metadata-registry-web test` 通过（至少相关模块无回归）
- [x] 3.2 前端 `npm run test` 与 `npm run lint` 通过
- [x] 3.3 重新打包 jar 后运行 `start.bat`，在后端未就绪窗口打开 `http://localhost:5173` 观察：只见启动等待界面、无错误提示；就绪后自动进入登录页并可正常登录（冷启动人工验收，2026-09-15 完成：探针 000→ready=true 实测通过）
