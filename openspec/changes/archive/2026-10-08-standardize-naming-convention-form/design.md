# 设计：命名规范表单对齐 ISO/IEC 11179-3:2023

## Context

- 后端已按标准实现：`NamingConvention` 实体含 scopeRule/authorityRule/semanticRule/syntacticRule/lexicalRule 五个 `Text` 属性（三方言列 V20260731001），DTO/Service 全量支持；`NamingConventionRequest.designations` 无 `@NotEmpty`。
- 缺口在前端：`NamingConventionList.vue` 表单仅两个规则字段、designations 前端强制 ≥1、列表缺三列；`types/common.ts` DTO 缺三字段。
- 标准依据（Milvus 检索 ISO_IEC 11179-3:2023 原文）：§8.4.4.2（规则集构造指称符号）；Table 17（scope_rule 基数 1，其余 0..1）；§8.4.4.1（Item 子类，可被 named/defined——故指称/定义页签保留）；§8.6.5（designation → naming convention 方向）。

## Goals / Non-Goals

**Goals:** 五规则属性全暴露（表单+列表）；designations/definitions 非必填；首列无指称回退 scopeRule。

**Non-Goals:** 不改后端/schema；不实现 rulesJson 机器规则表单编辑；不动指称侧选择链路。

## Decisions

1. **保留「指称」「定义」页签**（Item 子类固有能力，0..*）——曾误判移除，用户澄清后恢复；**不恢复** designations≥1 强制校验（非标准约束）。
2. **前端校验仅 scopeRule 必填** — 与后端 `@NotBlank` 及 Table 17 基数对齐。
3. **提示文案逐条取自 Table 17 定义中译**（项目「标准依据表述规则」）。
4. **单变更 capturing 终态**——历史上两次变更（standardize + restore）均未入库即被工作树清理，本次以终态直接建单变更。

## Risks / Trade-offs

- [无指称的存量/新建命名规范列表首列显示回退文本] → `label || scopeRule 截断 || '-'`。

## Migration Plan

纯前端。回滚 = 还原提交。

## Open Questions

无。
