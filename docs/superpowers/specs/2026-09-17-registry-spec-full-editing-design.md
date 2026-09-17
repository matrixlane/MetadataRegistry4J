# 注册规范（RegistrySpecification）属性完整编辑 — 设计文档

日期：2026-09-17
状态：已确认（方案 A）
关联变更：`registry-specification-full-editing`

## 背景

- 注册规范是注册表的固有组成部分：ISO/IEC 11179-3:2023 §9.4.10 / 表 28 定义了
  `Registry_Specification` 类（"Describes the environment in which a registry operates"），
  有注册表即应有注册规范。初始化向导已不再设独立注册规范步骤（上一步已完成），
  由系统在 setup 时自动创建默认注册规范。
- 因此注册规范的唯一配置入口是初始化完成后的「系统设置 → 注册规范」页。
  当前该页是"列表 + 编辑对话框"，只开放了对象的部分属性，与其作为
  "注册表运行环境完整描述"的定位不符。

## 标准依据（经 Milvus 核对 ISO/IEC 11179-3:2023 原文）

表 28 — Registry_Specification 属性：name(1 Sign)、comment(0..1)、web_address(0..1)、
default_language(0..1)、alternative_language(0..*)、character_repertoire(1)、
reference_document_identifier_kind(0..* String)、representation_class_scheme_id(0..1)、
context(0..1)、standard(0..1)、degree_of_conformance(0..1，表 29 枚举：
conforming / strictly_conforming)、conformance_level(0..1)、conformance_label(0..1)、
included_features(0..1)、excluded_features(0..1)、default_time_zone(0..1，[-+]HHMM)。
另继承 Item 的指称（Designation 0..*）与定义（Definition 0..*）。

## 差距分析（实体/DTO 已全覆盖，缺口全部在前端 UI）

| 属性 | 实体 | Request DTO | 当前设置页 |
|---|---|---|---|
| name / comment / web_address / character_repertoire / standard / conformance_level / conformance_label / included_features / excluded_features / default_time_zone / default_language | ✓ | ✓ | ✓ 已开放 |
| degree_of_conformance | ✓ | ✓ | ✓ 下拉（两枚举值与表 29 一致） |
| alternative_language (0..*) | ✓ | ✓ | ✗ 缺失 |
| reference_document_identifier_kind (0..*) | ✓ | ✓ | ✗ 缺失 |
| representation_class_scheme_id (0..1) | ✓ | ✓ | ✗ 缺失 |
| context (0..1) | ✓ | ✓ | ✗ 缺失 |
| 指称 / 定义 | ✓ | ✓ | 仅硬编码中/英文两个输入框，其余语言与上下文无法维护 |

后端行为问题：`RegistrySpecification` 实体 `setDefaultTimeZone` 对非法格式抛
`IllegalArgumentException`，经 `applyRequest` 冒泡为 HTTP 500，应转为 400 业务校验错误。

## 方案对比

- **方案 A（推荐）保留"列表 + 对话框"，把对话框做完整**：补齐 4 个缺失属性
  （替代语言多选、参考文档标识种类标签输入、表示类方案标识、上下文选择器），
  指称/定义改用既有 `DesignationDefinitionSection`（DesignationEditor + DefinitionEditor，
  支持任意语言与上下文，与概念域等对象的编辑体验一致），时区增加前端格式校验。
  改动集中在单文件 + 一个后端校验修正，风险低。
- 方案 B 重构为单例设置表单页：产品语义更贴切，但后端支持多规范列表，
  单例化是独立产品决策，本次不扩大范围。
- 方案 C 仅补 4 个缺失字段：指称/定义仍硬编码中英，"属性开放编辑"不彻底。

**决策：采用方案 A。**（auto 模式按上下文直接推进；如需改选方案 B/C 请指出。）

## 详细设计

### 前端 `metadata-registry-ui/src/views/registry-spec/RegistrySpecificationSettings.vue`

1. 对话框表单新增：
   - 替代语言：`el-select multiple`，选项同默认语言（languageApi 已加载）。
   - 参考文档标识种类：`el-select multiple filterable allow-create default-first-option`，
     标签样式输入（0..* String，标准示例：URI、文档管理系统不透明标识等）。
   - 表示类方案标识：普通输入框。
   - 上下文：`contextApi.options()` 远程搜索选择器（clearable）。
2. 指称/定义区：删除"中文名称/英文名称/中文定义/英文定义"四个硬编码字段，
   替换为 `<DesignationDefinitionSection :show-pairings="false">`；
   `defaultLanguageId` 由 `useRegistryDefaultLanguage()` 提供。
   打开编辑时把响应中的 designations/definitions（TextLineResponse[]）映射为
   `TextLineForm[]`（保留 id/languageId/value/contexts）；提交时原样回传。
3. 时区输入增加 `[-+]HHMM` 格式前端校验（blur 触发），与实体校验一致。
4. `openDialog`/`resetForm`/`submit` 同步维护新字段（alternativeLanguageIds、
   referenceDocumentIdentifierKinds、representationClassSchemeId、contextId）。

### 后端 `metadata-registry-web`

- `RegistrySpecificationService.applyRequest`：捕获 `setDefaultTimeZone` 的
  `IllegalArgumentException`，转为 `BusinessException(ApiCodes.VALIDATION_FAILED, …)`（400）。
- 补充单测：非法时区 → 400 业务异常；合法时区（±HHMM）正常保存。
- 数据模型、DTO、其他校验不变。

### 明确不做（Non-goals）

- 不实施 openspec 中"元数据存在后关键字段锁定"要求：该要求列出的
  identifierPrefix/autoGenerateIdentifier/identifierPattern/versioningPolicy/mandatoryClassification
  字段已不在注册规范实体上（标识/版本策略已迁至命名空间级别），且代码中从未实施该锁定；
  默认语言变更不会破坏既有数据（仅作缺省回退）。规范文件中该过时要求随本变更一并修正。
- 不改动初始化向导与 `createDefault` 的默认值。
- 不做指称-定义配对编辑（`show-pairings=false`，配对非 Registry_Specification 属性）。

## 测试策略

- 后端单测：时区非法格式 → 400；合法格式保存回读。
- 前端：vitest 覆盖对话框 payload 组装（新字段 + 指称/定义映射）；`npm run build` 通过。
- 既有 e2e（init-wizard、menu）不受影响；不新增 e2e（对话框交互由单测覆盖）。
