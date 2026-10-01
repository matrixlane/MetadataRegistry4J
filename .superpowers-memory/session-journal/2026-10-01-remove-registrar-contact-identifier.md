# 2026-10-01 移除 Registrar/Contact 标识符（非标准字段清除）

## 起因

延续 2026-09-30 用户裁定原则（mdr schema 严格按 ISO/IEC 11179 标准设计）。`Registrar extends Contact`（§6.3.5，属性仅 organization/individual/role 关联）带非标准 `identifier` 字段（mdr.registrar.identifier，JOINED 继承）。用户指令："联系人也要去掉标识符"。

## 实施要点

- `Registrar` 实体删字段；`RegistrarService` 清 4 处（含 `createRegistrarForUser` 的 `{username}-registrar` 生成）；`RegistrarRequest` 删 @NotBlank identifier、`RegistrarResponse` 删 identifier；三方言迁移 `V20261001001`（模式同 V20260930001）。
- **搜索谓词重写（偏离原计划，必要修正）**：原以为 list 搜索是"identifier + 其他条件"，实际 identifier 是唯一条件——直接删掉会让 DataTablePage 通用搜索框静默失效。按 §6.3.5 三要素重写为：个人姓名 + 角色名（LEFT JOIN，内连接会把缺要素的 Registrar 滤掉——集成测试抓出并已修）+ 组织名（@ElementCollection 走 exists 子查询防行数放大）。新增 `RegistrarListSearchIntegrationTest` 5 用例。
- `ContactList.vue` 的标识符列/必填表单项为**幽灵字段**（ContactRequest/Response 契约中不存在 identifier，列恒空、提交被忽略），纯 UI 删除。
- 4 个集成测试的 `registrar.setIdentifier(...)` 造数行同步删除；`RegistrarTest.shouldSetIdentifier` 改为 `shouldWireContactThreeElements`。

## 验证证据

- TDD 红绿：RegistrarServiceTest 18 + RegistrarListSearchIntegrationTest 5 + RegistrarTest 2 + OpenApiExportTest 1，全绿
- 后端全量 1373 tests：11 个失败与 CURRENT_STATE 记录的既有集合完全一致（ScopedIdentifier×1/SegmentTemplate×1/Workspace×9），零新增
- 前端 build（含 vue-tsc）通过；vitest 唯一失败为既有 MyProfile.spec（与本次文件零交集）
- SQLite 实测：迁移前 registrar 含 identifier 列 → 迁移后仅剩 id + registration_authority_id
- SQL Server 真实库：重启应用 V20261001001（6ms），registrar 列核查通过
- 冒烟（agent_smoke02，用后已删）：GET /registrars 响应字段无 identifier、关键词搜索 200、GET /contacts 无 identifier
- openspec validate --strict 通过；detect-changes 无 partial/truncated（risk medium，3 条执行流均被测试覆盖）

## Backlog 更新

- ~~mdr.registrar.identifier 移除~~（本次完成）
- 剩余：mdr 表标准符合性全面审计（Organization/Contact/Registration_Authority 扩展列核对——注意并行会话已自行完成 Organization identifier 移除 a34065d）、dm 迁移待实测、DataTablePage 搜索框 placeholder「搜索标识符或名称」对 Registrar/Contact 页已不准确（全站通用组件，属另一范围）、openapi.yaml 整体滞后于 openapi.json 建议单独全量再生成

## 遗留

- 达梦 V20261001001 待实测（沿用惯例）
