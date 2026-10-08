# 设计：恢复命名规范编辑界面的指称与定义

## Context

上一变更已归档，主 spec `naming-convention-ui` 现规定表单 MUST NOT 包含指称/定义编辑区。用户澄清：Naming_Convention 是 Item 子类（§8.4.4.1），可被 named/defined，该前提错误，需回退。

## Goals / Non-Goals

**Goals:** 恢复「指称」「定义」页签、编辑回填与提交 payload；designations/definitions 非必填。

**Non-Goals:** 不恢复旧的「designations 至少一条」强制校验（标准 0..*，非标准约束）；不动五个规则属性、列表列、后端。

## Decisions

1. **页签恢复为原状**（MultilingualTextEditor + 注册表默认语言预选），与数据元等注册项弹窗模式一致。
2. **不恢复必填校验** — 设计ations/definitions 在标准中为 0..*；「创建按标准」的诉求由「注册标识 + 五规则 + 非必填指称/定义」完整满足。
3. **spec 走新变更回退**，不篡改已归档变更（历史即事实）。

## Risks / Trade-offs

- [存量命名规范此前可通过 UI 编辑指称，上一变更后暂时不能] → 本次恢复即修复，无迁移。

## Migration Plan

纯前端变更。回滚 = 还原提交。

## Open Questions

无。
