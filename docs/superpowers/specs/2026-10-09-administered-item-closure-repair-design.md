# 注册库内容缺失受管项实例闭环修复设计

日期：2026-10-09
状态：待确认（用户确认后进入 OpenSpec 工件）
变更名（OpenSpec）：`repair-administered-item-closure`

> 定性更正记录：初稿曾把本问题定为「WS 363/364 基准数据补注册状态」，属**症状层定级**，
> 会把元模型缺陷固化成一次性数据补丁。经标准原文核对与用户指正后重定为
> **注册库内容缺失受管项实例的闭环缺陷**——初稿设计的问题（CHECK 约束陈旧、
> SQLite 64 表 join 上限）仍然成立并纳入本文档。

## 1. 缺陷陈述

ISO/IEC 11179-3:2023 §9.4.1.2：

> **As an abstract superclass**, Registered_Item shall be instantiated as **either an
> Administered_Item (9.4.2) or an Attached_Item (9.4.3) but not both.**

这是 **shall**。当前注册库中，11,177 个已标识（identified）条目既不是 Administered_Item
也不是 Attached_Item——继承链停在 `Identified_Item` 即断裂，违反上述条款。

**这不是"某个字段没填"，而是注册库内容没有完成标准要求的元模型闭环。**

### 1.1 标准依据链

| 条款 | 原文要点 | 本库符合性 |
| --- | --- | --- |
| §7.4.1 item_identification | "If an instance of the Item class is to be identified, then it **shall** be linked with **exactly one** instance of the Identified_Item class" | ✅ 11,177 行已建 `identified_item` |
| §7.4.2 identification | "**Every instance of the Identified_Item class shall have links** via one or more instances of the identification association" | ✅ 11,177 `scoped_identifier` |
| §9.4.1.2 | "Registered_Item shall be instantiated as either an Administered_Item or an Attached_Item but not both" | ❌ **0 条 Administered_Item / Attached_Item** |

§7.4.1 的 NOTE 允许条目不带标识符进入注册库（"allowing an item to be entered into a
registry without being identified"），但那**不适用于本批数据**——它们已有标识符，
处于 identified 状态，作为注册库内容被管理，落在 §9.4.1.2 "a registered item is an
identified item that is registered and managed in a metadata registry" 的定义内，
因而必须落到 Administered_Item 或 Attached_Item。

### 1.2 现状实测（SQL Server 真实库 `metadata_registry`）

| 表 | 行数 |
| --- | --- |
| `mdr.item` | 25,217 |
| `mdr.identified_item` | 11,177 |
| `mdr.registered_item` | **1** |
| `mdr.administered_item` | **1** |
| Attached_Item 对应行 | **0** |
| `mdr.scoped_identifier` | 11,177 |

即：11,177 个已标识条目全部缺环。数据库中 `administered_item` / `registration_state`
的唯一一行属于 `CTX.00001`（状态 RECORDED，2026-10-08 由应用创建）。

### 1.3 标准依据：受管链与内容链正交（决定了修法方向）

初稿曾误判为「具体子类应改继承到 Administered_Item」。经原文核对**该方向是错的**：

- **ISO/IEC 11179-31:2023 §7.2.2.4.1**：
  > **Data_Element is a subclass of Item** (specified in ISO/IEC 11179-3:2023, 6.4.2.1),
  > allowing instances to be identified, registered, administered, named, defined and classified.

  同文件 §7.2.2.5.1 Data_Element_Concept → Concept → Item；11179-3 §9.3.2.1 Namespace、
  §8.2.2.1 Context 亦同句式。**标准中内容类一律直接挂 Item。**
- **§7.3.1 Identified_Item 无 "Direct superclass" 子小节**（仅有 7.3.1.1 Description /
  7.3.1.2 Associations / 7.3.1.3 Attributes），且未声明为抽象类。

结论：**"被管理"链（`Entity → IdentifiedItem → RegisteredItem → AdministeredItem`）
与"内容类"链（`Item → DataElement/ValueDomain/...`）在标准中是两条正交链，靠 §7.4.1
的 `item_identification` 关联连接，不靠继承。**

因此：

- `IdentifiedItem extends Entity` + `@OneToOne Item item` **是正确建模**，
  亦符合 AGENTS.md 实体继承规则「标准未声明 Direct superclass 的类 MUST 建模为独立根实体」
  （先例 Contact/Organization/LanguageIdentification）。不得改为抽象——§7.4.1 要求
  Item 绑定 exactly one 可实例化的 Identified_Item。
- `DataElement extends Item` **符合 §7.2.2.4.1**，不得改为 `extends AdministeredItem`。
  强行把正交链拧成继承会制造新的标准偏差，且与
  `registry-specification-model-correction`（2026-10-09，孤儿行 → FK 失败 → 启动挂起
  13 分钟事故）属同一类错误。

**修法是补实例，不是改继承。**

## 2. 根因分析

| # | 根因 | 说明 |
| --- | --- | --- |
| 1 | 导入路径绕过唯一建壳代码 | `administered_item` / `registration_state` 全库仅由 `ItemAdministrationService.ensureAdministeredItem()` 创建。基准还原脚本是裸 SQL `INSERT`，**天然产不出这两张表** |
| 2 | 验收标准只看字节与行数 | `db-baseline/README.md` 称"还原后与基准库逐字节一致"，验证方式为"行数与基准声明一致"。基准库本身导出自同样缺壳的机器——**导出-导入-校验全链无任何一环检查语义不变式** |
| 3 | 守护测试只守继承边 | `StandardInheritanceConformanceTest` 守"extends 边须有标准依据"。「带 Scoped_Identifier 的 Item 必须有受管实例」是**跨表不变式**，不属于任何继承边，测试网未覆盖 |
| 4 | AGENTS.md 规则单向 | 实体继承规则防"凭空发明继承"，未防"漏建正交关联链"。同一次事故只提炼了一半教训 |

**危害面**：任何自动化流程（批量导入、联邦同步、孤儿清理、schema 校验）都会在这
11,177 行遇到"有标识符但非受管项"的异常态。

## 3. 目标 / 非目标

### 目标

1. 为全部 11,177 个已标识条目补齐 `registered_item` + `administered_item` 实例，
   使 §9.4.1.2 的 shall 成立。
2. 为其补 `registration_state`，`registration_status = 'PREFERRED_STANDARD'`、
   `effective_date = '2024-04-01'`（见 §4）。
3. 移除 `ItemRegistrationStateSection` 的推进流程提示（该提示对本次修复前状态不成立）。
4. 修正 `registration_status` CHECK 约束至标准 10 值集（三方言）。
5. 根治 SQLite 64 表 join 上限，使补壳后条目在个人版仍可编辑/删除。
6. **新增守护不变式测试**，防下次导入重现。
7. **AGENTS.md 补正交链完整性规则**。
8. 同步 `db-baseline/README.md` 还原步骤。

### 非目标

- 不改动任何实体继承关系（见 §1.3）。
- 不改动 `registration_status` 只读约束与推进流程设计（D6 / `registration-process` spec 不变）。
- 不为基准数据伪造 `previous_state` 状态链（§6.1）。
- 不设置 `administered_item.registration_authority_id`（§6.3）。
- 不处理 WS 218 / 365–375 等其余标准目录。

## 4. 生效日期来源

`mdr.reference_document.uri` 已记录的本地 PDF：

```
WS 卫生信息标准原文目录\363 卫生信息数据元目录\WST 363-2023\WST 363.<n>—2023.pdf
WS 卫生信息标准原文目录\364 卫生信息数据元值域代码\WST 364-2023\WST 364.<n>—2023.pdf
```

逐份 `pdftotext -f 1 -l 1` 提取首页，34 份（WS/T 363.1–363.17、364.1–364.17）
结果**完全一致**：

```
WS/T 363.1—2023   代替 WS 363.1-2011
2023 - 10 - 07 发布
2024 - 04 - 01 实施
```

> 目录名为「强制性行业标准」，但文件实为 **WS/T**（推荐性行业标准），与用户判断一致。
> WS 363-2011 / 364-2011 旧版亦在库中，但基准数据为 2023 版。

**取值：`effective_date = 2024-04-01`，全库统一。**

## 5. 方案比较与裁定

### 5.1 补壳范围

| 方案 | 覆盖 | 裁定 |
| --- | --- | --- |
| **A. 全部有 identified_item 的 item（采用）** | 11,177 | §9.4.1.2 的 shall 对所有 identified 条目一体适用，无区分依据 |
| B. 仅 2,200 数据元 | 6,600 | 否决：WS 364 值域同受 shall 约束，排除即保留同类违反 |
| C. 全部 25,217 item（含无标识符的 14,040） | — | 否决：那 14,040 行未标识，§7.4.1 明确允许，不在本次缺陷面 |

### 5.2 Administered_Item vs Attached_Item

用户已确认：**全部补 `Administered_Item`**。WS 363/364 条目是注册库的一等管理内容
（有注册机构卫宁健康管理、有注册状态与生命周期），不是附着项。
`AttachedItem`（`AttachedItem.java:14`，`extends RegisteredItem`，持
`administered_item_id`）在本次不产出数据。

### 5.3 脚本落点

`db-baseline/README.md` 说明两个 11MB 脚本由 `scratch/ws-verify/gen_baseline_sql.py`
从 `data/metadata-registry.db` 只读导出生成。**该库已不存在**（`data/` 下仅余
`.bak` 与辅助 SQL），主脚本**无法重新生成**。

| 方案 | 内容 | 裁定 |
| --- | --- | --- |
| **A. 独立补壳脚本（采用）** | 新增 `db-baseline/ws363-364-administered-item-{sqlite,sqlserver}.sql`，幂等补壳；主脚本不动 | 主脚本不可重生成；独立脚本 diff 可审、幂等可重跑、同服务两方言 |
| B. 原地追加到主脚本尾部 | 在 11MB 文件末尾加 INSERT 段 | 否决：破坏"单一职责还原脚本"定位，日后与基准库比对字节困难 |
| C. 改生成器重导 | 恢复生成源后重导 | 否决：生成源已丢失，且会漂移无关数据 |

**同时必须修正生成器**：`scratch/ws-verify/gen_baseline_sql.py` 的 `TABLES` 常量
需纳入 `registered_item` / `administered_item` / `registration_state`，否则将来恢复
生成源后重新导出仍会产出缺环数据。

### 5.4 SQLite 64 表 join 上限

`KNOWN_FAILURES.md` 的 `failure-2026-09-21-sqlite-64-table-join-limit`
（status **active**、verified）：带 AdministeredItem 的条目在 SQLite 上
update/delete 必 500。补壳后 11,177 行将全部命中。

| 方案 | 内容 | 裁定 |
| --- | --- | --- |
| **A. `assertCanModifyItem` 改轻量投影查询（采用）** | 不加载 `AdministeredItem` 实体图，改为只取 `registration_authority_id` 的投影 | 精准命中触发点，不动继承映射，风险可控 |
| B. 拆分实体继承映射 | JOINED 改单表 | 否决：影响全部 Item 子类，爆炸半径远超本次 |
| C. 个人版跳过权限校验 | 特殊分支 | 否决：牺牲正确性换性能 |

## 6. 详细设计

### 6.1 前端：移除推进流程提示

- `metadata-registry-ui/src/components/ItemRegistrationStateSection.vue:14-21`：删除
  该 `el-alert` 块（含 `readonly-status-hint` class）。
- `metadata-registry-ui/src/i18n/zh-CN.json:338`：删除 `itemRegistrationState.editHint`。
- `metadata-registry-ui/src/i18n/en-US.json:320`：同步删除。
- `metadata-registry-ui/src/components/ItemRegistrationStateSection.spec.ts`（与组件同目录）：
  现断言编辑态**存在**该提示且文案含「推进流程」，改为断言**不存在**；保留「编辑态无
  `ElSelect`」「新建态下拉恰为 3 项（不完整/历史/应用）」。

编辑态保留：只读状态标签 + 生效日期 + 截止日期 + 管理备注 + 未决问题 + 解释性注释。
`registration_status` 仍不可经表单变更（1124 守卫与 D6 不变）。

### 6.2 迁移：修正 CHECK 约束（三方言同版本号）

新版本 `V20261009009`（接续当前最高 `V20261009008`）。

**阻断项**：真实库 CHECK 仍是旧值集，实测（事务内 INSERT 后 ROLLBACK）：

```
PREFERRED_STANDARD: REJECTED
```

```
([registration_status]='APPLICATION' OR 'HISTORICAL' OR 'RETIRED' OR 'SUPERSEDED'
 OR 'PREFERRED' OR 'STANDARD' OR 'QUALIFIED' OR 'RECORDED' OR 'SUBMITTED' OR 'INCOMPLETE')
```

缺 `CANDIDATE` 与 `PREFERRED_STANDARD`，多出标准中不存在的 `SUBMITTED`、`PREFERRED`。

**根因**：反向纠偏脚本 `V20260901001__Correct_registration_status_enum.sql` 三方言均在，
但从未进入 `ext.flyway_schema_history`——项目 `baseline-version=20260918002` 将版本号
≤ 基线的迁移跳过；而方向错误的 `V20260517001__Migrate_registration_status_enum.sql`
在基线建立前已执行并加了 CHECK，约束就此定型。

- **SQL Server**：先查 `sys.check_constraints` 取实际约束名（**不硬编码**），
  `ALTER TABLE mdr.registration_state DROP CONSTRAINT …` + `ADD CONSTRAINT … CHECK`
  （标准 10 值集）。
- **SQLite**：不支持 ALTER CONSTRAINT，须**重建表**（建新表 → `INSERT…SELECT` →
  `DROP` → `RENAME`），保留既有索引与 FK；带幂等守卫（旧 CHECK 已不存在则 no-op）。
  注意 SQLite 基线 DDL `V20260723002__Schema_baseline.sql:813` 同为旧值集，
  全新库路径同样需要本迁移兜住。
- **达梦**：全限定 `MDR.REGISTRATION_STATE`，语法对齐 SQL Server；按项目既有惯例
  达梦未实测（记入遗留）。

### 6.3 根治 SQLite 64 表 join 上限

- `AdministeredItemRepository`：新增只取归属注册机构的轻量查询（投影
  `registrationAuthority.id`，返回 `Optional<UUID>` 或等价形态），不返回实体。
- `ActiveRegistrarService.assertCanModifyItem`（`:166-176`）改用该查询：无行或 RA 为
  null 放行，有行则走既有 `assertActiveIdentityOf`。
- `assertCanModify(AdministeredItem)`（实体在手场景）不改。
- TDD：新增用例 + SQLite 冒烟实测无 `at most 64 tables in a join`。

### 6.4 补壳脚本

`db-baseline/ws363-364-administered-item-{sqlite,sqlserver}.sql`：

1. **幂等守卫**：仅处理「有 `identified_item`、无 `registered_item`」的条目。
2. **补 `registered_item`**：`id = identified_item.id`（继承链要求同 id），
   `constraint_set_id = NULL`。
3. **补 `administered_item`**：`id = identified_item.id`，`registration_state_id` 指向
   新建状态行，`registration_authority_id = NULL`（§7.3），`origin` 填可追溯标识
   （如 `WS 363-2023/WS 364-2023 基准导入`），`creation_date_time` /
   `last_change_date_time` / `change_description` / `explanatory_comment` /
   `stewardship_record_id` 均 NULL。
4. **补 `registration_state`**：`registration_status = 'PREFERRED_STANDARD'`、
   `effective_date = '2024-04-01'`、`until_date = NULL`、`previous_state_id = NULL`、
   `administrative_status = NULL`、`administrative_note` / `unresolved_issue` = NULL。
5. **UUID 生成**：SQLite 用 `lower(hex(randomblob(4)))||'-'||…` 拼 v4 格式；
   SQL Server 用 `NEWID()`。同一条目三行 id 必须一致。
6. 整体包 `BEGIN TRANSACTION` / `COMMIT`，与现有基线脚本风格一致。

`db-baseline/README.md` 增补第 2 步还原命令与前置顺序说明，并修正"仅 SQLite 方言"
等已过期的限制描述。

### 6.5 守护不变式测试（防重现）

新增 `AdministeredItemClosureConformanceTest`（`metadata-registry-web` 测试目录，
与 `StandardInheritanceConformanceTest` 同级 `standard/` 包）：

- **不变式**：`mdr.identified_item` 中每一条，其对应 item 都必须能沿
  `item_identification → IdentifiedItem → RegisteredItem` 到达 `Administered_Item`
  或 `Attached_Item`（§9.4.1.2 shall）。
- **实现**：反连接查询返回违反集，断言为空。
- **测试形态**：
  1. 在 SQLite 副本库上跑补壳脚本前后各断言一次——修复前**必须失败**（红），
     修复后**必须通过**（绿），构成 TDD 红绿证据；
  2. 对真实库/种子库跑绿断言；
  3. 单元级断言反连接查询本身正确（含构造违规样本）。
- **与既有测试的关系**：不并入 `StandardInheritanceConformanceTest`——后者守继承边，
  本条守跨表正交链不变式，语义不同，分开更清晰。

### 6.6 生成器修正

`scratch/ws-verify/gen_baseline_sql.py` 的 `TABLES` 常量纳入
`registered_item` / `administered_item` / `registration_state`，并补 §6.4 的
INSERT 段生成逻辑，避免恢复生成源后重导仍产出缺环数据。

### 6.7 AGENTS.md 规则补充

在「实体继承规则（强制）」旁新增「正交链完整性规则（强制）」：

> - 涉及 §7.4.1 item_identification 等**正交关联链**的建模与数据导入时，MUST 保证
>   链的完整性：以 `Scoped_Identifier` 为例，其存在即要求 `Identified_Item`；
>   已标识（identified）条目 SHALL 沿 §9.4.1.2 落到 `Administered_Item` 或
>   `Attached_Item`，不得停在中间层。
> - **裸 SQL 数据还原 / 导入脚本 MUST 自建完整关联链**——`administered_item` 与
>   `registration_state` 全库仅由 `ItemAdministrationService.ensureAdministeredItem()`
>   创建，裸 SQL 天然产不出（2026-10-09 事故：11,177 条 WS 363/364 条目缺环）。
> - 验收 MUST 含**语义不变式断言**，不得只比对行数/字节。

### 6.8 真实库应用

按项目惯例，先备份到 `scratch/` 再执行，脚本幂等可重跑，并在
`.superpowers-memory/session-journal/` 记录。**执行需用户单独确认**
（涉及 `metadata_registry` 生产库写入）。

## 7. 语义说明

### 7.1 无 `previous_state` 链

单行 `PREFERRED_STANDARD` 表示「该条目以 Preferred Standard 状态进入注册库」。
这些条目是**从外部已发布标准整体导入**，并非经本注册库逐级推进而来，故不伪造
`INCOMPLETE → CANDIDATE → … → PREFERRED_STANDARD` 历史链。补齐后会出现在注册项列表，
可继续向下迁移（Retired / Application 等）。

### 7.2 `administrative_status` 留空

管理状态由工作空间流程系统治理，是**本注册库的运行时概念**，外部导入标准无本库流程
痕迹，留空不影响状态显示。

### 7.3 `registration_authority_id` 留空

- 置空：与当前一致（无壳时权限校验本就短路），避免给 11,177 行骤加「须为卫宁健康活动
  Registrar」的编辑闸门。
- 置为命名空间 RA（`6E486DAE-…`）：语义更准，但会骤然加上编辑权限闸门。

**裁定：置空。** 基准脚本是数据还原而非「本机构注册」行为，`registration_authority_id`
在代码中由 `resolveOwnershipForCreate` 从活动身份**推导**，回填不应伪造归属。
界面「注册机构」仍从命名空间显示卫宁健康，不受影响。

## 8. 验证计划

| 项 | 方式 |
| --- | --- |
| 不变式红绿 | SQLite 副本库补壳前不变式测试**失败**、补壳后**通过**（TDD 证据） |
| CHECK 约束 | 三方言迁移落库后 `PREFERRED_STANDARD` 写入被接受（事务内实测 + 保留校验） |
| 补壳正确性 | 补壳后"有 identified_item 无 registered_item"的条目数为 0；三表增量一致；无重复主键 |
| 数据一致 | 全部 identified_item 均可沿链取到 `PREFERRED_STANDARD`（反连接为 0） |
| SQLite join | SQLite 副本库补壳后对数据元执行 update/delete 冒烟，无 `at most 64 tables in a join` |
| UI | vitest 全量绿（改后的 `ItemRegistrationStateSection.spec.ts`）；编辑态无提示条、新建态 3 项下拉不变 |
| 回归 | 后端全量测试与既有基线失败集合对比无新增；`detect-changes` 无 partial/truncated |

## 9. 风险

| 风险 | 缓解 |
| --- | --- |
| 三方言 CHECK 迁移在 SQLite 重建表丢索引/FK | 迁移内显式重建 + 冒烟断言 |
| 真实库写入不可逆 | 先备份 `scratch/`；脚本幂等可重跑 |
| 11,177 条进入注册项列表影响首屏 | 列表已分页；必要时补索引（`V20260924001` 已有先例） |
| 达梦未实测 | 记入遗留，与项目既有惯例一致 |
| 生成源 `data/metadata-registry.db` 缺失 | 补壳脚本不依赖生成源；生成器修正先行落地，源恢复记入 backlog |
| 与并行的 `registry-specification-model-correction` 等未提交改动冲突 | 隔离 worktree 实施 |

## 10. 遗留 / backlog

1. 恢复 `data/metadata-registry.db` 生成源（否则主脚本永远无法再生成）。
2. WS 218 / 365–375 等其余标准目录的数据导入（同类闭环风险未评估）。
3. 14,040 个无 `identified_item` 的 item 是否需要闭环（§7.4.1 允许，需业务判断）。
4. 达梦方言实测。
5. 其余 mdr 表标准符合性全面审计（沿用既有 backlog）。