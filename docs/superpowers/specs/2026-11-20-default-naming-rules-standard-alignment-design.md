# 设计稿：默认命名规则与标准指称保真度对齐（2026-11-20）

## 1. 问题定义（数据实证）

系统对**每一条指称（Sign/Designation value）保存**强制套用一套硬编码"默认命名规则集"
（`NamingConventionEngine.buildDefaultRules()` = 长度规则 + 字符集规则 + 默认正则规则），
其中字符构成限制为白名单：`A-Za-z0-9_`、`连字符`、空格、`\u4e00-\u9fa5`（CJK 基本汉字）。

**后果**：WS 363/364-2023 标准指称中的合法字符被写入路径拒绝。实测当前注册库（SQLite 实例）
`designation` 表 **1,331 条去重指称**违反该白名单，涉及 55+ 种字符，全部为既有基准数据：

| 字符 | Unicode | 出现指称数 | 典型标准指称 |
|------|---------|-----------|--------------|
| （ ） | U+FF08/U+FF09 | 838 / 831 | 主任（副主任）医师姓名、舒张压（mmHg）值域 |
| 、 | U+3001 | 354 | 工作单位地址-市（地区、州）值域 |
| / | U+002F | 236 | 患者/法定代理人姓名 |
| ( ) 半角 | U+0028/U+0029 | 123 / 129 | 医嘱(处方)开立医师姓名 |
| Ⅰ Ⅱ Ⅲ Ⅳ Ⅴ | U+2160–U+2164 | 35 | Ⅰ助姓名、Ⅱ期 |
| ， ／ ＜ ＞ ％ ； | 全角标点 | 90+ | 骶耻外径＜18cm |
| µ μ β γ α δ Γ Ν | 希腊字母 | 36 | 尿铅检测值(ptmol/L)值域 |
| ㎡ ℃ ‰ ° ± ≥ ≤ — | 单位/符号 | 90+ | 右侧小腿CT 薄层扫描(3D VR 处理) |
| “ ” 《 》 〜 | 中文标点 | 30+ | 颞骨岩部后前斜位，X 线 |

2026-11-19 会话实测：为 94 个"姓名"类数据元补齐概念链时，5 条 DEC 草稿因该限制被拒
（`患者/法定代理人`、`医嘱(处方)开立医师`、`主任（副主任）医师`、`Ⅰ助`、`Ⅱ助`），
被迫改写指称、偏离标准原文。

**既有先例佐证**：`repair-administered-item-closure`（2026-10-09）修复 WS 基准导入数据时同样
无法走服务层写入（受管链补壳只能裸 SQL），命名限制是"标准内容无法经正规写路径注册"的
又一实例。

## 2. 标准依据

- ISO/IEC 11179-3:2023 对 Sign（指称值）的字符构成**无强制限制**——2026-09-23
  `add-registry-context-bootstrap` 变更中已核对标准原文并据此决策："默认正则命名规则允许空格
  （方案 1）：标准对符号字符构成无强制限制"。
- Naming_Convention 是**命名空间级治理工具**（`Namespace.mandatoryNamingConventionIndicator` +
  关联 NamingConvention），标准语义是"启用才约束"，而非全局无条件强制。
- 当前实现：默认规则集在 `ItemAdministrationService.validateNamingConventions`、
  `DesignationService`、 `DataGovernanceReadinessService`（治理就绪评分）三处全局套用；
  命名空间级强制校验（`NamespaceConstraintValidator.validateMandatoryNamingConvention`）
  只检查"是否关联了命名规范"，charset 机器规则（rulesJson `type=charset`）虽已实现但
  未接入任何执行路径。

## 3. 候选方案

### 方案 A：扩充默认白名单（additive）
把全角标点、罗马数字、希腊字母、单位符号等纳入 `CharacterSet` 与默认 pattern。
- 优点：改动最小、行为可预测。
- 缺点：治标不治本——白名单永远追不上标准原文的任意符号（现有数据已出现 55+ 种，
  未来导入 ICD、LOINC、药典名称只会更多）；每加一类字符都要发版。

### 方案 B：默认规则集去字符构成限制（推荐）
- 默认规则集仅保留**长度规则**（1–255：防空串、防溢出，与 DB 列宽一致）。
- 字符构成类规则（`CharsetNamingConventionRule`、默认 `RegexNamingConventionRule`）
  从默认集移除；二者保留为可选机器规则，经命名空间 Naming_Convention（rulesJson）
  显式启用后生效（引擎 `validate(sign, convention)` 路径已存在，仅需接线）。
- 未启用强制命名规范的命名空间：指称字符构成不做任何限制。
- 已启用强制命名规范的命名空间：走既有 `NamespaceConstraintValidator` 链路 + 约定规则执行。
- 治理就绪评分（`DataGovernanceReadinessService`）同步改为基于命名空间约定评估，
  不再以硬编码白名单打分。

### 方案 C：默认字符集可配置化
将默认字符集做成系统设置或命名空间设置项。
- 优点：最灵活。
- 缺点：配置面、UI、三方言持久化全链路成本最高；与"标准无强制"的定位不符，
  属过度设计。

**推荐方案 B**：与标准语义一致（约定才约束）、一次根治、为零配置负担；
charset 规则的治理能力不丢失（下沉为命名空间约定），需要严格字符治理的场景
（如自有数据标准命名）仍可用。

## 4. 影响面

- 写入路径：`ItemAdministrationService.applyDesignations`、`DesignationService`（创建/更新指称）。
- 只读路径：`DataGovernanceReadinessService` 就绪评分口径变化（评分项语义修正，非功能回退）。
- 既有数据零影响（校验仅作用于写入路径，不回溯）。
- MCP `draft-item`/`update-draft` 同步受益（走同一 Service 层）。
- 回滚：恢复默认规则集即可，无 schema 变更。

## 5. 验收标准

1. WS 363/364 全部 55+ 种特殊字符指称可经 MCP/UI 原样注册与编辑（以 §1 样例 5 条为必过用例）。
2. 未启用强制命名规范的命名空间：任意字符构成指称（除空/超 255 字符）写入成功。
3. 启用强制命名规范但未配规则的命名空间：写入仍 422（既有守卫不退化）。
4. charset 机器规则经 rulesJson 在命名空间约定启用后，对关联该约定的指称生效。
5. 全量回归零新增失败；`openspec validate --strict` 通过。

## 6. 开放问题（默认假设）

- 长度上限 255 是否保留：保留（与 designation 列宽一致，防溢出属工程底线，非字符构成限制）。
- `DataGovernanceReadinessService` 评分口径细节：实施任务中细化，原则=评估"命名空间约定合规"而非字符白名单。
