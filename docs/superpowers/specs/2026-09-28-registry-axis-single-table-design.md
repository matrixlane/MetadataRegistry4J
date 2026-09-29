# 注册轴继承层次物理表单表化设计

- 日期：2026-09-28
- 状态：待用户确认
- 工作流：superpowers-openspec-superpowers-workflow

## 1. 背景与问题

当前注册轴继承层次采用 JOINED 表继承（每个抽象层一张独立表）：

```
Item(item) ← 0..1 → IdentifiedItem(identified_item) → RegisteredItem(registered_item)
                                                        ├─ AdministeredItem(administered_item)
                                                        └─ AttachedItem(attached_item)
```

物理事实（已核实）：

- `registered_item` 表整表只有 `id` 一列（abstract 层无独立存在性）；
- `identified_item` 只有 `id + item_id` 两列；
- 一次完整对象组装需 5 张表（`data_element → item → identified_item → registered_item → administered_item`）；
- 搜索热点 `MetadataSearchService`（:274-339）显式 join 三表；`ContextTestDataInitializer`（:85-114）手工四层级联删除；`identified_item.id` 与 `item.id` 双 ID 空间已造成实际缺陷（`DimensionalityService` :85-93 防御性注释）；
- 项目既有平台限制与此直接相关：SQLite 64 表 join 上限（带 AdministeredItem 条目 update/delete 500）、SQL Server 选择列 4096 上限（`add-workspace-administrative-status` 已用瘦查询规避）。

ISO/IEC 11179-3:2023 中该四层为纯继承类，`RegisteredItem` 为 abstract，无独立实例——物理合并不损失标准语义，逻辑模型由 Java 类层次保留。

## 2. 已确认决策

**Java 类层次、字段、关联全部不变；仅将物理表从 JOINED（4 张）改为 SINGLE_TABLE（1 张）。**

已评估并否决的替代方案：

| 方案 | 否决原因 |
|---|---|
| 维持 JOINED 现状 | join 复杂度、双 ID 空间缺陷、SQLite/SQL Server 平台限制未解 |
| `@MappedSuperclass` 消掉 `registered_item`（方案 A） | 只省 1 张表，收益有限，不如一步到位 |
| 类层级向上抹平（`AdministeredItem`/`AttachedItem` 类型消失） | 类型互斥不变式失守、ISO 溯源变弱、web 模块 45+ 处业务代码重构，投入产出为负 |
| 父类下放到叶子（concrete-table） | 父类存在多态引用（`ScopedIdentifier`、`Item.identifiedItem`、两个 M2M、`SubmissionRecord`），一张 FK 无法指向两张表 |

## 3. 目标物理结构

以 `identified_item` 为合并目标表（**保持表名不变**，将改动面降至最小）：

| 列 | 来源 | 约束 |
|---|---|---|
| `id` | 三层共用 | PK（三层 PK 已天然相等：`ai.id = ii.id = ri.id`） |
| `record_type` | 新增判别列 | varchar，`'ADMINISTERED'` / `'ATTACHED'` |
| `item_id` | identified_item | unique，指向 `item.id`（双轴锚点，不动） |
| `constraint_set_id` | registered_item | 可空 |
| `creation_date_time` | administered_item | **可空（见 4.2）** |
| `last_change_date_time`、`change_description`、`explanatory_comment`、`origin` | administered_item | 可空 |
| `registration_state_id` | administered_item | unique、可空 |
| `stewardship_record_id`、`registration_authority_id` | administered_item | 可空 |
| `administered_item_id` | attached_item | 可空自引用（指向同表 `id`） |

消亡表：`registered_item`、`administered_item`、`attached_item`。

## 4. 技术要点

### 4.1 映射层改动（common-facilities）

- `IdentifiedItem.java:21`：`@Inheritance(JOINED → SINGLE_TABLE)`，新增 `@DiscriminatorColumn(name = "record_type", length = 20)`；
- `AdministeredItem`、`AttachedItem`：新增 `@DiscriminatorValue("ADMINISTERED")` / `@DiscriminatorValue("ATTACHED")`；
- `RegisteredItem` 保持 abstract `@Entity`，SINGLE_TABLE 下不产生独立表；
- 其余字段、关联（`Item.identifiedItem`、`ScopedIdentifier`、`registered_item_reference_document`、`submission_record_registered_item`、`AdministeredItem.attachedItems` 级联）零改动——JPA 类型引用随映射自动指向合并表。

### 4.2 唯一不可避免的微调：`creation_date_time` 约束策略

`AdministeredItem.creationDateTime` 现为 `@Column(nullable = false)`（ISO 11179-3 表 21 必备属性）。单表后 `AttachedItem` 插入同表却无该字段，DDL 级 NOT NULL 会导致附件插入失败。**已全字段核对：整个层次受影响的 NOT NULL 仅这一列**（`RegisteredItem`/`AttachedItem` 字段全部天然可空）。处理分两层：

- 列放宽：`@Column(nullable = false)` 放宽为可空（**字段保留**，仅列约束策略调整）；
- 应用层强制：`AdministeredItem` 加 `@PrePersist` 校验（为 null 即抛异常）；
- **数据库级等价约束**：三方言均支持判别列条件 CHECK，由 Flyway 迁移脚本统一补上（ddl-auto 先建表、Flyway 后执行，与 4.4 执行序兼容）：

  ```sql
  ALTER TABLE identified_item ADD CONSTRAINT chk_identified_item_creation_dt
  CHECK (record_type <> 'ADMINISTERED' OR creation_date_time IS NOT NULL);
  ```

  语义与原 `administered_item.creation_date_time NOT NULL` 完全等价（"ADMINISTERED 行必填、ATTACHED 行不受限"），数据库强制，覆盖手工 SQL 路径；
- 标准溯源：`@StandardReference("ISO/IEC 11179-3:2023, 表 21")` 注解保留。

### 4.3 外部 FK 重指向

以下列原指向 `administered_item` / `registered_item` / `attached_item`，迁移后重指向 `identified_item`（PK 值不变，仅目标表变化）：

- `approval_record.administered_item_id`、`approval_process_instance.administered_item_id`、`registration_transition_approval.administered_item_id`（`ApprovalRecord`/`ApprovalProcessInstance`/`RegistrationTransitionApproval`）；
- `ai_generation_record.administered_item_id`（ext 模块 `AiGenerationRecord`）；
- `scoped_identifier.identified_item_id`、`registered_item_reference_document.registered_item_id`、`submission_record_registered_item.registered_item_id`（目标表名不变，无需动作）；
- 合并表内 `administered_item_id` 成为自引用 FK。

既有库的 FK 由 Hibernate ddl-auto 生成、命名不可预测——迁移脚本采用既有模式（参照 `V20260920001__Drop_mapping_scheme.sql`）：动态扫描并删除引用旧三表的全部 FK，合并后按需重建。

### 4.4 迁移脚本（SQLite / SQL Server / 达梦 三方言）

执行序：Hibernate ddl-auto 先于 Flyway（既有架构）。对既有库：

1. ddl-auto 自动给 `identified_item` 增加新列（含 `record_type`，可空）；
2. Flyway 迁移（幂等、检测旧表存在与否）：
   - `UPDATE identified_item` 从 `registered_item` / `administered_item` / `attached_item` 取数回填，按来源打 `record_type`；
   - 动态删旧三表的全部传入 FK → `DROP TABLE` 旧三表；
   - 重建指向 `identified_item` 的必要 FK（含自引用）；
3. 全新库：ddl-auto 直接按 SINGLE_TABLE 元数据建表，迁移脚本空转（旧表不存在）。

需同步更新的基线/历史文件：

- `db-baseline/ws363-ws364-baseline-*.sql`、`metadata-registry-web/src/main/resources/db/migration/{sqlite,sqlserver}/V20260723002__Schema_baseline.sql`、`dm/V20260723001__Baseline.sql`（引用旧三表的 DDL 描述）；
- `MetadataSearchServiceTest` 中的建表清单。

### 4.5 原生 SQL 适配点

| 位置 | 改动 |
|---|---|
| `MetadataSearchService.java:274-339` | `administered_item ai JOIN identified_item ii JOIN registered_item ri` 改为单表 `identified_item`，按需加 `record_type = 'ADMINISTERED'` 过滤 |
| `ContextTestDataInitializer.java:85-114` | 四表手工级联删除简化为对 `identified_item` 的操作 |
| `DimensionalityService.java:85-93` | `identified_item` 表名不变，语义保持 |
| `MetadataSearchServiceTest.java:169-170`、`WorkspaceAdministrativeStatusMigrationTest.java:33` | 建表清单更新 |

JPA 类型化的业务代码（24 个 service、Repository 泛型、`@ManyToOne AdministeredItem` 字段）**零改动**。

### 4.6 顺带收益

- SQLite 表数 -3，缓解 64 表 join 上限（已知平台缺陷方向性改善，不承诺根治）；
- 注册链查询 SQL 简化，双 ID 空间 bug 面收窄；
- **注册记录 id 全局唯一由物理约束保证**：当前 JOINED 下该唯一性依赖 `identified_item` 根表；若改为两张叶子表（父类下放方案），两表 id 空间独立，UUID 随机碰撞虽可忽略但手工 SQL/迁移/数据复制路径无数据库约束兜底，且多态引用（`ScopedIdentifier`、`submission_record_registered_item`）在 id 撞车时不具消歧能力。SINGLE_TABLE 一张表一个 PK，唯一性从"约定"升级为"约束"；
- `attachedItems` 级联删除语义不变（11179-3 9.4.3.5）。

## 5. 风险与对策

| 风险 | 对策 |
|---|---|
| `creation_date_time` NOT NULL 卡附件插入 | 4.2 约束策略调整 + `@PrePersist` 强制 |
| ddl-auto 先于 Flyway 的中间态（`record_type` 为 NULL） | 迁移回填在启动流程内完成，查询发生在启动完成后；迁移幂等可重入 |
| 既有库数据合并错行 | 三层 PK 天然相等，合并为等值 UPDATE，无重行风险；迁移后校验计数（三表行数之和 = 合并表行数） |
| 达梦方言未实测（既有遗留） | 脚本按 DM 语法编写，标注待实测；不阻塞 SQLite/SQL Server 交付 |
| 原生 SQL 遗漏 | 全库 grep 旧三表名（`administered_item`/`registered_item`/`attached_item`）逐一核对 |

## 6. 验证计划

- web 模块全量测试（当前基线 1971 tests）；
- 前端测试/build 不受直接影响（零 API 变更），仅回归冒烟；
- 三方言迁移：SQLite 副本库重放、SQL Server 真实库（8081）应用、达梦待实测标注；
- 冒烟：管理项创建/注册状态流转/附件/提交记录/搜索/删除链路（含级联删除）；
- `detect_changes` 无 partial/truncated；`openspec valid`；
- 计数校验：迁移前后注册记录行数一致。

## 7. OpenSpec 变更名（预选）

`merge-registry-axis-single-table`

## 8. 标准依据附录：Attached_Item 关联的 Item 子类（ISO/IEC 11179-3:2023 原文核实）

- **9.4.3.2 定义**：attached item 是这样一种注册项，其管理信息记录在另一个注册项（administered item）中；每个 attached item 都有属主管理项，由属主为其与管理项两者提供管理信息。机制目的是将一组注册项作为一个组管理，而非为每个注册项维护独立管理信息；
- **9.4.3.2 EXAMPLE 1-4**（哪些 Item 子类应作附件）：EXAMPLE 1（11179-31）概念域内的 Value_Meaning 附着于 Conceptual_Domain；EXAMPLE 2（11179-32）概念系统内的 Assertion 与 Concept 附着于 Concept_System；EXAMPLE 3（11179-33）数据集的分发、评估、规范附着于数据集；EXAMPLE 4（11179-35）模型元素附着于模型。共同模式：附件 = 容器的组成部分；
- **附录 C.1 示例**：Administered_Item1 → Conceptual_Domain1（标识符 2625673281）；Attachment1 → Value_Meaning_Set1（…282）、Attachment2 → Meaning1（…283）、Attachment3 → Meaning2（…284）——**每个附件持有独立 Scoped_Identifier**，验证 `scoped_identifier.identified_item_id` 须可指向 ATTACHED 行（合并单表天然满足）；
- **9.4.3.5 约束**：属主管理项删除时其 attached items 必须一并删除——与现有 `AdministeredItem.attachedItems` 级联实现一致；
- **本项目现状**：已实现的候选附件类为 `ValueMeaning`/`ValueMeaningSet`/`PermissibleValueMeaning`/`PermissibleValueSet`/`Concept`（覆盖 EXAMPLE 1 场景）；`AttachedItem(Item)` 接受任意 Item，较标准示例更宽（标准未禁止，但全部官方示例均为"组成部分"语义，校验收紧留作后续方向，不在本次范围）；附件候选集是开放的 Item 子类集合，支持以单表 + 判别列承载两类注册记录的设计取向。
