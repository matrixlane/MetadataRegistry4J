# Proposal: registration-process-layout-persistence

## Why

注册流程页的画布布局（节点位置 + 连线折线段微调）目前只存在于浏览器内存，刷新即丢失。用户需要一个由系统 Admin 操作的"保存布局"能力：把当前调整后的布局固化到本地 JSON 文件，下次加载页面时自动恢复；该 JSON 作为开放式脚本文件预置、可手工编辑。

## What Changes

- 后端新增端点：
  - `GET /api/v1/registration-process/layout`（登录可读）：返回已保存的布局 JSON；启动时若文件缺失则自动用预置模板播种（`layout/registration-process-layout.json`，路径可用 `app.registration-process-layout-file` 覆盖）。
  - `PUT /api/v1/registration-process/layout`（仅 SUPER_ADMIN）：校验（JSON 对象、大小上限）后原子写入同一路径，立即生效。
- 预置模板打包于 `classpath:layout-defaults/registration-process-layout.json`（内容为当前确认的初始排布），首次启动复制为工作文件，用户可手工修改。
- 前端注册流程页：挂载时预加载布局（节点坐标 + 连线折线段覆盖），优先级：内置默认 < 已保存布局 < 本次会话拖动；页头新增"保存布局"按钮，仅 SUPER_ADMIN 可见，点击将当前画布状态（全部节点坐标 + 全部正交连线的 sx/laneY/tx）PUT 保存。
- "重置布局"重置到已保存布局（无则内置默认）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `registration-process`: 新增"布局持久化（Admin 保存与启动预加载）"需求。

## Impact

- 代码：`metadata-registry-web` 新增 layout controller/service/seed 资源与 SecurityConfig 规则；`metadata-registry-ui` 注册流程页视图、resources.ts、i18n（zh/en）、单测 mock。
- 数据：工作目录 `layout/registration-process-layout.json`（首次启动自动播种）。
- 安全：PUT 限 SUPER_ADMIN；GET 登录可读。
