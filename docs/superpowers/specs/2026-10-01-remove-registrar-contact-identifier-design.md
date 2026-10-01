# 设计：移除 Registrar/Contact 标识符（非标准字段清除）

日期：2026-10-01
状态：用户已确认
关联变更：OpenSpec `remove-registrar-contact-identifier`

## 1. 背景与依据

延续 2026-09-30 用户裁定原则：mdr schema 严格按 ISO/IEC 11179 标准设计，扩展归 ext。前序 `remove-individual-nonstandard-fields` 已清除 `mdr.individual` 的 identifier/avatar。本次清除 Registrar 侧同源问题：

- `Registrar extends Contact`（Contact 为 §6.3.5 标准类，属性仅 organization/individual/role 三个关联 + id），Registrar 子类新增的 `identifier`（`mdr.registrar.identifier` 列，JOINED 继承）非标准属性。
- UI 侧 `ContactList.vue` 的"标识符"列与必填表单项是**幽灵字段**——`ContactRequest`/`ContactResponse` 契约中无 identifier，列恒空、提交被忽略。

## 2. 目标 / 非目标

**目标**：`mdr.registrar` 删 identifier 列；Registrar 管理 API/DTO 无 identifier 契约；RegistrarList/ContactList 页面无标识符列与表单项。

**非目标**：`Contact` 本体（本就无 identifier，仅清 UI 幽灵字段）；`contact_account_binding` 等 ext 表不动；Item/Namespace 的标识体系（ScopedIdentifier 等标准设施）不动。

## 3. 方案（同前例：硬移除 + 三方言迁移）

- 迁移 `V20261001001__Remove_registrar_identifier.sql`（sqlite 裸 DROP COLUMN / sqlserver COL_LENGTH 守卫 / dm ALL_TAB_COLUMNS 守卫 + EXECUTE IMMEDIATE，模式同 V20260930001）。
- `Registrar` 实体删字段；`RegistrarService` 清 4 处（list 搜索谓词、applyRequest setIdentifier、toResponse、`createRegistrarForUser` 的 `{username}-registrar` 自动生成）。
- DTO：`RegistrarRequest` 删 identifier（连带 @NotBlank 契约移除）；`RegistrarResponse` 删 identifier。同名同源发布，不做兼容期。
- UI：RegistrarList.vue 删列/表单项/表单模型字段；ContactList.vue 删幽灵列/表单项。
- openapi 再生成（OpenApiExportTest，`-Dopenapi.export=true`）。

## 4. 风险与缓解

- 该 SQL Server 实例的 QP 8624 史仅涉及 mdr.individual 的 DML——本次是 registrar 表 DDL DROP COLUMN，风险低；触发则换表法。
- 存量 `mdr.registrar.identifier` 数据（`{username}-registrar` 格式）随列删除丢弃——该列无标准语义、无搜索/展示依赖（RegistrarList 名称列走 individualName/organizationName），可接受。
- 工作区并行会话 WIP 与本次文件零重叠（Registrar 链路 vs relocate-flyway/InitWizard 链路）。

## 5. 验证

TDD 红绿（RegistrarServiceTest 等）→ 全量 mvn test → vitest/build → Python scratch 实测迁移 → 重启后端真实库应用 → 冒烟（临时管理员账号：Registrars/Contacts 接口响应无 identifier、搜索 200、管理端列表渲染）→ openspec validate/archive → detect-changes → 按变更提交。
