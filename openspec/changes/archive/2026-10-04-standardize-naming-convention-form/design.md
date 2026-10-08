# 设计：命名规范表单对齐 ISO/IEC 11179-3:2023

## Context

- 后端已按标准实现：`NamingConvention` 实体含 scopeRule/authorityRule/semanticRule/syntacticRule/lexicalRule 五个 `Text` 属性（列已存在于三方言库，V20260731001），DTO/Service 全量支持；`NamingConventionRequest.designations` 无 `@NotEmpty`，创建 API 本就允许无指称（控制器测试即用 null designations）。
- 指称→命名规范链路为既有实现：`Designation.namingConventions`（§8.6.5 conformance）、`ItemAdministrationService.applyDesignations` 持久化 `namingConventionIds`、前端 `MultilingualTextEditor`/`DesignationEditor` 每行多选、`ItemFormDialog` 按命名空间装载候选。
- 缺口全在前端：`NamingConventionList.vue` 表单仅有 scopeRule/authorityRule 两字段，含「指称」「定义」页签并强制 designations ≥ 1。

## Goals / Non-Goals

**Goals:**

- 命名规范新建/编辑表单按 Table 17 暴露五个规则属性（scopeRule 必填，其余选填），提示文案忠实还原标准定义。
- 表单移除「指称」「定义」页签与 designations 必填校验；payload 不再携带 designations/definitions。
- 列表补齐五个规则属性列，首列无指称时回退 scopeRule 文本。

**Non-Goals:**

- 不改后端 API/实体/schema（designations/definitions 字段保留兼容存量数据）。
- 不改指称侧命名规范选择链路（已符合标准）。
- 不实现机器可执行规则（rulesJson）的表单编辑（既有范围之外）。

## Decisions

1. **保留「注册标识」页签，仅移除「指称」「定义」** — Naming_Convention 是 Item 子类（§8.4.4.1），经命名空间+标识符+版本识别；注册标识是其标准识别途径。替代方案（一并移除注册标识）违背标准，否决。
2. **前端校验只保留 scopeRule 必填** — Table 17 中 scope_rule 基数为 1，其余四个 0..1；与后端 `@NotBlank(scopeRule)` 对齐。
3. **提示文案逐条取自 Table 17 定义中文翻译** — 遵循项目「标准依据表述规则」（忠实还原原文、不概括、不引条款号）。
4. **列表加三列而非合并展示** — 与现有 scopeRule/authorityRule 列风格一致（截断 code 文本），信息无损；替代方案（tooltip 合并）牺牲可读性，否决。
5. **后端不动** — 需求纯 UI 层；API 保留 designations/definitions 兼容旧客户端与存量数据。

## Risks / Trade-offs

- [存量命名规范依赖指称显示名称，移除后列表首列可能大量显示回退文本] → 首列 `label || scopeRule 截断 || '-'` 回退，NamespaceList 下拉已有同款回退（`n.label || n.scopeRule`），行为一致。
- [旧 e2e/文档断言 designations 必填] → 检索确认 e2e 未覆盖命名规范表单；主 spec 以 delta 修正。
- [en-US.json 无 namingConvention 文案区] → 既有模式即 zh-CN 承载该区块（en-US 走 fallbackLocale），新增 key 仅入 zh-CN.json，与现状一致。

## Migration Plan

纯前端变更，随构建发布；无数据迁移。回滚 = 还原提交。

## Open Questions

无。
