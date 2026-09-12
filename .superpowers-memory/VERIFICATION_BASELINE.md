# Verification Baseline

Use this file for the verification commands and evidence standards that the team considers trustworthy for this project.

## Entry Template

```md
### Verification Rule: <short-title>
- id: verification-YYYY-MM-DD-<slug>
- type: verification_rule
- status: active
- confidence: verified
- last_updated: YYYY-MM-DD
- source:
- owner:
- review_after:

Command or method:

What it validates:

What it does not validate:

Evidence expected:
```

## Notes

- Prefer commands that are reproducible and already used successfully by the team.
- Record known blind spots so future sessions do not overclaim confidence.
- Durable entries should always include `id`, `status`, `confidence`, `source`, `last_updated`, and `review_after`.
- If the rule is `verified`, the `source` should point to a successful command, log, test, or documented evidence.

### Verification Rule: 目标包回归 + 真实握手/冒烟双层验证
- id: verification-2026-09-13-targeted-plus-smoke
- type: verification_rule
- status: active
- confidence: verified
- last_updated: 2026-09-13
- source: 本会话六轮功能+四轮换 bug 的验证记录（session-journal/2026-09-13-0635）
- owner: matrixlane
- review_after: 2026-12-13

Command or method:

- 后端：`./mvnw -pl metadata-registry-web -am test -Dtest='<目标包glob>' -DfailIfNoTests=false`（注意 surefire 3.2.2 下 `inference.**.*` 不匹配 provider 子包，须用类名 glob 如 `Acp*,Inference*,Assistant*` 防假绿）
- 前端：`cd metadata-registry-ui && npx vitest run && npm run build`
- 传输/协议层：scratch 客户端真实握手（initialize/PINEAPPLE），不用假 server 冒充真实验证
- 前端交互：jsdom 之外必须 Playwright 真实浏览器验证 EP 原生行为
- 无口令 API 验证：用 `~/.metadata-registry/application-local.yml` 的 jwt.secret + admin account id 铸 HS512 调试令牌（仅限本机调试）

What it validates: 单测逻辑正确性 + 构建完整性 + 协议/环境真实链路。

What it does not validate: 全量回归中的既有基线失败（CaseInsensitiveEnumPersistenceTest×2、ItemSortMvcIntegrationTest×2、ResetDatabaseHelperTest、NamespaceGateIntegrationTest×4——均为环境问题，stash 对照确认非引入）；UI 全流程人工验收。

Evidence expected: 目标包 0 失败数字、真实握手输出摘要、Playwright 场景通过记录；收尾 detect_changes 无 partial/truncated。
