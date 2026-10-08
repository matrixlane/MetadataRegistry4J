# 命名规范表单对齐 ISO/IEC 11179-3:2023 设计稿

日期：2026-10-04
变更：`standardize-naming-convention-form`
方案：默认方案（用户指令直接实现，不询问不确认）

## 背景与问题

命名规范（Naming_Convention，ISO/IEC 11179-3:2023 §8.4.4）当前的创建/编辑实现未按标准：

1. **创建不按标准**：`NamingConventionList.vue` 表单强制「指称至少一条」（`submitFn` 前端校验 `designations 不能为空`），且表单设置「指称」「定义」两个页签。按标准，Naming_Convention 是 Item 子类，经注册标识（命名空间 + 标识符 + 版本）识别，其实质内容是五个规则属性（Table 17）；designation/definition 不是命名规范编辑界面的内容。
2. **方向反了**：标准中 designation → naming convention 是 `naming_convention_conformance` 关联（§8.6.5，"records the naming convention to which a designation conforms"，0..* ↔ 0..*）；即指称侧可以选择命名规范，命名规范侧不需要维护指称。
3. **规则属性不全**：标准 Table 17 有五个规则属性（scope_rule 必填，authority/semantic/syntactic/lexical_rule 选填），后端实体/DTO/数据库列已齐备，但前端表单只有 scopeRule、authorityRule 两个。

## 标准依据（Milvus 检索 ISO/IEC 11179-3:2023 原文核实）

- §8.4.4.2："Naming_Convention is a class, each instance of which models a naming convention, which provides a set of rules for constructing the sign of a designation of a registry item."
- Table 17 — Attributes of Naming_Convention class：
  - `scope_rule`（1，必填）：rule specifying the range within which the naming convention is in effect。RULE：范围可宽可窄，由注册机构或其他机构决定；应说明该命名规范是描述性（descriptive）还是规定性（prescriptive）。
  - `authority_rule`（0..1）：rule identifying the authority that assigns names or enforces naming conventions, or both。
  - `semantic_rule`（0..1）：rule specifying the meanings of parts of a name and possibly separators that delimit them in a naming convention（whether or not names convey meaning and if so how）。
  - `syntactic_rule`（0..1）：rule specifying the arrangement of parts of a name and the separators that delimit them（relative / absolute arrangement）。
  - `lexical_rule`（0..1）：rule specifying the appearance of names（preferred/non-preferred terms、synonyms、abbreviations、part length、spelling、permissible character set、case sensitivity 等）。
- §8.6.5 `naming_convention_conformance`：绑定 Designation ↔ Naming_Convention，记录指称所遵循的命名规范。
- §8.4.4.3 Naming_Convention 的附加关联为 naming_convention_conformance 与 naming_convention_utilization（Namespace → Naming_Convention，§8.6.6）——两者都是从指称/命名空间指向命名规范，命名规范侧无指向指称的维护需求。

## 方案（默认方案）

### 1. 命名规范表单按标准重建（`NamingConventionList.vue`）

- 移除「指称」「定义」两个页签及其 `MultilingualTextEditor` 引入。
- 移除 `submitFn` 中 `designations 不能为空` 的前端校验。
- 「属性」页签暴露全部五个规则属性：`scopeRule`（必填，保留现有校验）、`authorityRule`、`semanticRule`、`syntacticRule`、`lexicalRule`（选填）。
- 字段 tooltip/placeholder 忠实还原 Table 17 定义（中文翻译，不概括扩写），i18n 新增 key。
- 「注册标识」页签保留（Item 子类经注册识别，不变）。
- 提交 payload 仅含五个规则属性 + registration；不再提交 designations/definitions（后端 API 保留这两个字段以兼容存量数据，不改动）。
- 列表首列在无指称时回退显示 scopeRule 截断文本（新建命名规范无指称，避免全部显示「-」）。

### 2. 列表列补齐

列表在现有 scopeRule、authorityRule 列基础上增加 semanticRule、syntacticRule、lexicalRule 三列（同为截断代码文本，空显 `-`）。

### 3. 指称侧选择命名规范（已具备，保持）

- 后端：`Designation.namingConventions`（`designation_naming_convention` 关联表）+ `ItemAdministrationService.applyDesignations` / `DesignationService.applyNamingConventions` 已持久化 `namingConventionIds`。
- 前端：`MultilingualTextEditor` / `DesignationEditor` 每行已有命名规范多选；`ItemFormDialog` 按命名空间装载候选。本次不改。

### 4. 影响面

- `metadata-registry-ui/src/views/naming-convention/NamingConventionList.vue`
- `metadata-registry-ui/src/i18n/zh-CN.json`（新增 key；删除不再使用的 `designationsRequired`）
- `openspec/specs/naming-convention-ui`（delta：修改 designations 必填需求、表单/列需求）
- 无后端、无 schema、无 OpenAPI 变更。

### 5. 验证

- 新增 `NamingConventionList.spec.ts`：表单五字段渲染、无指称/定义页签、提交 payload 不含 designations/definitions、scopeRule 必填校验保留。
- `npm run build`（vue-tsc）+ vitest 全量。
- `openspec validate --all`。
- `node .gitnexus/run.cjs detect-changes --scope all --repo .` 无 partial/truncated。
