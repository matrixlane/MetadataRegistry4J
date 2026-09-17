# 实施计划：add-startup-readiness-screen

依据：`openspec/changes/add-startup-readiness-screen/`（proposal / design / specs / tasks）与 `docs/superpowers/specs/2026-09-15-startup-readiness-screen-design.md`。

## 顺序（TDD）

1. 后端先红后绿：
   - `StartupControllerTest`（`@WebMvcTest(StartupController.class) + @AutoConfigureMockMvc(addFilters=false)`，含 `@MockBean JwtService`）：ready 两态 → 确认红；
   - 实现 `web/startup/StartupReadinessIndicator.java` + `web/controller/StartupController.java` + `web/dto/startup/StartupStatusResponse.java` → 绿；
   - `StartupReadinessIndicatorTest`：默认 false、`ApplicationReadyEvent` 后置 true；
   - `SecurityConfig` 追加 `/api/v1/startup/status` 放行。
2. 前端先红后绿：
   - `src/utils/backendStartup.spec.ts`（fake timers + mock fetch）→ 确认红；
   - 实现 `src/utils/backendStartup.ts` → 绿；
   - `index.html` 启动屏（含延迟淡入）+ `src/main.ts` 挂载前等待 + `vite.config.ts` `build.target: 'es2022'`。
3. `start.bat`：后端启动改为 `powershell Start-Process javaw -RedirectStandardOutput/-RedirectStandardError`（无窗口 + 日志，实测结论见设计文档 4.3）。
4. 验证：
   - 后端 `mvn -pl metadata-registry-web test -Dtest='Startup*Test,InitWizardControllerTest'`（全量更佳）；
   - 前端 `npm run test`、`npm run lint`、`npm run build`；
   - `static/` 用新 dist 全量替换；
   - 冷启动人工验收：就绪窗口打开 5173 见启动屏 → 自动进登录页；已就绪打开不闪现。

## 关键约束

- 探测用裸 fetch，不经 axios 拦截器；500/网络错误一律按"未就绪"继续轮询，不弹错误。
- 就绪判定：`/api/v1/startup/status` 200 且 `code===0 && data.ready===true`；401/403/404 → fallback `/api/v1/init-wizard/status` 200 即就绪。
- 启动屏为 `index.html` 内联纯 HTML/CSS；`#boot-screen`/`#boot-timer`/`#boot-slow-tip`/`#boot-retry` 与模块约定 id 一致；DOM 操作全部防御式（元素缺失跳过）。
- 启动屏 CSS 延迟淡入（0.35s/0.4s），就绪场景不得闪现。
- 不改动 Login.vue / router / auth store。
- bat 文件内禁放中文注释（cmd 按 GBK 解析 UTF-8 注释会执行乱码报错）。
