# Design: remove-registrar-contact-identifier

详见 `docs/superpowers/specs/2026-10-01-remove-registrar-contact-identifier-design.md`（用户已确认）。本文档为 OpenSpec 侧摘要。

## Context

`mdr.registrar` 有非标准列 `identifier`（JOINED 继承下 Registrar 子类字段）。`RegistrarRequest.identifier` 为 @NotBlank 必填，管理端 Registrar 页有对应列与表单项；`ContactList.vue` 的标识符列/表单项为无契约对应的幽灵字段。约束与风险档案同 `remove-individual-nonstandard-fields`（QP 8624 仅涉 individual DML；并行会话 WIP 与 Registrar 链路零重叠）。

## Goals / Non-Goals

**Goals**：`mdr.registrar` 仅保留标准语义列（id + registration_authority_id，Join Table 继承下 Contact 主表承载三关联）；Registrar 管理 API/UI 无 identifier 契约；ContactList 幽灵字段清除。

**Non-Goals**：Contact 本体实体（本就无 identifier）；Item/Namespace 标识体系（ScopedIdentifier 等标准设施）。

## Decisions

1. **硬移除**（同 Individual 前例）：删列 + 清契约 + UI 同步，无兼容期。
2. **DTO 契约同步收缩**：`RegistrarRequest` 的 @NotBlank identifier 随字段一并移除——前后端同源发布，同变更组内一致。
3. **数据不搬迁**：identifier 无标准语义、无搜索/展示依赖（名称列走 individualName/organizationName），随列删除丢弃。
4. **迁移守卫沿用 V20260930001 模式**：sqlite 裸 DROP COLUMN；sqlserver COL_LENGTH('mdr.registrar','identifier') 守卫；dm ALL_TAB_COLUMNS 守卫 + EXECUTE IMMEDIATE。

## Risks / Trade-offs

- [DDL 在问题实例上的执行风险] → 同前例缓解（换表法兜底）；registrar 表无 individual 那样的 DELETE 病史
- [存量 `{username}-registrar` 数据丢失] → 无依赖，可接受，已记录

## Migration Plan

代码与迁移同变更组提交 → 后端重启 Flyway 自动应用（SQLite/SQL Server）→ scratch 副本库实测 → 真实库重启核查 → 冒烟 → 归档。

## Open Questions

无。
