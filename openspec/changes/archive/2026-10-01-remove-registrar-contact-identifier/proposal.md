# Proposal: remove-registrar-contact-identifier

## Why

延续项目原则（2026-09-30 用户裁定）：mdr schema 的表必须严格按照 ISO/IEC 11179 标准中定义过的内容设计；标准定义之外的部分统一使用 ext schema（内部标识 id 列与审计字段除外）。前序变更 `remove-individual-nonstandard-fields`（2026-10-01 归档）已清除 `mdr.individual` 的非标准列。本次清除 Registrar/Contact 侧的同源问题：

- `Registrar extends Contact`（ISO/IEC 11179-3:2023 §6.3.5，属性仅 organization/individual/role 三个关联），Registrar 子类新增的 `identifier` 为非标准属性，对应 `mdr.registrar.identifier` 列（JOINED 继承）。
- 前端 `ContactList.vue` 的"标识符"列与必填表单项为幽灵字段——`ContactRequest`/`ContactResponse` 契约中不存在 identifier，列恒空、提交被后端忽略。

## What Changes

- **删除 `mdr.registrar.identifier`**：`Registrar` 实体删字段；`RegistrarService` 清 4 处（list 关键词搜索谓词、applyRequest 的 setIdentifier、toResponse 透传、`createRegistrarForUser` 的 `{username}-registrar` 自动生成）；`RegistrarRequest`（含 @NotBlank 契约）/`RegistrarResponse` 删 identifier 字段。
- **三方言迁移 `V20261001001__Remove_registrar_identifier.sql`**：drop column，方言守卫与 V20260930001 同模式。
- **前端**：`RegistrarList.vue` 删标识符列/必填表单项/表单模型字段；`ContactList.vue` 删幽灵标识符列/表单项。
- **openapi.json/yaml 再生成**。

## Capabilities

### New Capabilities

- `registrar-standard-compliance`: mdr.registrar 列集与标准（Contact §6.3.5 语义）一致的约束，以及 Registrar 管理 API/UI 无 identifier 契约的落地。

### Modified Capabilities

（无既有能力语义变更；为回滚性变更恢复标准原状。）

## Impact

- `metadata-registry-common-facilities`：`Registrar` 删 identifier 字段。
- `metadata-registry-web`：三份新迁移；`RegistrarService`、`RegistrarRequest/Response`；相关测试断言清理；openapi 再生成。
- `metadata-registry-ui`：`RegistrarList.vue`、`ContactList.vue`。
- 向后兼容性影响：Registrar 管理 API 删必填字段（前后端同源发布，无外部消费者）；存量 `mdr.registrar.identifier` 数据（`{username}-registrar` 格式，无搜索/展示依赖）随列删除丢弃。
- 多方言要求：三方言迁移语义一致、幂等守卫；达梦沿用"待实测"惯例。

## 标准依据

ISO/IEC 11179-3:2023 §6.3.5 Contact class：属性为 organization（0..1）、individual（0..1）、role（0..1）三个关联，无 identifier 属性。`Registrar` 为应用侧子类（extends Contact 附加 RegistrationAuthority 关联），其 identifier 列不在标准定义内，按项目口径从 mdr schema 移除。
