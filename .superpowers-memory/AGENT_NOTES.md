# Agent Notes

Use this file for durable agent-side reminders about execution quality, repo-specific handling, or recurring operational pitfalls.

## Entry Template

```md
### Agent Note: <short-title>
- id: agent-YYYY-MM-DD-<slug>
- type: agent_note
- status: active
- confidence: verified
- last_updated: YYYY-MM-DD
- source:
- owner:
- review_after:

Reminder:

Why it matters:

How to apply it:
```

## Notes

- Keep this separate from project facts and user preferences.
- Use this file for repeatable execution reminders, not transient scratch notes.
- If a note is no longer relevant, mark it `superseded` instead of silently removing the history.

### Agent Note: Coding Agent 登录系统必须自建 Admin 账户，禁止应急码登录
- id: agent-2026-09-21-no-emergency-login
- type: agent_note
- status: active
- confidence: verified
- last_updated: 2026-09-21
- source: 2026-09-21 应急码登录事故（session-journal/2026-09-21-dimensionality-management.md 事故记录）；用户当次指令
- owner: matri
- review_after: 2027-03-21

Reminder:

Coding Agent 需要登录系统（API 冒烟、E2E、手工验证）时，**必须自己创建一个专用 Admin 测试账户**，严禁使用超级管理员应急码（`emergency-code.txt`）登录。

Why it matters:

应急码登录的副作用写死在 `AuthService.login` 应急登录分支：会把真实库 `force_password_reset` 置 1（导致用户下一次正常登录被强制改密弹窗阻塞），且旧应急码一次性作废重生。2026-09-21 事故：验证维度接口可达时用应急码登录了 8081 真实库，未恢复标志，用户随后登录被强制改密。

How to apply it:

- 冒烟/验证一律优先用独立临时实例或副本库（scratch 模式）。
- 必须登录真实库所在实例时：先用管理员可用的途径创建专用测试账户（如已有管理员会话时调用户管理 API；或请用户提供凭据），用完禁用/删除。
- 应急码仅在用户本人遗忘管理员密码、且用户明确知情同意时才能使用；用后立即恢复 `force_password_reset` 原值并告知用户。
