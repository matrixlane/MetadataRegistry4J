## Why

个人版与团队版不是两种孤立的交付物，而是同一注册表网络的节点：员工在个人版实例上独立完成一段时期的数据治理后，需要把工作成果以类似 Git Pull Request 的方式提交到团队实例，经团队负责人评审后正式并入，且该操作可周期性反复执行。当前系统没有任何跨实例协作机制：实例间不知道彼此存在、没有变更集（changeset）的表示、没有"同一逻辑元数据项"在跨实例下的身份判定、也没有合并评审的载体。

幸运的是，代码库已具备关键积木：`version_history`（逐项版本快照，相当于提交日志）、多级审批流（`ApprovalRecord`/`ApprovalProcessInstance`，相当于评审流）、`MatchingEngineService` + `InteropMapping` 确认/拒绝工作流（相当于身份匹配置信工具）、`MetadataExportService` JSON 导出（相当于打包基础）。本变更把这些积木组装为联邦合并能力。

## What Changes

- **注册表节点（RegistryNode）**：实例可登记对端节点（名称、地址、注册机构标识 RAI、认证凭据），形成 PERSONAL→TEAM、TEAM→TEAM 的联邦拓扑；节点关系与部署版本无关。
- **合并请求（MergeRequest）**：源节点自上次同步水位线起计算增量变更集，打包为标准格式的**合并包（bundle）**，通过文件（离线/内网场景）或 HTTP 推送（联网场景）提交到目标节点；目标节点生成合并请求进入评审。
- **身份与链接模型**：以"来源 RAI + 来源标识符"作为元数据项的全局逻辑身份（实例内 UUID 仅限本地），新增 `item_link` 表记录跨实例条目链接（相当于 Git 的跟踪引用）。首次合并无链接时，由匹配引擎给出候选，评审人确认后建立链接。
- **冲突检测与三方合并**：已建立链接的条目，以"上次合并快照"为合并基（merge base）做三方比对；双方都改过的字段标记为冲突，由评审人逐项裁决（采用传入/保留现有）。
- **评审与并入**：合并请求复用现有多级审批流进行评审；通过后事务性并入目标库，写入 `change_type=MERGE` 的版本历史，更新双方同步水位线（目标直接更新、源节点凭合并回执更新），使下一次提交只含新增量。
- **范围约束**：合并请求必须指定目标命名空间；评审人必须具备该命名空间的相应权限（衔接 `namespace_permission`）。
- **可移植性**：全部新增表遵守 `database-portability` 规则（UUID 主键、不绑定 schema、双方言迁移脚本）。

**明确不做（本期）**：删除传播的合并（delete propagation）；字段级手工编辑合并（仅整项采用/保留）；实例克隆（clone）与从上游初始化个人版；云端多租户节点的联邦。

## Capabilities

### New Capabilities

- `registry-federation`：联邦节点登记与管理、跨实例身份/链接模型（RAI + 标识符、`item_link`）、同步水位线与合并回执、合并包格式与两种传输通道（文件包 / HTTP 推送）。
- `merge-request`：合并请求全生命周期（DRAFT → SUBMITTED → UNDER_REVIEW → MERGED/REJECTED）、增量计算、评审人逐项裁决（接受/拒绝/建立链接/冲突裁决）、事务性并入与版本历史写入。

### Modified Capabilities

（无：复用既有审批流、匹配引擎与版本历史，不修改其需求语义。）

## Impact

- **新增表**：`registry_node`、`item_link`、`sync_watermark`、`merge_request`、`merge_request_item`（双方言迁移脚本）。
- **新增服务**：联邦节点管理、合并包生成/解析、增量计算、合并执行器；新增控制器 `/api/v1/federation/**`。
- **复用**：`version_history`（增量来源与并入记录）、`ApprovalService`/`ApprovalProcessInstance`（评审流）、`MatchingEngineService`（首合身份匹配）、`MetadataExportService`（打包基础）、`namespace_permission`（评审范围控制）。
- **依赖**：本变更依赖 `edition-aware-init-and-db-portability` 先落地（版本概念与数据库可移植性规则是其前提），实施排序在其之后。
- **前端**：联邦节点管理页、合并请求提交向导、评审工作台（复用匹配确认 UI 模式）。
- **不影响**：既有单实例功能与 API 契约；不引入租户概念。
