# administered-item-reference-documents-ui 设计文档

## Context

见 `proposal.md` — Why。本节只列实现所需的现状事实与约束。

### 阻塞项复现

真实库 `metadata_registry`（WS 363/364 基准数据已补齐受管链）：

| 操作 | `/api/v1/data-elements` | `/api/v1/administered-items` |
| --- | --- | --- |
| 关联表为空 | 200 / 0.34s | 200 |
| `registered_item_submission_record` 挂 11,182 行 | 超时 40s+ | 超时 40s+ |
| 清空后 | 200 / 0.35s | 200 |

同在位的 23,449 条引用关联与 11,182 条 `administered_item.stewardship_record_id`
**均不影响**，故为单一根因。

### 根因调用链

```
GET /api/v1/data-elements
  └─ DataElementService.toResponse(entity)          逐条
       └─ ItemAdministrationService.buildItemMeta(item)          :880
            └─ submissionRecordRepository.findAllByRegisteredItemIdOrderByIdDesc   :943
                 SELECT DISTINCT sr FROM SubmissionRecord sr
                 JOIN sr.registeredItems ri WHERE ri.id = :itemId
```

`JOIN sr.registeredItems` 需实例化 `RegisteredItem`。`RegisteredItem extends
IdentifiedItem`，后者带 `@Inheritance(JOINED)`，其下挂 30+ 个 Item 子类
（`StandardInheritanceConformanceTest` 白名单可数）。Hibernate 为解析该关联，
把全部子类表拉进同一条 SQL 的 join。列表逐条调用 → N+1 × 重 join。

关联表为空时该 join 瞬间返回，故缺陷长期不可见。

> 该查询的原注释已写明：*"`sr.registeredItems` 为 `@ManyToMany`，`DISTINCT` 是必需的，
> 否则 JOIN 会因 `registered_item_submission_record` 无 UNIQUE 约束出现重复 sr 行"*。
> 当年已踩过这张表，只是当时表为空。

### 可复用的既有资产

| 能力 | 位置 |
| --- | --- |
| 参考文档增删端点 | `AdministeredItemController:109-121`（POST / DELETE） |
| 参考文档 CRUD | `ReferenceDocumentController` + `ReferenceDocumentResponse` |
| 注册项响应已暴露关联 | `AdministeredItemResponse.referenceDocumentIds` |
| 方言中立表名限定 | `DatabaseEnvironment.qualify(...)`，用法见 `ContextTestDataInitializer:127`、`DatabaseCleaner:185`、`DmFkConstraintStrategy:47` |

### 缺口

1. `ItemMetaResponse` 无 `referenceDocumentIds`——数据元对话框走这个 DTO。
2. `ItemFormDialog` 无参考文档 Tab（现有 Tab：注册标识 / 指称与定义 / 属性 /
   关联 / 注册状态 / 注册记录）。
3. 两张关联表原本无任何索引（已手工补 4 个，未进迁移）。

## Goals / Non-Goals

### Goals

- 消除提交记录查询的继承展开，使全库规模关联下列表接口保持亚秒级。
- 数据元对话框可查看与编辑本条目的参考文档关联。
- 索引以迁移固化，三方言一致。
- 修复验证后恢复提交关联。

### Non-Goals（设计级边界）

- 不动**类层次**：D6 只合并物理表，继承声明与标准条款引用一律不动。
- 不把 Item 子类的 JOINED 继承改单表（会影响全部 Item 子类，另立变更）。
- 不新增参考文档的增删端点（既有能力复用，避免契约分叉）。
- 不把参考文档混入 `ItemMetaRequest`——`ItemFormDialog` 为 15+ 条目类型共用，
  混入会把通用契约与受管项专属能力绑死。
- 本次不施加关联表 UNIQUE 约束（存量未验证无重复，见 D4）。

## Decisions

### D1：提交记录查询改走 JdbcTemplate + `DatabaseEnvironment.qualify`

`findAllByRegisteredItemIdOrderByIdDesc` 改为：

1. 经 `DatabaseEnvironment.qualify(...)` 定位 `registered_item_submission_record`，
   按 `registered_item_id` 取 `submission_record_id`（含 `ORDER BY submission_record_id DESC`）；
2. `submissionRecordRepository.findAllById(ids)` 取实体；
3. 在内存按 id 降序排序返回。

**为什么**：开销来自 `RegisteredItem` 的 JOINED 继承展开；只按 id 列查关联表完全不
触碰继承映射。方言中立由 `DatabaseEnvironment.qualify` 保证——这是项目既有模式
（见 `database-portability` 的「业务代码方言中立」要求）。

**替代方案与否决理由**：
- 原生 `@Query` 内硬编码 schema → 方言硬编码，违反方言中立。否决。
- `@BatchSize` / `join fetch` → 只把 N 次变 1 次，单次仍是重 join；且
  `buildItemMeta` 逐条调用的路径未批处理化。否决。
- 继承映射改单表 → 影响全部 Item 子类。否决。

### D2：`ItemMetaResponse` 增参考文档关联，按页批量取

`ItemMetaResponse` 增 `List<UUID> referenceDocumentIds`。取值经新增的批量仓储方法
（一次传入本页全部 `registered_item_id`，返回 `itemId → [docId]` 映射），
按 `itemId` 分桶回填。

**为什么必须批量**：`buildItemMeta` 在列表响应中被逐条调用。直接读
`administeredItem.getReferenceDocuments()`（`@ManyToMany` 默认 LAZY）即构成 N+1，
与 D1 修复的缺陷同型。批量查询保持"每页 1 次"。

**签名调整**：`buildItemMeta` 增加一个可选的批量关联入参（默认空 map），
调用点无需逐个改造；列表路径由 Service 层统一预取后传入。

**替代方案**：*在 `AdministeredItem` 上加 `@BatchSize`* —— 对
`@ManyToMany` 的分批语义不可靠，且未解决逐条调用结构。否决。

### D3：前端新增「参考文档」Tab，保存走既有增删端点

`ItemFormDialog` 在「注册记录」之后插入 `referenceDocuments` 页签，条件为
`showRegistration && form.meta.administeredItemId`（无受管链则不渲染——引用关联
挂在受管项上）。

- 列表：`identifier` + `title`，可删除
- 新增：远程搜索 `referenceDocumentApi`（`crud('/reference-documents')`）多选
- 保存：对比初始集合与当前集合，对差异集调用既有 POST / DELETE 端点

沿用既有 `el-tab-pane` + `hasTabError` 错误标记模式，与「关联」「注册记录」页签一致。

**替代方案**：*随 ItemMetaRequest 整体提交* —— 见 Non-Goals。否决。

### D4：索引以 `V20261009011` 固化；UNIQUE 约束另行判定

新增 `V20261009011`（sqlite/sqlserver/dm 三方言同版本号），为两张关联表各建两个
join 列索引。`CREATE INDEX IF NOT EXISTS` 三方言通用，风险低。

**为什么不同步加 UNIQUE**：多对多关联表语义上 `(两侧列)` 应唯一，但存量数据未验证
无重复。贸然加约束会在存量库上失败。先建非唯一索引解除扫描；
收紧为 UNIQUE 前 SHALL 先按确定性规则去重（保留 id 最小行 → 重定向引用 → 删多余行），
该工作记 backlog，并已在 `database-portability` delta 中立下"先验证再收紧"的要求。

### D6：受管链物理表合并，`IdentifiedItem` 降为 `@MappedSuperclass`

**决策**：保留 `RegisteredItem extends IdentifiedItem` 的类层次，
把 `IdentifiedItem` 从 `@Entity` 改为 `@MappedSuperclass`，使其不再占用物理表；
`registered_item` 新增 `item_id` 列承接原 `identified_item.item_id`；
`scoped_identifier.identified_item_id` 的外键改指 `registered_item.id`（列名保留）。
`Item.identifiedItem` 与 `ScopedIdentifier.identifiedItem` 的 Java 类型改为
`RegisteredItem`。

**为什么是这个做法**：元模型（类层次）保持对 ISO/IEC 11179-3:2023 §9.4.1.1 的忠实——
`Registered_Item` 仍声明 `Identified_Item` 为 Direct superclass，
`StandardInheritanceConformanceTest` 白名单与条款引用一字不动。
只有物理层收敛为一张表。若删掉 `IdentifiedItem` 类（类层合并），
§9.4.1.1 声明的父类就不存在了，那才是需要向标准辩护的偏差。

**替代方案与否决理由**：
- *删掉 `IdentifiedItem` 类，物理与类一并合并* —— 与 §9.4.1.1 直接冲突，
  须改守护测试白名单并记录标准偏差。用户明确选择"元模型不改"。否决。
- *两个 `@Entity` 共享一张表* —— JPA JOINED 继承下不可行（同一表不能承载
  继承链上的两层独立实体）。否决。

**有意的语义后果**：`Registered_Item` 为抽象类（§9.4.1.2），合并后任何带
`Scoped_Identifier` 的条目必然同时具备 `Administered_Item` 或 `Attached_Item` 实例，
「已标识但未注册」这一中间态不再可表达。该态在标准下是允许的（§7.4.1 的 0..1 +
§9.4.1.1），本变更为**用户裁定的产品决策**，已写入 specs 的说明段。

**连带影响（实施前核出，原初稿遗漏）**：`IdentifiedItemRepository` 声明为
`JpaRepository<IdentifiedItem, UUID>`，而 Spring Data 要求泛型参数为实体类型——
`IdentifiedItem` 降为 `@MappedSuperclass` 后该仓储**无法存在**。须改挂到
`RegisteredItem`（JPQL 中 `IdentifiedItem ii` 改写为 `RegisteredItem ri`），
并同步三个调用点（`ItemMappingCrudService`、`ScopedIdentifierService`、
`WorkspaceService`）的返回类型。涉及方法：`existsById`、`findItemIdById`、
`findItemConcreteTypeById`。

**迁移形态**：SQL Server 可直接 `ALTER TABLE ... ADD` + 换 FK；
SQLite 须重建 `registered_item`（建新表 → 拷数据 → 换 `scoped_identifier` 外键 → 删旧表），
迁移自带幂等守卫。

### D5：提交关联在 D1 修复并实测后才恢复

先挂关联等于主动把应用打到超时态，无法区分"修复失败"与"关联失败"。
顺序：迁移索引 → 重打包重启（空关联态基线）→ 恢复关联 → 实测三端点。

**回滚**：清空 `registered_item_submission_record`（幂等、可重跑，无数据损失）；
索引只增不减，无需回滚。

## Risks / Trade-offs

| 风险 | 缓解 |
|---|---|
| D1 改写后返回行序与原 JPQL 不一致 | 原为 `ORDER BY sr.id DESC`；实现保持同序并加用例锁定 |
| D2 改动 `buildItemMeta` 签名波及 15+ 调用点 | 新增参数为可选且默认空集合，调用点无需逐个改；列表路径统一预取 |
| 恢复关联后仍有其他慢路径（如工作空间视图的 owner 判定亦经 `sub.registeredItems`） | 恢复后实测 `data-elements` / `administered-items` / `workspace` 三端点；工作空间慢路径另立 backlog |
| 迁移在达梦未实测 | `CREATE INDEX` 语法三方言通用，风险低；沿用惯例记遗留 |
| Tab 在无受管链条目上渲染出空壳 | 以 `administeredItemId` 存在为渲染条件 |
| D6 表合并后 `getIdentifiedItem()` 返回类型变更，波及未知调用点 | 编译期全量检查（类型变更是编译错误而非静默行为变化）；迁移后按标识符反查、正反双向读取各加一条冒烟 |
| D6 迁移在 SQLite 上重建表丢外键/索引 | 迁移内显式重建 + 冒烟断言标识符绑定与索引均在 |

## Migration Plan

1. 写 `V20261009011`（sqlite / sqlserver / dm）
2. 重打包、重启应用 → 验证空关联态下列表接口正常（基线）
3. 执行提交关联回填（集合式 + 幂等）
4. 实测 `data-elements` / `administered-items` / `workspace` 三个端点亚秒级
5. 界面验收：数据元对话框「参考文档」页签展示 WS/T 363.x / 364.x 文档并可增删

真实库上的第 3、5 步需用户确认后执行。

## Open Questions

无。D1–D5 已定案；联系人口径、注册状态取值、引用关联映射已在前一变更
`repair-administered-item-closure` 确定，本变更不再触碰任何 WS/T 取值。