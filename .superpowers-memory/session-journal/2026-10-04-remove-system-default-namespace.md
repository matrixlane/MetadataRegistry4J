# 2026-10-04/08 remove-system-default-namespace

## 背景与裁定

ISO/IEC 11179-3:2023 无"系统默认/根命名空间"概念（Milvus+PDF 双核实：`shorthand_prefix` 0..1 人类可读前缀、标准无唯一性要求；Namespace 识别可选；`identifier_scope` 仅约束每个 ScopedIdentifier 必属恰好一个 Namespace）。实库核实 `sys:` 未 scope 任何标识，唯一职责是门槛/默认推导排除哨兵。用户裁定：移除该功能（标准没有、实现用不到的不保留）；连带移除遗留系统组织（…0001）存量逻辑。`namespace.shorthand_prefix` 唯一索引因标准无要求且实现有应用层查重，不补。

## 交付（最终形态）

分支 `feat/remove-system-default-namespace`（worktree `.worktrees/remove-system-default-namespace`），**单提交 d5eb16c8，已 rebase 到 master 27921a1a（mdr-standard-compliance-cleanup 之后）**。

- 三方言迁移 `V20261010001__Remove_system_default_namespace.sql`（版本让位 master 已占的 20261004001-09006）：按 scheme 定位删除存量 sys:；防御（scoped_identifier/identifier_strategy_config/identifier_sequence_registry/**identified_item**——GlobalNamespaceService.wrapperOf 会把命名空间包装为受管项，identified_item.item_id→item.id 会使基行删除撞 FK，独立评审 MAJOR 修复）；先子后父删 designation 关联表（**表名适配 master V20260909005/06 更名**：designation_context / naming_convention_conformance / naming_convention_utilization——sqlite 链无 09005/06 保留旧名）→ designation → definition/slot/映射 → 关联表 → namespace → **item JOINED 基行**（漏删会致 Hibernate 多态装载 NPE，冒烟抓出修复）；随后清理无引用 …0001 组织。真实库验证：sys: 行+designation+基行全删、向导命名空间与卫星配置不动。
- 代码：`SystemDefaultNamespaceInitializer` 整类删除（含 3 常量）；`StaticDataPresetService.presetAll()` 去 sys: 步骤；`InitWizardService` 去 3.6 步（rebase 冲突点：master 已重构出 NamespaceProvisioningService，保留其 RA/命名空间供给代码仅删 sys: 挂接）；`ItemAdministrationService` 门槛简化 + `findFirstByRegistrationAuthority_Id`；`NamespaceRepository` 删 3 旧 finder；`OrganizationService` 删系统组织保护；master 新增 `RegistrationAuthorityDeletionService`/`DeletionPreview` 的 sys: 注释清理（逻辑本就通用）；前端删常量/门禁/推导排除（`gateBlocked` 死信号评审改暴露 `loaded`）。
- 测试：删 InitializerTest；改造 6 个存量测试类 + **master 新增 2 个**（InitWizardServiceOrderTest 去 @Mock 字段；RegistrationAuthorityDeletionServiceIntegrationTest 的 sys: 场景改为通用"非 RA 所属命名空间"夹具）；`OrganizationServiceTest` 补 delete 用例；集成测试断言强化为 setup 后 namespace 恰好 1 行。
- OpenSpec change `remove-system-default-namespace`：4 份主 spec delta（system-initialization 预置集合与系统组织段落删两条 MUST NOT、identification 删"前端禁用创建入口"SHALL、default-namespace-by-active-registrar 去 scheme 条件、active-registrar-ownership 措辞；**scenario 原名必须保留**——validate 硬约束）。

## 关联变更处置

`cleanup-namespace-nonstandard-columns`（治理列物理下线）：**退役**——master 的 V20261009005（三方言）已删 4 治理列，真实库 namespace 已 8 标准列，变更失去增量价值。其评审产出中 db-baseline 两脚本 INSERT 剥离治理列（32 行元组）**保留并入本提交**（master 的 09005 落地后还原脚本同样会炸）；`NamespaceNonstandardColumnsMigrationTest` 随变更删除。sqlite 遗留死列 + sqlite 链缺 09005/06 + V20260727001 的 `DROP COLUMN IF EXISTS` 语法错误（实测 SQLite 3.50 不支持）记入 backlog。

## 验证（rebase 后）

- 受影响 10 测试类 132 用例全绿（含 master 新 2 类）；web 全量 1473，15 失败=文档化预存家族（Workspace 6F+3E、SegmentTemplate 1E、ScopedIdentifierConcurrency 1F——2026-10-01 journal 已记录的 ScopedIdentifier*/Workspace*/SegmentTemplate* 家族），与本变更零交集。
- vitest ItemRegistrationSection 24/24（等待信号 gateBlocked→loaded）。
- SQL Server 应用级冒烟（真实库形状副本 mdr_dbg）：Flyway 链内应用 V20261010001（删除 sys: 行/2 designation/item 基行，卫星配置不动）→ 应用启动成功。注：验证期出现过一次"Flyway 报成功但 sys: 未删"的烟雾弹——最终复测证明当前制品正确（mdr_dbg 全链路删除+启动），初因未完全定位（疑似验证时序/环境问题），以最终复测为准。
- openspec validate ✓；`mvn -pl web -am`（~/.m2 SNAPSHOT 会被并行流程改写，必须 -am）。
- 教训：① 修改已应用迁移会触发校验和失配——利用迁移自身幂等删史重放治愈；② `-Dspring-boot.run.jvmArguments` 传含分号 URL 不可靠，用 env 传 DB_URL；③ target/classes 会残留已重命名迁移的旧副本，验证前须清理/重建。

## 状态

待 `continue-archive`（OpenSpec 归档：4 份主 spec delta 合并；归档时留意 `registry-context-bootstrap` 主 spec 仍有 sys: 表述但语义恒真，可不动）。
