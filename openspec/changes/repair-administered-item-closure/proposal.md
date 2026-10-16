## Why

注册库中 11,177 个 WS 363/364 基准条目只有 `Identified_Item`，既不是 `Administered_Item`
也不是 `Attached_Item`，违反 ISO/IEC 11179-3:2023 §9.4.1.2 的 **shall**
（"Registered_Item shall be instantiated as either an Administered_Item or an
Attached_Item but not both"）。这不是某个字段没填，而是注册库内容没有完成标准要求的
元模型闭环。

后果是这些条目落在操作死角：注册状态读不出来、无法出现在注册项列表、也就永远无法经
推进流程获得状态。用户视角表现为数据元对话框「注册状态」恒为 `-`。

暴露该缺陷的触发点是发现 CHECK 约束拒绝 `PREFERRED_STANDARD`（详见 design.md §6.2），
追查后定位到闭环本身。这是一次**元模型层面的缺陷**，不是一次性数据问题——任何裸 SQL
导入路径都会重现，故必须连同守护不变式与规则补充一并修复。

## What Changes

- **补齐受管项实例**：为全部 11,177 个已标识条目补 `registered_item` +
  `administered_item` + `registration_state` 三行，注册状态 `PREFERRED_STANDARD`，
  生效日期 `2024-04-01`（取自 WS/T 363/364-2023 各部分首页「2024-04-01 实施」，
  34 份一致）。
- **新增基准补壳脚本**：`db-baseline/ws363-364-administered-item-{sqlite,sqlserver}.sql`，
  幂等、集合式，不硬编码行数；主脚本不动（生成源 `data/metadata-registry.db` 已丢失，
  不可重新生成）。
- **修正基准生成器**：`scratch/ws-verify/gen_baseline_sql.py` 的表清单纳入三张受管表，
  防恢复生成源后重导仍产出缺环数据。
- **新增守护不变式**：`AdministeredItemClosureConformanceTest` 断言每个带标识符的
  Item 都可沿 `item_identification` 链到达 `Administered_Item` 或 `Attached_Item`，
  在 SQLite 副本库上以「修复前红、修复后绿」构成 TDD 证据。
- **修正 `registration_status` 的 DB CHECK 约束**：三方言迁移重建为标准 10 值集。
  当前真实库约束仍是旧值集（缺 `CANDIDATE`/`PREFERRED_STANDARD`，多出标准中不存在的
  `SUBMITTED`/`PREFERRED`），实测 `PREFERRED_STANDARD` 写入被拒绝。
- **根治 SQLite 64 表 join 上限**：`assertCanModifyItem` 改轻量投影查询，避免加载
  `AdministeredItem` 实体图。已知失败 `failure-2026-09-21-sqlite-64-table-join-limit`
  会使补壳后的 11,177 行在个人版全部无法编辑/删除。
- **移除注册状态 Tab 的推进流程提示**（文案调整）。
- **AGENTS.md 新增「正交链完整性规则」**：裸 SQL 导入 MUST 自建完整关联链；
  验收 MUST 含语义不变式断言，不得只比对行数/字节。

### 明确不改（BREAKING 风险规避）

- **不改动任何实体继承关系**。经标准原文核对：ISO/IEC 11179-31:2023 §7.2.2.4.1 明确
  "Data_Element is a subclass of Item"，§7.3.1 Identified_Item 无 "Direct superclass"
  子小节。故 `DataElement extends Item` 与 `IdentifiedItem extends Entity` 均为正确建模。
  受管链与内容链在标准中是**正交**的两条链，靠 §7.4.1 关联连接。改为继承会制造新的
  标准偏差，与 `registry-specification-model-correction`（孤儿行 → 启动挂起 13 分钟）
  属同一类错误。
- 不改动 `registration_status` 只读约束与推进流程设计（D6 不变）。
- 不为基准数据伪造 `previous_state` 状态链。

## Capabilities

### New Capabilities

- `administered-item-closure`: 受管项闭环不变式——已标识条目 SHALL 沿
  §9.4.1.2 落到 Administered_Item 或 Attached_Item；基准数据导入自建完整关联链；
  守护测试与 AGENTS.md 规则；补壳脚本与生成器修正。

### Modified Capabilities

- `registration-status`: 补充**数据库层 CHECK 约束**须与标准 10 值集对齐的要求。
  现有规约只在应用层规定枚举正确，DB 层约束未纳入，导致 `PREFERRED_STANDARD` 无法写入。
- `database-portability`: 补充 SQLite 上带受管项条目须可写的要求（64 表 join 上限不得
  使条目 update/delete 失败）。

## Impact

**数据**：真实库 `metadata_registry` 将新增约 33,531 行（11,177 × 3）。执行需用户单独确认。

**代码**：
- `metadata-registry-web/.../repository/AdministeredItemRepository.java`（轻量投影查询）
- `metadata-registry-web/.../service/ActiveRegistrarService.java:166-176`（改用投影）
- 新增 `AdministeredItemClosureConformanceTest`
- `metadata-registry-ui/src/components/ItemRegistrationStateSection.vue` + spec + i18n（zh-CN/en-US）

**迁移**：新增 `V20261009009`，SQL Server / SQLite / 达梦三方言同版本号。达梦按项目既有
惯例未实测。

**脚本**：`db-baseline/` 新增 2 个 SQL；`db-baseline/README.md` 增补还原步骤；
`scratch/ws-verify/gen_baseline_sql.py` 表清单修正。

**文档**：`AGENTS.md` 增补正交链完整性规则；`.superpowers-memory/` 会话记录更新。

**已知连带**：补壳后这 11,177 行将进入注册项列表（已分页，必要时补索引）。