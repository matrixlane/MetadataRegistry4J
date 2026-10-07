# Design: default-registrar-auto-activation

## Context

现状：`resolveActiveRegistrar` 优先账号 `active_registrar_id`，失效回退"首个有效身份"；AppLayout 多身份时渲染下拉切换器 + 选择对话框；`listMyContacts` 按绑定顺序返回（默认可能不置顶）。

## Decisions

- **解析链**（同事务内解析+按需持久化）：①持久化身份仍有效（身份存在且关联机构）→ 用之；②否则取账号默认绑定（`ContactAccountBinding.isDefault`），若该绑定指向 Registrar 且关联机构 → 自动激活（写回 `active_registrar_id`）并返回；③兜底首个有效 Registrar 身份并同样持久化。方法不再只读（仅派生时写一次）。
- **界面**：移除下拉与对话框；顶栏保留只读 `el-tag`（`data-testid="header-active-registrar"` 不变，`active` 为空时 tag 隐藏）；`activeRegistrarApi.select` 调用点全部删除，事件派发保留。
- **默认置顶**：`listMyContacts` 排序=默认身份在前，其余保持绑定顺序（稳定排序，不动默认标记语义）。
- **端点保留**：`PUT /api/v1/active-registrar` 不移除（外部脚本/测试兼容），前端不再调用。

## Risks / Trade-offs

- [默认绑定是 Contact 而非 Registrar] → 链②跳过该绑定，落到链③（首个 Registrar），语义正确。
- [默认绑定变更后激活不跟随] → 已持久化的激活身份优先；如需跟随默认，用户侧无入口（本需求目标即去除选择），可接受。
