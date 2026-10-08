# 提案：命名规范表单对齐 ISO/IEC 11179-3:2023

## Why

命名规范（Naming_Convention）的创建/编辑未按 ISO/IEC 11179-3:2023 §8.4.4 落地：表单强制「指称至少一条」（标准基数 0..*，非必填）；规则属性只暴露 scopeRule/authorityRule 两个（标准 Table 17 共五个）；列表未展示全部规则属性。

## What Changes

- 表单「属性」页签暴露全部五个规则属性：scopeRule（必填，Table 17 基数 1）、authorityRule、semanticRule、syntacticRule、lexicalRule（选填），字段提示忠实还原 Table 17 定义。
- 移除「designations 至少一条」前端强制校验；「指称」「定义」页签保留（Naming_Convention 为 Item 子类，§8.4.4.1，实例可被 named/defined，0..* 非必填）。
- 列表新增 semanticRule/syntacticRule/lexicalRule 三列（截断显示、空显 `-`）；首列无指称时回退 scopeRule 截断文本。
- 指称→命名规范方向为既有 naming_convention_conformance 链路（§8.6.5），不改动。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `naming-convention-ui`: 「scopeRule 必填与 designations 至少一条」需求改为 designations 非必填；「列表列与表单字段」需求补齐五个规则属性列与表单字段。

## Impact

- `metadata-registry-ui/src/views/naming-convention/NamingConventionList.vue`（+ 新增 spec.ts）。
- `metadata-registry-ui/src/types/common.ts`（NamingConvention DTO 三字段，与并行暂存的 TextLine 改动共存）。
- `metadata-registry-ui/src/i18n/zh-CN.json`（key 已存在，本次无需改动）。
- 无后端 / schema / OpenAPI 变更。
