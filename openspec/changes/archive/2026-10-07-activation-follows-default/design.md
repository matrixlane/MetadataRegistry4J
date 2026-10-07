# Design: activation-follows-default

## Context

前序变更解析链：持久化有效 → 沿用；否则默认绑定派生。用户改默认后激活不跟随，与"登录后按默认注册人员身份激活"的需求冲突。

## Decisions

- 目标身份 = 默认绑定 Registrar（回退首个），**不**再优先持久化值；持久化仅缓存，不一致即写回（每账号每次默认变更至多一次写）。
- `selectActiveRegistrar` 保留端点并同步 `markDefault`，使"显式激活"与"默认"语义合一，避免端点失效误导。

## Risks / Trade-offs

- [默认绑定变更频繁导致多次写回] → 变更低频（身份管理操作），可接受。
