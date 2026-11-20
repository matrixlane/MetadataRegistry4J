# 设计：默认命名规则与标准指称保真度对齐

## 背景

设计稿全文见 `docs/superpowers/specs/2026-11-20-default-naming-rules-standard-alignment-design.md`（Superpowers 阶段产物，含数据实证与候选方案对比）。本文件为 OpenSpec 设计节，记录选定方案与关键决策。

## 选定方案：方案 B —— 默认规则集去字符构成限制

默认规则集仅保留长度规则（1–255）；字符构成类规则（charset / 默认 regex）从默认集移除，下沉为命名空间 Naming_Convention 的可选机器规则。

### 决策记录

| 决策点 | 结论 | 理由 |
|--------|------|------|
| 默认规则集构成 | 仅 `LengthNamingConventionRule(1,255)` | 空串/列宽溢出是工程底线；字符构成标准无强制 |
| 字符构成限制的归宿 | 保留 `CharsetNamingConventionRule` 实现，经 `rulesJson type=charset` 按命名空间启用 | 治理能力不丢失；与 11179 命名空间约定语义一致 |
| 既有"默认正则规则" | 从默认集移除（其 DEFAULT_REGEX 编码同一白名单） | 与 charset 规则重复，同属字符构成限制 |
| 未启用约定的命名空间 | 不做字符构成校验 | 标准语义：约定才约束 |
| 启用强制约定的命名空间 | 既有守卫（必须关联约定）不退化；字符规则在所配 rulesJson 存在时执行 | 防治理能力回退 |
| 治理就绪评分 | 由硬编码白名单改为命名空间约定合规评估 | 评分口径与校验语义一致 |
| 长度上限 255 | 保留 | 与 designation 列宽一致 |

### 备选方案及不采纳理由

- **方案 A（扩充默认白名单）**：治标。白名单永远追不上标准原文任意符号（现有数据已 55+ 种字符，未来 ICD/LOINC/药典名称只会更多），每加一类字符都要发版。
- **方案 C（默认字符集可配置化）**：配置面 + UI + 三方言持久化全链路成本最高，与"标准无强制"定位不符，属过度设计；可作为后续演进方向。

## 影响面清单

- `NamingConventionEngine.buildDefaultRules()`：默认集 = 长度规则单条。
- `ItemAdministrationService.validateNamingConventions`：调用语义不变（仍走默认集），随默认集变化自然放宽。
- `DesignationService`：同上。
- `NamespaceConstraintValidator`：在既有强制约定守卫基础上，补"所配 rulesJson 机器规则对指称执行"的接线（复用引擎 `validate(sign, convention)`）。
- `DataGovernanceReadinessService`：评分项改为约定合规。
- 测试：`NamingConventionEngineTest`/相关校验测试需按新语义修订（原断言默认集三条规则的用例改为一条）。

## 风险与缓解

- 风险：依赖"默认字符限制"拦脏数据的部署失去该护栏。缓解：长度规则保留；需要字符治理的命名空间显式建 Naming_Convention 即恢复等价能力，且比原先更精确（按命名空间差异化）。
- 风险：就绪评分口径变化引发阈值告警误报。缓解：评分项语义在设计文档标注，实施任务含评分口径核对。

## 验收

见 `tasks.md` 与 spec 场景：§1 样例 5 条标准指称（`医嘱(处方)开立医师姓名`、`主任（副主任）医师姓名`、`患者/法定代理人姓名`、`Ⅰ助姓名`、`Ⅱ助姓名`）经 MCP `draft-item` 原样注册必过；长度守卫与强制约定守卫不退化。

## 范围外

- 命名规范表单 UI（`naming-convention-ui` 能力）不变；rulesJson 机器规则的表单化编辑仍为范围外（既有 backlog）。
- 定义（Definition）文本的校验语义不变。
