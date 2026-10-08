# 提案：命名规范表单对齐 ISO/IEC 11179-3:2023

## Why

命名规范（Naming_Convention）的创建/编辑未按 ISO/IEC 11179-3:2023 §8.4.4 落地：表单强制至少一条指称并设有「指称」「定义」页签（标准上命名规范由注册标识识别、内容为五个规则属性）；规则属性只暴露 scopeRule/authorityRule 两个（标准 Table 17 共五个）；指称↔命名规范的方向应为指称侧选择命名规范（§8.6.5 naming_convention_conformance），命名规范侧不维护指称。

## What Changes

- 命名规范新建/编辑表单移除「指称」「定义」页签，移除「designations 至少一条」前端校验；提交 payload 不再携带 designations/definitions（后端 API 字段保留兼容，不改动）。
- 表单「属性」页签暴露全部五个规则属性：scopeRule（必填）、authorityRule、semanticRule、syntacticRule、lexicalRule（选填），字段提示忠实还原 Table 17 定义。
- 命名规范列表新增 semanticRule、syntacticRule、lexicalRule 三列（截断显示，空显 `-`）；首列在无指称时回退显示 scopeRule 截断文本。
- 指称侧选择命名规范的能力为既有实现（Designation ↔ Naming_Convention conformance 关联 + 编辑器多选），本次仅确认保持，不改动。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `naming-convention-ui`: 修改「scopeRule 必填与 designations 至少一条」需求（designations 不再必填、表单不再含指称/定义）；修改「列表列与表单字段」需求（列表与表单暴露五个规则属性）。

## Impact

- `metadata-registry-ui/src/views/naming-convention/NamingConventionList.vue`：表单/列表改造。
- `metadata-registry-ui/src/i18n/zh-CN.json`：新增五个规则属性相关 key，删除 `designationsRequired`。
- 新增 `metadata-registry-ui/src/views/naming-convention/NamingConventionList.spec.ts`。
- 无后端 / DB schema / OpenAPI 变更；指称侧命名规范选择链路（DesignationEditor/MultilingualTextEditor/ItemFormDialog/DesignationService/ItemAdministrationService）不改动。
