# 提案：默认命名规则与标准指称保真度对齐（default-naming-rules-standard-alignment）

## Why

系统对每一条指称（Designation value）的保存强制套用硬编码"默认命名规则集"（`NamingConventionEngine.buildDefaultRules()` = 长度规则 + 字符集白名单规则 + 默认正则规则），白名单仅允许 `A-Za-z0-9_`、连字符、空格与 CJK 基本汉字（`\u4e00-\u9fa5`）。

该限制与 ISO/IEC 11179-3:2023 的立场相悖：标准对 Sign（指称值）的字符构成无强制限制，Naming_Convention 是命名空间级治理工具（启用才约束），而非全局无条件强制（2026-09-23 `add-registry-context-bootstrap` 已核对标准原文并据此决策）。

**数据实证**（当前注册库 `designation` 表实测）：**1,331 条去重既有指称**违反该白名单，涉及 55+ 种字符，全部为 WS 363/364-2023 基准内容——全角括号（）838/831 条、顿号 354 条、斜杠 236 条、半角括号 123/129 条、罗马数字 Ⅰ–Ⅴ 共 35 条、全角标点 90+ 条、希腊字母 36 条、单位符号（㎡ ℃ ‰ ° ± ≥ ≤ —）90+ 条等。

**真实业务受损**（2026-11-19 会话）：为 94 个"姓名"类数据元（DE02.01.039.\*）经 MCP 补齐数据元概念链时，5 条草稿因字符限制被拒（`患者/法定代理人`、`医嘱(处方)开立医师`、`主任（副主任）医师`、`Ⅰ助`、`Ⅱ助`），被迫改写指称、偏离标准原文，注册内容失去保真度。

## What Changes

- **BREAKING（校验放宽）**：默认命名规则集仅保留长度规则（1–255 字符：防空串、防列宽溢出）；字符构成类规则（`CharsetNamingConventionRule` 与默认 `RegexNamingConventionRule`）从默认集移除。未启用强制命名规范的命名空间，指称字符构成不再做任何限制。
- 字符集规则保留为**可选机器规则**：经命名空间 Naming_Convention 的 `rulesJson`（`type=charset`）显式启用后，对关联该约定的指称执行校验（引擎 `validate(sign, convention)` 路径已存在，补齐接线）。
- 启用强制命名规范（`mandatoryNamingConventionIndicator=true`）的命名空间：既有 `NamespaceConstraintValidator` 守卫（必须关联约定、指称必须挂约定）不退化，并按所配机器规则执行字符校验。
- 治理就绪评分（`DataGovernanceReadinessService`）不再以硬编码白名单打分，改为评估命名空间命名约定合规性。
- MCP `draft-item` / `update-draft` 与 UI 表单走同一 Service 校验路径，同步受益，无需单独改造。

## Capabilities

### New Capabilities

- `designation-naming-validation`: 指称保存时的默认命名校验语义——默认仅长度约束；字符构成约束由命名空间 Naming_Convention 约定驱动、显式启用才生效；治理就绪评分口径与之一致。

### Modified Capabilities

（无。命名空间强制命名规范守卫属既有能力行为，本变更明确其不退化。）

## Impact

- **代码**：`NamingConventionEngine.buildDefaultRules()`、`ItemAdministrationService.validateNamingConventions`、`DesignationService`、`NamespaceConstraintValidator`（可选规则接线）、`DataGovernanceReadinessService`（评分口径）。
- **数据**：既有数据零影响（校验仅作用于写入路径，不回溯）；WS 363/364 全部特殊字符指称此后可经 MCP/UI 原样注册与编辑。
- **部署**：无 schema 变更、无迁移；回滚=恢复默认规则集。
- **测试**：需新增默认规则集语义测试（放行标准特殊字符、保留长度守卫）与命名空间约定驱动 charset 规则的端到端用例；全量回归零新增失败。
