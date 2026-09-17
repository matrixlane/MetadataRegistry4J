# Proposal: add-startup-readiness-screen

## Why

`start.bat` 启动后后端需要数秒至数十秒完成 Spring Boot 启动（含多个 `ApplicationRunner` 数据初始化）。启动窗口内打开网站，前端请求经 Vite 代理得到 500，登录页与路由守卫连续弹出多处"服务器内部错误"，用户无法判断应用"仍在启动"而不是"已损坏"。需要一个对用户友好的等待界面，并就绪后才允许登录。

## What Changes

- 后端新增匿名端点 `GET /api/v1/startup/status`，基于 `ApplicationReadyEvent` 返回 `{ ready }`（启动全部阶段完成后才为 `true`）。
- `SecurityConfig` 放行上述端点（匿名可访问）。
- 前端 `index.html` 内联全屏启动等待界面（不依赖 JS bundle，首帧即显示；延迟淡入避免就绪场景"闪现"）。
- 前端新增启动探测模块：`main.ts` 挂载前轮询探针；就绪后自动移除等待界面并进入既有路由流程；超过 60 秒未就绪显示排查提示与重试入口。
- 探测端点不存在（旧版本后端）时退化为探测 `/api/v1/init-wizard/status` 可达。

## Capabilities

### New Capabilities

- `startup-readiness`: 应用进程启动就绪的探测契约（后端就绪探针端点）与前端全局启动等待界面行为。

### Modified Capabilities

（无既有能力的需求变更；初始化向导 `system-initialization` 流程不变。）

## Impact

- `metadata-registry-web`：新增 2 个类（就绪指示组件、Controller），`SecurityConfig` 追加一行放行；需重新构建 jar。
- `metadata-registry-ui`：新增 `src/utils/backendStartup.ts` 及单测；`index.html`、`src/main.ts`、`vite.config.ts`（build target es2022）小幅修改。
- `start.bat`：后端启动改为 PowerShell `Start-Process javaw` + stdout/stderr 日志重定向（无窗口、失败可排查）。
- 无数据库 schema 变更、无第三方依赖变更；既有 API 行为不变。
