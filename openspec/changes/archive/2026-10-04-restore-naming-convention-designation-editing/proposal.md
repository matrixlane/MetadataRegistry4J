# 提案：恢复命名规范编辑界面的指称与定义

## Why

`standardize-naming-convention-form`（2026-10-04 归档）基于「命名规范编辑界面不应有指称和定义」的理解移除了两个页签。该前提有误：ISO/IEC 11179-3:2023 §8.4.4.1 明确 Naming_Convention 是 Item 子类，实例可被 named、defined——指称与定义是命名规范作为注册项的既有能力，编辑界面应保留（0..*，非必填）。

## What Changes

- 命名规范新建/编辑表单恢复「指称」「定义」页签与对应 payload 字段（designations/definitions）。
- 保持 designations/definitions 非必填（标准基数 0..*），不恢复「designations 至少一条」强制校验。
- 五个规则属性（Table 17）与列表变更保持不变。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `naming-convention-ui`: 「列表列与表单字段」需求恢复指称/定义编辑区；「scopeRule 必填，designations 非必填」需求明确编辑区存在且条数不校验。

## Impact

- `metadata-registry-ui/src/views/naming-convention/NamingConventionList.vue`（+ spec.ts）。
- 无后端 / schema / OpenAPI 变更。
