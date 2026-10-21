# repair-administered-item-closure 设计文档

## Context

见 `proposal.md` — Why。本节只列实现所需的现状事实与约束。

### 继承链与关联链（物理实测，SQL Server 真实库）

```
item ──item_id(FK)──> identified_item ──id(FK)──> registered_item ──id(FK)──> administered_item
                                                                              └──registration_state_id──> registration_state
```

`registered_item` 只有 `constraint_set_id` 一个自有列；`administered_item` 持有
`registration_state_id` / `registration_authority_id` / `stewardship_record_id`。
继承链每层 PK/FK 同名 `id`。

**JPA 侧的两条链是正交的**（这是本次修复最关键的结构认知）：

```java
public abstract class Item extends Entity                      // 内容类链根
public class DataElement extends Item                          // 11179-31 §7.2.2.4.1

public class IdentifiedItem extends Entity                      // 受管链根，无 Item 继承
    @OneToOne @JoinColumn(name = "item_id") private Item item;  // §7.4.1 item_identification 关联
public abstract class RegisteredItem extends IdentifiedItem     // §9.4.1.2 抽象
public class AdministeredItem extends RegisteredItem           // §9.4.2
public class AttachedItem extends RegisteredItem               // §9.4.3，持有 administered_item_id
```

`Item` 与 `IdentifiedItem` 之间是 `@OneToOne` 关联而非继承。因此一个 DataElement
条目要获得注册状态，必须**同时**存在 `data_element` 行与其绑定的
`identified_item → registered_item → administered_item → registration_state` 行。

### 行数现状

`item` 25,217 / `identified_item` 11,177 / `registered_item` 1 / `administered_item` 1 /
`attached_item` 0 / `scoped_identifier` 11,177。11,177 行全部挂在同一命名空间
（`registration_authority_id = 6E486DAE-…`）。

### 约束与阻断

1. **CHECK 约束阻断**：真实库 `registration_state.registration_status` 约束为
   ```
   IN ('APPLICATION','HISTORICAL','RETIRED','SUPERSEDED','PREFERRED',
       'STANDARD','QUALIFIED','RECORDED','SUBMITTED','INCOMPLETE')
   ```
   事务内实测 `PREFERRED_STANDARD` 写入 **REJECTED**。
   反向纠偏脚本 `V20260901001__Correct_registration_status_enum.sql` 三方言均在，
   但不在 `ext.flyway_schema_history` 中——`baseline-version=20260918002` 将其跳过；
   而方向错误的 `V20260517001` 在基线前已执行并加了 CHECK。
   SQLite 基线 DDL `V20260723002__Schema_baseline.sql:813` 同为旧值集。

2. **SQLite join 上限阻断**：`KNOWN_FAILURES.md` 的
   `failure-2026-09-21-sqlite-64-table-join-limit`（status active / verified）。
   触发链：`ActiveRegistrarService.assertCanModifyItem:171` →
   `AdministeredItemRepository.findByItem_Id` → 加载完整受管项对象图 →
   `[SQLITE_ERROR] at most 64 tables in a join`。补壳后 11,177 行全部命中。

3. **生成源缺失**：`db-baseline/README.md` 记载两个 11MB 脚本由
   `scratch/ws-verify/gen_baseline_sql.py` 从 `data/metadata-registry.db` 导出，
   该库**已不存在**（`data/` 仅余 `.bak` 与辅助 SQL），主脚本不可重新生成。
   两脚本对 `registered_item` / `administered_item` / `registration_state`
   的引用数经 `grep -c` 均为 **0**。

### 生效日期来源

`mdr.reference_document.uri` 指向 `WS 卫生信息标准原文目录\` 下 34 份
PDF（WS/T 363.1–363.17、364.1–364.17）。`pdftotext -f 1 -l 1` 逐份提取首页，
结果完全一致：`2023-10-07 发布` / **`2024-04-01 实施`**。

## Goals / Non-Goals

### Goals

- 闭环不变式成为**构建期可验证**的约束，而非人工检查项。
- 补壳脚本**集合式、幂等、不硬编码行数**，可对任意目标库执行。
- 同一修复在 SQL Server 与 SQLite 上均可用；达梦脚本齐备（未实测，记遗留）。
- 缺陷可被复现：补壳前守护测试必须红。

### Non-Goals（设计级边界）

- 不引入新的状态枚举值、不改状态机与推进流程。
- 不为补壳条目伪造状态历史链或注册机构归属（语义裁定见 specs）。
- 不重构实体继承映射（爆炸半径过大，另立变更）。
- 不恢复 `data/metadata-registry.db` 生成源（记 backlog，本次脚本不依赖它）。
- 不处理 WS 218 / 365–375 等其余标准目录。

## Decisions

### D1：补实例，不改继承

**决策**：新增 `registered_item` / `administered_item` / `registration_state` 数据行；
**不动任何 `extends` 声明**。

**依据**：ISO/IEC 11179-31:2023 §7.2.2.4.1 明文 "Data_Element is a subclass of Item"；
§7.3.1 Identified_Item 无 "Direct superclass" 子小节，受管链与内容链在标准中是两条
正交链，靠 §7.4.1 关联连接。

**替代方案**：
- *把 `DataElement` 改为 `extends AdministeredItem`*：与 §7.2.2.4.1 直接冲突；且因
  `IdentifiedItem extends Entity` 不在 Item 链上，会把两条正交链拧成继承，使 §7.4.1
  关联失去意义或产生第二条 item_identification 路径。与
  `registry-specification-model-correction`（2026-10-09 孤儿行 → FK 失败 → 启动挂起
  13 分钟）属同一类错误形状。否决。
- *把 `IdentifiedItem` 改为抽象类*：标准未声明其为抽象；且 §7.4.1 要求 Item 绑定
  exactly one **可实例化的** Identified_Item，改抽象后绑定对象消失。否决。
- *两者都保留现状、只做数据补齐*：采纳。

### D2：补壳范围取全部 11,177

**决策**：对所有「有 `identified_item`」的条目补壳。

**依据**：§9.4.1.2 的 shall 对所有 identified 条目一体适用。§7.4.1 NOTE 允许条目不带
标识符入库，故那 14,040 个无 `identified_item` 的 item **不在本次范围**。

**替代方案**：*仅补 2,200 数据元*——会保留 WS 364 值域的同类违反，制造"数据元闭环而
其引用值域不闭环"的新不一致。否决。

### D3：全部补 Administered_Item，不补 Attached_Item

**决策**：11,177 行一律落 `Administered_Item`。

**依据**：WS 363/364 条目是注册库的一等管理内容（有注册机构、有注册状态与生命周期），
符合 §9.4.2 "a registered item for which administrative information is recorded by a
registration authority"。`AttachedItem` 表达的是附着关系（持 `administered_item_id`
外键指向宿主），WS 363/364 无此语义。用户已确认。

### D4：独立补壳脚本，不改主脚本

**决策**：新增 `db-baseline/ws363-364-administered-item-{sqlite,sqlserver}.sql`；
`ws363-ws364-baseline-{sqlite,sqlserver}.sql` 不动。同时修正生成器表清单。

**依据**：主脚本不可重新生成（生成源丢失），只能原地追加或加补丁；独立脚本 diff 可审、
幂等可重跑、同服务两方言，且不破坏"单一职责还原脚本"的定位。

**替代方案**：*原地追加到主脚本尾部*——破坏脚本职责边界，日后与基准库比对字节困难。
否决。*改生成器重导*——生成源已丢失且会漂移无关数据。否决。

**生成器修正为何不能推后**：生成源恢复后重导会再次产出缺环数据，使缺陷复发。故表清单
修正必须与补壳脚本同批落地。

### D5：CHECK 约束用新版本号重建，不复用被跳过的旧脚本

**决策**：新增 `V20261009009` 三方言迁移重建约束；**不复用**
`V20260901001`（其版本号 ≤ `baseline-version=20260918002`，永远不会被执行）。

**依据**：Flyway 对 ≤ 基线版本的迁移直接跳过，复用即等于不执行。

**SQLite 要点**：不支持 `ALTER TABLE … ADD/DROP CONSTRAINT`，须重建表
（建新表 → `INSERT…SELECT` → `DROP` → `ALTER … RENAME`），并显式保留索引与 FK；
带幂等守卫（已是标准值集则 no-op）。SQL Server 须先查
`sys.check_constraints` 取**实际约束名**，不硬编码（历史迁移的约束名不可假设）。

### D6：SQLite join 上限走查询形状收敛

**决策**：`assertCanModifyItem` 改为只查询归属注册机构标识（投影），不再加载
`AdministeredItem` 实体。

**依据**：该校验只需要 `registrationAuthority.id`。加载整棵受管项对象图（含
registration_state / stewardship / constraint_set / 引用文档等）是纯粹的浪费，且是
64 表 join 的唯一来源。

**替代方案**：*拆分继承映射为单表*——影响全部 Item 子类，爆炸半径远超本次。否决。
*SQLite 上跳过权限校验*——牺牲正确性换性能。否决。
*按方言分支*——方言中立是既有 `database-portability` 规约要求。否决。

### D7：守护测试独立于继承守护测试

**决策**：新增 `standard/AdministeredItemClosureConformanceTest`，不并入
`StandardInheritanceConformanceTest`。

**依据**：后者守"extends 边须有标准依据"；本项守跨表正交链不变式。语义不同，
混在一起会让两类失败的归因困难。测试形态见 tasks。

### D8：导入方（用户）不伪造归属

**决策**：`administered_item.registration_authority_id` 留空。

**依据**：该字段在应用中由 `resolveOwnershipForCreate` 从活动注册人员身份**推导**，
是运行时归属而非静态事实；还原脚本是数据还原而非"本机构注册"行为，伪造归属会
给 11,177 行骤加"须为卫宁健康活动 Registrar"的编辑闸门。界面"注册机构"仍由命名空间
呈现，体验不变。

## Risks / Trade-offs

| 风险 | 缓解 |
|---|---|
| SQLite 重建表丢索引/FK | 迁移内显式重建；冒烟断言索引与 FK 均在 |
| SQL Server 约束名未知导致迁移失败 | 迁移先查 `sys.check_constraints` 动态取名，不硬编码 |
| 真实库写入不可逆 | 执行前备份至 `scratch/`；脚本幂等可重跑；**执行需用户单独确认** |
| 11,177 行进入注册项列表影响首屏 | 列表已分页；必要时补索引（`V20260924001` 已有先例） |
| 达梦未实测 | 三方言脚本齐备，达梦记入遗留（与项目既有惯例一致） |
| 补壳后权限校验行为变化 | RA 留空 → 校验短路 → 与补壳前一致，无行为变化 |
| 与并行未提交改动（`registry-specification-model-correction` 等）冲突 | 隔离 worktree 实施 |

## Migration Plan

**部署顺序**（迁移版本号 `V20261009009` 须在三方言下均可应用）：

1. 备份真实库到 `scratch/`。
2. 应用 `V20261009009`（CHECK 约束修正）——**必须先于**补壳脚本，
   否则 `PREFERRED_STANDARD` 写入被拒。
3. 执行 `db-baseline/ws363-364-administered-item-sqlserver.sql`（或 sqlite 版）。
4. 运行闭环守护测试确认转绿。
5. 真实库写入**需用户单独确认后**执行。

**回滚**：补壳脚本新增的行可按 `origin` 标记（`WS 363-2023/WS 364-2023 基准导入`）
精确识别并回删；CHECK 约束回滚为反向迁移即可（枚举值本身未被改动，
仅约束收紧/放宽）。故回滚代价低。

## Open Questions

无。以下均已在 specs 或 D1–D8 中定案：补壳范围（D2）、受管项类型（D3）、
脚本落点（D4）、CHECK 修正方式（D5）、SQLite 修法（D6）、
归属与状态历史（specs 两节）。