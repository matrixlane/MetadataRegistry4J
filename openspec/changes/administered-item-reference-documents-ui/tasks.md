# administered-item-reference-documents-ui 任务清单

> 依据 `specs/`（做什么）与 `design.md`（怎么做）拆分。每项标注验证方式。
> **实施顺序即依赖顺序**：先解除阻塞（1、2），再交付界面（3、4），最后恢复数据（5）。
> 真实库写入需用户单独确认。

## 1. 解除阻塞：提交记录查询形状（TDD 红）

- [ ] 1.1 在 `SubmissionRecordRepository` 旁新增按 `registered_item_id` 取
      `submission_record_id` 的查询（经 `DatabaseEnvironment.qualify` 方言中立限定），
      并加单元测试锁定返回顺序为 id 降序——验证：测试覆盖空关联 / 单条 / 多条降序三态
- [ ] 1.2 改写 `findAllByRegisteredItemIdOrderByIdDesc`：先按 id 列取 id，再
      `findAllById` 取实体，内存按 id 降序排序；**不再经 `JOIN sr.registeredItems`**——
      验证：既有调用点（`ItemAdministrationService:943` 等）签名不变、行为不变
- [ ] 1.3 红态证据：在真实库挂满 11,182 条提交关联后跑修复前的实现，记录列表接口超时；
      再跑修复后实现——验证：修复前 >40s 超时、修复后 <1s，两组数据均记录在案

## 2. 关联表索引迁移（三方言）

- [ ] 2.1 编写 `V20261009011__Administered_item_link_indexes.sql` SQL Server 版：
      `registered_item_reference_document` 与 `registered_item_submission_record`
      各建两个 join 列索引，动态取名不硬编码（若已存在则跳过）——验证：迁移脚本内
      不出现硬编码约束/索引名
- [ ] 2.2 编写同版本号 SQLite 版：`CREATE INDEX IF NOT EXISTS`——验证：脏库与干净库
      各执行一次均成功
- [ ] 2.3 编写同版本号达梦版：全限定 `MDR.`，语法对齐 SQL Server——验证：脚本齐备
      （未实测，记遗留）
- [ ] 2.4 在 SQL Server 真实库副本上应用迁移并核对索引——验证：4 个索引存在；
      按受管项与按文档检索均走索引（`SET STATISTICS IO` 断言 logical reads 不随表规模线性增长）

## 3. 参考文档取值（后端 DTO + 批量查询）

- [ ] 3.1 新增按一批 `registered_item_id` 批量取参考文档 id 的仓储方法，返回
      `itemId → [referenceDocumentId]` 映射——验证：单元测试覆盖「无关联返回空」与
      「多条目不串扰」
- [ ] 3.2 `ItemMetaResponse` 增 `List<UUID> referenceDocumentIds`——
      验证：编译通过，既有构造调用点补默认值
- [ ] 3.3 `ItemAdministrationService.buildItemMeta` 增加可选的批量关联入参，
      按 `itemId` 分桶回填；列表路径由 Service 层统一预取后传入——验证：
      `DataElementService` 等既有调用点无需逐个改造，编译通过
- [ ] 3.4 接口实测：`GET /api/v1/data-elements?size=20`——验证：返回 20 条，
      每条的 `meta.referenceDocumentIds` 与库中关联一致，且响应耗时不随关联总数线性增长

## 4. 前端「参考文档」页签

- [ ] 4.1 `types/common.ts` 的 `meta` 补 `referenceDocumentIds`——验证：`vue-tsc` 通过
- [ ] 4.2 新建参考文档选择组件（远程搜索 `referenceDocumentApi` 多选，展示
      `identifier` + `title`）——验证：组件 spec 覆盖搜索、已选项回显、去重
- [ ] 4.3 `ItemFormDialog.vue` 在「注册记录」之后插入 `referenceDocuments` 页签，
      渲染条件为 `showRegistration && form.meta.administeredItemId`——验证：
      spec 断言有受管链条目显示该页签、无受管链条目不显示
- [ ] 4.4 保存时对比初始集合与当前集合，对差异集调用既有
      `POST` / `DELETE /administered-items/{id}/reference-documents`——验证：
      spec 断言「新增一项只发 POST」「移除一项只发 DELETE」「无变化不发请求」
- [ ] 4.5 前端 `vitest` 全量 + `npm run build`——验证：全绿、构建通过

## 5. 受管链物理表合并（元模型不变）

> 顺序说明：本组必须在第 6 组之前完成——闭环守护测试与基准脚本都引用
> `identified_item` 表，表合并后须改写；先合并会让第 1、6 组的验证口径失真。
> 但也应在第 1 组（D1 修查询）之后做：D1 的验证需要真实的关联规模。

- [ ] 5.1 `IdentifiedItem.java` 由 `@Entity` 改为 `@MappedSuperclass`（去掉
      `@Inheritance`/`@Table`），`RegisteredItem` 成为该继承链上唯一的持久化实体——
      验证：`StandardInheritanceConformanceTest` 白名单与条款引用**一字不改**仍全绿
- [ ] 5.2 `RegisteredItem` 侧承接 `item_id`；`Item.identifiedItem` 与
      `ScopedIdentifier.identifiedItem` 的类型改为 `RegisteredItem`——
      验证：编译通过（全量检查类型变更波及的调用点）
- [ ] 5.2b `IdentifiedItemRepository` 改挂 `RegisteredItem`（`JpaRepository` 泛型不能是
      mapped superclass）：JPQL 中 `IdentifiedItem ii` → `RegisteredItem ri`，
      返回类型随之变更；同步 `ItemMappingCrudService`、`ScopedIdentifierService`、
      `WorkspaceService` 三个调用点——验证：编译通过；`existsById` / `findItemIdById` /
      `findItemConcreteTypeById` 三个方法行为与合并前一致（加用例锁定）
- [ ] 5.3 编写 `V20261009012__Merge_identified_into_registered_item.sql` SQL Server 版：
      `registered_item` 增 `item_id` 列 → 由 `identified_item` 回填 → 重指
      `scoped_identifier` 外键 → 删旧表，带幂等守卫——验证：迁移脚本在已合并库上重跑为 no-op
- [ ] 5.4 编写同版本号 SQLite 版：重建 `registered_item`、换外键、删旧表，
      保留索引——验证：脏库与干净库各执行一次均成功
- [ ] 5.5 编写同版本号达梦版：语法对齐 SQL Server——验证：脚本齐备（未实测，记遗留）
- [ ] 5.6 改写 `AdministeredItemClosureConformanceTest` 的不变式查询为合并后的表结构，
      **判定口径不变**——验证：红→绿证据重跑一遍（人工构造缺环记录仍能被报出）
- [ ] 5.7 更新 `db-baseline/ws363-364-administered-item-*.sql` 与
      `ws363-364-reference-link-sqlserver.sql` 中对 `identified_item` 的引用——
      验证：脚本中不再出现 `identified_item`
- [ ] 5.8 合并后冒烟：按标识符反查注册项、从条目侧与注册项侧双向读取——
      验证：三者指向同一对对象，与合并前结果一致

## 6. 恢复提交关联（需用户确认后执行）

- [ ] 6.1 备份真实库到 `scratch/`——验证：备份文件存在且可读
- [ ] 6.2 重打包并重启应用，验证**空关联态**下列表接口正常（基线）——
      验证：`data-elements` / `administered-items` 亚秒级返回
- [ ] 6.3 执行提交关联回填（集合式 + 幂等，11,182 条）——验证：关联行数 = 受管项行数
- [ ] 6.4 实测三端点——验证：`data-elements` / `administered-items` / `workspace`
      均亚秒级；`workspace` 若仍慢则另立 backlog 并如实记录
- [ ] 6.5 界面验收：数据元对话框「参考文档」页签展示 WS/T 363.x / 364.x 文档并可增删——
      验证：截图或接口返回确认

## 7. 提交前检查

- [ ] 7.1 `detect-changes`（`node .gitnexus/run.cjs detect-changes --scope all --repo .`）
      ——验证：无 `partial`、无 `truncated`
- [ ] 7.2 后端全量回归，与既有基线失败集对比——验证：无新增失败
- [ ] 7.3 `openspec validate administered-item-reference-documents-ui`——
      验证：valid
- [ ] 7.4 `openspec validate --all`——验证：无本次引入的失败
- [ ] 7.5 归档记录：`.superpowers-memory/` 会话记录 + `CURRENT_STATE.md`，
      并把「关联表收紧 UNIQUE 约束」与「workspace owner 判定慢路径」记入
      `LEARNING_BACKLOG.md`——验证：两条 backlog 均存在