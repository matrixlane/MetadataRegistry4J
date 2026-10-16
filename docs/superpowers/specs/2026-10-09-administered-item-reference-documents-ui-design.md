# 受管项参考文档展示与提交关联查询修复设计

日期：2026-10-09
状态：待确认（用户确认后进入 OpenSpec 工件）
变更名（OpenSpec）：`administered-item-reference-documents-ui`

## Context

见 `openspec/changes/repair-administered-item-closure/`——本变更是它的后续，
在同一批 WS 363/364 基准数据上继续工作。本节只列实现所需的现状事实。

### 数据现状（真实库 `metadata_registry`，已应用并验证）

| 项 | 值 |
| --- | --- |
| 注册状态 | `STANDARD` 11,184 条（WS/T 363.2–363.17 §4.1「注册状态 标准状态」） |
| 注册机构 | 国家卫生健康标准委员会卫生健康信息标准专业委员会，11,181 条 |
| 主管记录 | 1 条，contact = 委员会注册管理员，已挂 11,182 |
| 提交记录 | 1 条，contact 同上，**关联 0 条** |
| 引用关联 | `registered_item_reference_document` 23,449 条，覆盖 11,181 条目 |
| 闭环 | 未闭环 0 |

### 阻塞项：提交关联一挂上，列表接口即超时

`registered_item_submission_record` 挂 11,182 行后，`/api/v1/data-elements` 与
`/api/v1/administered-items` 均 40s+ 超时；清空即恢复 0.35s。
**同在位的 23,449 条引用关联与 11,182 条主管关联不影响**，故为单一根因。

调用链：`ItemAdministrationService.buildItemMeta`（`:943`，**列表响应逐条调用**）
→ `SubmissionRecordRepository.findAllByRegisteredItemIdOrderByIdDesc`：

```java
SELECT DISTINCT sr FROM SubmissionRecord sr JOIN sr.registeredItems ri WHERE ri.id = :itemId
```

`JOIN sr.registeredItems` 需实例化 `RegisteredItem`——它是 `IdentifiedItem` 下
30+ 子类的 JOINED 继承根，Hibernate 必须把全部 Item 子类表拉进同一条 SQL。
列表里逐条调用 → N+1 × 重 join。

> 该查询的原注释已写明 *"`registered_item_submission_record` 无 UNIQUE 约束"*，
> 当年即踩过这张表，只是当时表为空、查询瞬间返回，问题未暴露。

### 既有资产（可直接复用，不需新建）

- `POST /api/v1/administered-items/{id}/reference-documents`（增）
- `DELETE /api/v1/administered-items/{id}/reference-documents`（删）
- `AdministeredItemResponse.referenceDocumentIds`（注册项列表已暴露）
- `ReferenceDocumentController` + `ReferenceDocumentResponse`（文档 CRUD）
- `DatabaseEnvironment.qualify(...)`：既有的方言中立表名限定模式

### 缺口

1. `ItemMetaResponse` **没有** `referenceDocumentIds`——数据元对话框走这个 DTO，
   前端拿不到当前条目的参考文档。
2. `ItemFormDialog` 没有参考文档 Tab。
3. 两张关联表**原本无任何索引**（已手工补 4 个，未进迁移）。

## Goals / Non-Goals

### Goals

- 修掉提交关联查询的继承展开，使 11,182 条关联下列表接口保持亚秒级。
- 数据元对话框能展示与编辑本条目的参考文档（增删）。
- 关联表索引以迁移脚本形式固化（三方言）。
- 修复后恢复提交关联。

### Non-Goals

- 不改参考文档自身的 CRUD（已存在）。
- 不改「注册记录」Tab（提交/主管记录的只读展示已存在）。
- 不动实体继承映射（爆炸半径过大）。
- 不改 WS/T 数据的任何取值。

## Decisions

### D1：提交记录查询改走 JdbcTemplate + 方言中立表名限定

**决策**：`findAllByRegisteredItemIdOrderByIdDesc` 改为经
`DatabaseEnvironment.qualify()` 取关联表列出的 `submission_record_id`，
再 `findAllById` 取实体。

**依据**：`RegisteredItem` 的 JOINED 继承展开是开销来源；只按 id 列查关联表
完全不触碰继承映射。项目已有 `DatabaseEnvironment.qualify` + JdbcTemplate 的
方言中立模式（`ContextTestDataInitializer:127`、`DatabaseCleaner:185`、
`DmFkConstraintStrategy:47`）。

**替代方案**：
- *原生 `@Query` 带 schema* —— 方言硬编码，违反 `database-portability`
  「业务代码方言中立」。否决。
- *`@BatchSize` / fetch join* —— 不改变继承展开本身，只把 N 次变 1 次，
  单次仍是重 join；且列表逐条调用路径未批处理化。否决。
- *重构实体继承为单表* —— 影响全部 Item 子类。否决。

### D2：`ItemMetaResponse` 增加参考文档 id 列表，按页批量取

**决策**：`ItemMetaResponse` 增 `List<UUID> referenceDocumentIds`；
取值走**按页批量查询**（一次传入本页全部 `registered_item_id`），
不在 `buildItemMeta` 内逐条触发懒加载。

**依据**：`buildItemMeta` 在列表响应中被逐条调用，任何逐条懒加载都会重演
提交记录那个 N+1。批量查询保持"每页 1 次"。

**替代方案**：*直接读 `administeredItem.getReferenceDocuments()`* ——
`@ManyToMany` 默认 LAZY，逐条访问即 N+1，与被修的 bug 同型。否决。

### D3：前端新增「参考文档」Tab，保存走既有增删端点

**决策**：`ItemFormDialog` 在「注册记录」之后新增 Tab，列表展示
`identifier` + `title`，支持增删；保存时对差异集调用既有
`POST` / `DELETE /administered-items/{id}/reference-documents`。

**依据**：后端已具备完整能力，自造端点会重复且造成契约分叉。
Tab 用既有 `el-tab-pane` + `hasTabError` 错误标记模式。

**替代方案**：*随表单整体提交* —— 编辑表单的 payload 是 `ItemMetaRequest`，
混入关联集合会把 `ItemFormDialog` 的通用契约与受管项专属能力绑死，
且 15+ 条目类型共用该对话框。否决。

### D4：索引以三方言迁移固化，版本号 `V20261009011`

**决策**：新增 `V20261009011`（接续 `V20261009010`），为
`registered_item_reference_document` 与 `registered_item_submission_record`
各建 join 列索引；SQLite 不支持 `ALTER TABLE ADD CONSTRAINT`，
但索引走 `CREATE INDEX IF NOT EXISTS` 三方言均可。

**依据**：手工建的 4 个索引必须在迁移里固化，否则换环境重建库即丢失。
关联表为 `@ManyToMany` 关联表，语义上 `(两侧列)` 应有 UNIQUE；本次先建
非唯一索引解除扫描，是否收紧为 UNIQUE 需先清理历史重复行，另行判定。

**替代方案**：*一并加 UNIQUE 约束* —— 现有数据未验证无重复，
贸然加约束会在存量库上失败。否决（记 backlog）。

### D5：提交关联在 D1 修复并验证后才恢复

**决策**：D1 完成并实测列表接口恢复亚秒级后，再执行关联回填
（复用 `db-baseline` 下的补齐脚本形态，集合式 + 幂等）。

**依据**：先挂关联等于主动把应用打到超时态，无法区分是修复失败还是关联失败。

## Risks / Trade-offs

| 风险 | 缓解 |
|---|---|
| D1 改查询后返回行序与原 JPQL 不一致 | 原为 `ORDER BY sr.id DESC`；新实现保持同序并加用例锁定 |
| 批量取参考文档使 `buildItemMeta` 签名变化 | 批量结果按 `itemId` 分桶传入，保持纯函数形态；既有 15+ 调用点不逐个改 |
| 迁移在达梦未实测 | 沿用项目既有惯例记遗留（`CREATE INDEX` 语法三方言通用，风险低） |
| 恢复提交关联后仍有其他慢路径（如工作空间视图） | 恢复后对 `data-elements`、`administered-items`、`workspace` 三端点一并实测 |

## Migration Plan

1. `V20261009011`（索引）→ 重启即应用
2. 重打包并重启应用，验证空关联态下列表接口正常
3. 恢复提交关联（幂等脚本）
4. 实测三个端点亚秒级
5. 真实库收尾操作需用户确认后执行

**回滚**：提交关联回滚为清空 `registered_item_submission_record`（幂等、可重跑）；
索引可保留（只增不减）。均无数据损失。

## Open Questions

无。D1–D5 已定案；联系人口径、注册状态取值、引用关联映射均已在前一变更确定，
本变更不再触碰。