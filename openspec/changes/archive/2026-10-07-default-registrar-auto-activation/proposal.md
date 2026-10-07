# Proposal: default-registrar-auto-activation

## Why

用户登录后不再需要"选择激活哪个注册机构"的步骤与界面：注册人员身份本身已有默认机制（首个注册人员身份自动设为默认，必有其一），系统应在登录后按**默认注册人员身份**自动激活当前注册机构。同时"我的"联系人身份列表应将默认身份始终置顶展示（当前默认身份可能排在非默认之后）。

## What Changes

- 后端激活解析改为默认驱动：`ActiveRegistrarService.resolveActiveRegistrar` 解析链调整为——账号持久化的活动身份（仍有效）→ 账号默认绑定对应的 Registrar 身份（自动激活并持久化）→ 首个有效 Registrar 身份（兜底）；删除"多身份必须显式选择"的交互前提，`PUT /api/v1/active-registrar` 端点保留（兼容/测试）。
- 后端 `ContactService.listMyContacts` 默认身份置顶返回（其余保持绑定顺序）。
- 前端移除顶栏 Registrar 身份下拉切换器与身份选择对话框，改为只读展示当前活动身份；加载后仍派发 `mdregistry:active-registrar-changed` 事件保持既有联动。
- 提交选择器/工作空间预检等消费 `activeRegistrarApi.current()` 的路径不变（返回值语义不变）。

## Capabilities

### Modified Capabilities

- `active-registrar-ownership`: 多身份不再需要显式选择；激活随默认注册人员身份自动完成；切换入口移除。
- `contact-identity-management`: 身份列表默认身份置顶。

## Impact

- 后端：`ActiveRegistrarService`（解析链 + 自动持久化）、`ContactService.listMyContacts`、相关测试。
- 前端：`AppLayout.vue`（去切换器/对话框，保留只读 tag）、`AppLayout.spec.ts`。
