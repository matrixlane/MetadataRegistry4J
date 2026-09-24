# Design: registration-process-layout-persistence

## Context

画布状态两部分：节点位置（`nodes` 引用，拖动经 `onNodesChange` 回写）与连线折线段微调（`edgeOverrides`，绝对画布坐标）。此前全部为会话内状态。后端 jar 运行于工作目录，可写文件；前端经 vite 代理 `/api/v1` → 8081。

## Goals / Non-Goals

**Goals:**

- Admin 一键把当前画布保存为服务器本地 JSON；下次加载自动恢复。
- JSON 文件预置（首启播种）、可手工编辑（开放式脚本）。
- 权限：仅 SUPER_ADMIN 可保存；登录用户均可读（用于渲染）。

**Non-Goals:**

- 不做多布局槽位/版本历史；不做非 Admin 的布局共享。
- 不改内置默认 `NODE_POSITIONS`（作为无文件时的兜底）。

## Decisions

**D1：文件存储而非数据库。** 布局是"开放式脚本"，用户要求可直接编辑；JSON 文件 + 首启从 `classpath:layout-defaults/` 播种最贴合。原子写（临时文件 + `Files.move(REPLACE_EXISTING)`），失败不影响运行。

**D2：三层优先级合并。** 内置默认 < 已保存布局（挂载时加载到 `savedLayout`）< 会话内拖动（`edgeOverrides`）。重置布局 = 清会话覆盖 + 以 `savedLayout`/默认重建——基线即"上次保存"。

**D3：保存全量快照而非增量。** 保存时提交全部节点坐标 + 全部正交连线的 `{sx, laneY, tx}`（从当前渲染 path 解析）。快照自洽（节点与连线同一坐标系），避免"只存增量导致漂移"。

**D4：Schema 宽松校验。** 仅校验顶层为 JSON 对象且体积 ≤ 64KB；字段缺失/非法坐标由前端合并时容错（缺项回落下层）。

**D5：权限走 SecurityConfig 显式规则**（对齐 assistant/settings 模式）：PUT 限 `SUPER_ADMIN` 置于通用写规则之前；GET 随通用登录规则。

## Risks / Trade-offs

- [手工编辑 JSON 出错 → 前端容错回落默认，不会白屏] → 合并时逐项判空/判数。
- [并发保存冲突] → 原子写 + 覆盖语义（最后写入生效），单管理员场景可接受。

## Migration Plan

1. 部署后端（播种文件自动生成）。
2. 部署前端；页面重载后经代理 GET 布局并应用。
3. 回滚：删 `layout/registration-process-layout.json` 即回内置默认。

## Open Questions

无。
