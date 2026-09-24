# startup-readiness — 应用启动就绪探测与等待界面

## Purpose

定义应用进程启动就绪的探测契约与前端启动等待行为：后端启动（含数据初始化）完成前，用户看到友好的等待界面且无法登录；就绪后自动进入既有流程。

## ADDED Requirements

### Requirement: 就绪探针端点

系统 SHALL 提供匿名端点 `GET /api/v1/startup/status`，响应体为统一 `ApiResponse` 包装的对象，含 `ready` 布尔字段。`ready=true` 当且仅当应用启动全部阶段完成（含所有数据初始化 Runner）；启动完成前该端点 MUST 可达且返回 HTTP 200、`ready=false`。就绪状态 MUST NOT 在进程生命周期内回退。

#### Scenario: 启动完成前查询

- **WHEN** 应用正在启动（Web 层已监听但初始化 Runner 未完成，或任何可响应状态）
- **THEN** 端点返回 HTTP 200 且 `data.ready=false`

#### Scenario: 启动完成后查询

- **WHEN** `SpringApplication.run()` 全部阶段完成
- **THEN** 端点返回 HTTP 200 且 `data.ready=true`

#### Scenario: 匿名访问

- **WHEN** 未携带任何认证信息请求该端点
- **THEN** 返回 HTTP 200（不返回 401/403）

### Requirement: 前端启动等待界面

前端 SHALL 在应用挂载前探测后端就绪。未就绪期间，界面 MUST 显示全屏启动等待界面（含品牌标识、启动中提示与不确定态加载动画），MUST NOT 发起任何业务 API 请求，MUST NOT 显示"服务器内部错误"类错误提示。后端已就绪时，等待界面 MUST NOT 对用户可见闪现（就绪场景下探测与挂载完成前等待界面尚未浮现）。

#### Scenario: 后端未就绪时打开网站

- **WHEN** 后端尚未就绪时用户打开前端任意页面
- **THEN** 用户看到启动等待界面，页面无任何错误提示，且不产生业务 API 请求

#### Scenario: 就绪后自动进入

- **WHEN** 探测到 `ready=true`
- **THEN** 等待界面被移除，应用进入既有流程：系统未初始化进入初始化向导，未登录进入登录页，已登录进入目标页

### Requirement: 长时间未就绪提示

等待后端就绪超过 60 秒时，等待界面 MUST 显示排查提示（指引检查后端启动窗口）与重试入口（刷新重探测）。

#### Scenario: 后端启动失败或极慢

- **WHEN** 等待超过 60 秒仍未就绪
- **THEN** 等待界面显示排查提示与重试按钮；点击重试后重新开始探测

### Requirement: 旧版本后端兼容

探测端点返回 401、403 或 404（旧版本后端未提供该端点）时，前端 SHALL 退化为探测 `GET /api/v1/init-wizard/status` 的可达性：该端点可达（HTTP 200）即视为后端就绪。

#### Scenario: 旧版本后端

- **WHEN** 探测 `/api/v1/startup/status` 得到 401/403/404，且 `/api/v1/init-wizard/status` 返回 HTTP 200
- **THEN** 前端视为就绪并进入既有流程
