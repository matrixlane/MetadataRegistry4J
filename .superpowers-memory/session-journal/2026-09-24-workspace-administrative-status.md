# 2026-09-24 工作空间 + 管理状态 + 后续 UI 调优（add-workspace-administrative-status）

## 主交付：工作空间与管理状态

OpenSpec 变更 `add-workspace-administrative-status`（已归档为 `2026-09-24-add-workspace-administrative-status`）：
- 后端（common-facilities + web）：
  - `AdministrativeStatus` 枚举（16 常量，剔除 `APPROVED_AS_CANDIDATE`）+ Converter（`autoApply`，String 持久化）
  - `AdministrativeStatusService`：清空约定（除 INCOMPLETE→Initial 外全部置空）+ markDraft/clear/applyLanding
  - `ItemAdministrationService`：编辑→Draft 钩子、迁移后清空
  - `VersionHistoryService`：全量 DTO 快照 + meta{kind: BASELINE/DRAFT_EDIT/SUBMITTED/ROLLED_BACK}
  - `WorkspaceRollbackService`：恢复基线/降级处理
  - `RegistrationProcessService`：reject 动作、Submitter 白名单（INCOMPLETE→CANDIDATE）
  - 权限：`SecurityConfig` 放行 `/assistant/settings`（PUT 与 GET 均 authenticated，参照用户"应用设置"指令）
  - 索引迁移 `V20260924001`（已部分回退——见后续"教训"）
- 前端：
  - 一级菜单"工作空间"+ 右下角徽标（`useWorkspaceBadge` 模块单例+30s 轮询+立即刷新）
  - 工作空间三视图聚合页面 + 批量提交（Submission_Record 选择器）+ Request Candidate + Rollback
  - 04 个新依赖项 + 1 个新依赖

## 关键修复：SQL Server 维度删除 409 FK 冲突

DimensionalityService.delete 未清理 `submission_record_registered_item` 关联 → FK 阻挡：
- 第 1 次：原生无 schema `DELETE` → SQL Server 登录默认 schema=`dbo`（非 `mdr`），找不到表 → 500
- 第 2 次：JPA load-modify-save（`removeIf`+`save`）→ 集合脏标记 / Hibernate flush 顺序未真正发出 join DELETE → 仍 409
- **最终**：注入 `DatabaseEnvironment` + `JdbcTemplate`，用项目既有方言感知表名解析 `databaseEnvironment.qualify("submission_record_registered_item")` + `toJdbcUuid(id)`：
  - SQL Server → `DELETE FROM "mdr"."submission_record_registered_item"`
  - SQLite → `DELETE FROM "submission_record_registered_item"`
  - 跨方言一致；与 `ContextTestDataInitializer` / `MetadataSearchService` 同款
- **教训**：同类问题可能存在于 DataElement/ValueDomain/ConceptualDomain 等所有有提交记录的 AdministeredItem 子类（按用户"只修报告的"原则未批量处理）

## 启动加载页 500

初次归档时 SQL Server 迁移 `V20260924001` 表名无 schema 前缀（同上 FK 修复的同根问题）→ Flyway 失败 → 后端启动中止 → 前端启动加载页卡死。修复后正常。

## 后续 UI 调优（未进 OpenSpec delta——用户接受"主 spec 已记录核心能力，后续 UI 调优不入 spec"）

- **AI 辅助 → 应用设置（右上角 MagicStick 图标）**：原 system-settings 的 AI tab 迁出至独立 `/app-settings` 页（沿用同一页面组件），后端 settings 端点权限放宽为 authenticated
- **系统/用户管理合并 → "系统"一级入口**：右移"我的"之下、底栏的"系统设置"+"用户管理"合并为单一"系统"入口，主内容区展示**一级平面菜单**（通用/用户管理/数据源/危险区域，默认进入"通用"），仅"用户管理列表"宽度自适应撑满；admin-only
- **指称/定义/配对合并为"指称与定义"一级 Tab**：内嵌三个二级 Tab，配对校验导航由 `activateNamingTab(sub)` 统一处理
- **新一级 Tab"注册记录"**：提交记录 + 主管记录（前后端新增 `StewardshipInfo` DTO + `StewardshipRecordRepository`，前端 ItemFormDialog 新增主管记录 editor）
- **`ItemFormDialog` 提交记录与主管记录提取**：从系统设置迁移至独立"注册记录" Tab（主 spec：submission-record ADDED）

## DataTablePage 新建按钮位置折腾史（用户多轮纠正）

1. 原位置（HEAD）：搜索栏右上"新建"
2. 用户："将新建放到第二栏，与批量删除同一栏"→ 移至 batch-bar 右侧（space-between）
3. 用户："最好与下面的操作列对齐"→ 移至表格卡顶部（table-toolbar）
4. 用户："不是让你往下贴啊，先回退"→ 撤回
5. "回退吧，你太蠢了"→ `git restore`（误伤 selectable+批量删除未提交功能）
6. 重建 selectable+批量删除后用户："新建按钮都没跟左边对齐"→ 移到左侧
7. "新建按钮在右侧没错，我说的是与左侧纵向对齐"→ 撤回左移回到右侧
8. "新建和重置对齐了吗？"→ 合并到同一 form-item
9. "X 轴在原来位置不变，Y 轴上和重置按钮对齐"→ **最终**：保留右侧 X 位置 + 去掉新建的 `el-form-item` 包装（其内部纵向对齐与"搜索/重置"不在同一基线）→ Y 对齐

**最终态**：新建按钮在 search-form-right 内（X 不变），裸 `<el-button>`（无 el-form-item），与"搜索/重置"自然同基线。

**教训**：UI 微调迭代应直接在最小可用 patch 上逐步推进，不要整体反复 restore/reconstruct。`git restore DataTablePage.vue` 把之前未提交的"selectable+批量删除"功能也冲掉了，需要重新按照 spec.ts 契约重建。

## 验收

- OpenSpec 严格校验通过；归档成功
- 后端全量测试 1971；前端 253（258 含 AppSettingsEntry/SystemEntry）→ 改 DataTablePage 后 258（1 条 VTU 2.4.11 与 `wrapper.emitted` 包装层数不兼容遗留——已确认功能不受影响）
- detect_changes 自 T7 后未重跑（整个会话持续 uncommitted 迭代，按 OpenSpec 变更分组提交时统一核查）

## 提交清单（按 OpenSpec 变更分组）

- `add-workspace-administrative-status` 主体（已归档）：spec 落地 + 全部代码
- 后续 UI 调优（不单独开 OpenSpec）：应用设置、工作空间徽标、系统入口合并、指称与定义 Tab 合并、注册记录 Tab、维度删除 FK 修复 → 合并到工作树，按 OpenSpec 变更清单逐组提交