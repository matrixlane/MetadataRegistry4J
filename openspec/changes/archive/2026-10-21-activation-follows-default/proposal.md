# Proposal: activation-follows-default

## Why

`default-registrar-auto-activation` 的解析链以"持久化激活身份仍有效则沿用"为第一优先，导致用户把另一注册人员身份设为默认后，活动注册机构仍停留在旧身份（实测：默认已改为"卫宁健康"，顶栏仍激活"默认注册机构"）。按需求本意，激活应**始终跟随默认注册人员身份**，`active_registrar_id` 仅作同步缓存。

## What Changes

- `resolveActiveRegistrar` 目标身份恒为：账号默认绑定对应的有效 Registrar 身份（默认绑定缺失或非 Registrar 时回退首个有效身份）；与持久化缓存不一致时写回同步。
- `PUT /api/v1/active-registrar` 端点语义对齐：显式激活同时把该身份设为默认绑定，保证后续解析一致。

## Capabilities

### Modified Capabilities

- `active-registrar-ownership`: 活动 Registrar 硬约束的激活语义改为"恒随默认注册人员身份"。
