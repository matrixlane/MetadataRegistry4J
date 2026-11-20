# Tasks: default-naming-rules-standard-alignment

## 1. 引擎默认规则集

- [x] 1.1 `NamingConventionEngine.buildDefaultRules()` 改为仅含 `LengthNamingConventionRule(1,255)`；`CharsetNamingConventionRule` 与默认 `RegexNamingConventionRule` 从默认集移除
- [x] 1.2 修订/新增 `NamingConventionEngineTest`：默认集单条长度规则的断言（原"默认集三条规则"用例按新语义改写）；`validate(sign, convention)` 约定路径用例保持绿
- [x] 1.3 保留 `CharsetNamingConventionRule` 实现与 `CharacterSet` 枚举（供 rulesJson `type=charset` 使用），补"charset 规则仍可由约定显式启用"的单元用例

## 2. 命名空间约定接线

- [x] 2.1 `NamespaceConstraintValidator` 在强制命名规范守卫通过后，对指称执行所关联 Naming_Convention 的机器规则（rulesJson 含 charset 时），复用引擎 `validate(sign, convention)`；失败报校验错误（422）
- [x] 2.2 新增用例：启用强制约定 + 配置 charset 规则 → 违规字符被拒；未配置机器规则 → 放行（守卫仍生效）
- [x] 2.3 回归用例：强制命名空间缺约定 / 指称未挂约定 → 仍 422（守卫不退化）

## 3. 就绪评分口径

- [x] 3.1 `DataGovernanceReadinessService` 指称合规评分项改为命名空间约定合规评估，移除硬编码白名单判据
- [x] 3.2 新增用例：未启用约定空间的特殊字符指称不扣分；启用 charset 约定的违规指称在评分中体现

## 4. 端到端验收（TDD 红绿）

- [x] 4.1 服务端用例：以 §样例 5 条标准指称（`医嘱(处方)开立医师姓名`、`主任（副主任）医师姓名`、`患者/法定代理人姓名`、`Ⅰ助姓名`、`Ⅱ助姓名`）走 `ItemAdministrationService.applyDesignations` 全部通过
- [x] 4.2 MCP 通道验收：部署后以真实 `draft-item`/REST 更新提交含全角括号/斜杠/罗马数字指称（Ⅰ助姓名等 8 条标准命名恢复）作为端到端证据（替代自动化用例）
- [x] 4.3 空指称与 256+ 字符指称仍被拒（长度守卫）

## 5. 验证与收尾

- [x] 5.1 后端全量测试：失败集 = 干净 HEAD 基线（零新增）
- [x] 5.2 `openspec validate --strict` 通过
- [x] 5.3 GitNexus detect-changes 无 partial/truncated；如需编辑前影响分析已留痕
- [x] 5.4 设计稿/文档同步：本变更归档前按 OpenSpec archive 流程合并主 spec；无需改 AGENTS.md
