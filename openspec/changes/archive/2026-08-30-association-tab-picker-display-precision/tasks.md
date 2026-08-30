# 任务清单（已全部完成，回补归档）

## 1. 搜索 SQL 修复

- [x] 1.1 修复 JOIN/WHERE 片段粘连导致的 `rs.idLEFT` 语法错误（SQL_GLUE 边界）。
- [x] 1.2 移除 type 筛选分支中为无占位符 EXISTS 子查询追加的多余参数（修复 sqlite-jdbc 参数超限 `ArrayIndexOutOfBoundsException`）。
- [x] 1.3 更正 `registration_specification` → `registry_specification` 表名（`itemTypeCaseExpr` 与 `typeToTable` 两处）。
- [x] 1.4 `constraint_set_id` 改经 `registered_item` 中间表 LEFT JOIN 获取（`administered_item` 无此列）。
- [x] 1.5 `has_preferred` / `acceptabilities` 分支改为直接读取 designation/definition 表的 `acceptability` 列，不再 JOIN context 中间表。
- [x] 1.6 namespaceId 分支删除引用不存在列 `ai.namespace_id` 的 OR 条件。
- [x] 1.7 在真实 SQLite 库上逐字段验证完整搜索 SQL（命中 VD.00003 测试用例）。
- [x] 1.8 新增回归断言：占位符数与参数数一致、SQL 片段词边界、表名白名单（`MetadataSearchServiceTest`）。

## 2. 搜索性能优化

- [x] 2.1 新增 `itemTypeExprFor(typeFilter)`：带 type 时仅对请求的类型子集生成探测，单类型常量化；无 type 时保持全量 CASE 探测。
- [x] 2.2 补充单类型/多类型/无类型三种场景的单元测试。

## 3. 四实体标准关联端点

- [x] 3.1 `AssociationType` 新增 `VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN`（value_domain_meaning，§7.4.4.1，子类型一致 §7.4.5.2.1）。
- [x] 3.2 新增 `StandardAssociationType`（path 值与标准方向标注）。
- [x] 3.3 新增 `ItemAssociationController`：`GET /api/v1/items/{itemId}/associations`、`POST/DELETE /api/v1/associations/{relationType}`。
- [x] 3.4 新增 `ItemAssociationService`：查询汇集正向+反向；写操作按标准方向落库、记录版本快照、返回最新关联列表。

## 4. 关联管理界面

- [x] 4.1 新增 `ItemAssociationsPanel`：分组展示全部标准关联，去方向化（移除「被引用」概念）。
- [x] 4.2 一对多关联支持多选批量添加（`Promise.allSettled`，部分失败提示「已建立 N 个关联，M 个失败」）。
- [x] 4.3 未保存实体显示「保存后即可管理关联。」。
- [x] 4.4 值域 / 数据元概念 / 概念域列表视图接入「关联」tab 并调整分组标题。

## 5. picker 显示格式与多选

- [x] 5.1 `formatLabel`/`metaLine` 各信息段以 ` | ` 分隔。
- [x] 5.2 新增 `latestTimeLabel`：创建/最后修改取最晚者，显示 `创建于：`/`最后修改于：` 前缀。
- [x] 5.3 `SmartItemPicker` 新增 `allow-multiple` 多选模式（collapse-tags）。

## 6. 数据元精度属性

- [x] 6.1 `DataElement` 实体新增 `precision`（Integer，0..1，§7.6.2.1.4 Table 23）。
- [x] 6.2 `DataElementRequest`/`DataElementResponse` 与 `DataElementService` 双向映射贯通。
- [x] 6.3 数据元编辑对话框属性 tab 新增「精度」输入（最小值 0）。
- [x] 6.4 补充精度往返与清除的单元测试；修正既有构造调用。

## 7. 规范回补

- [x] 7.1 更新 `specs/metadata-object-association/spec.md`（关联矩阵、方向一视同仁、REST 端点、关联界面；修正解除关联场景）。
- [x] 7.2 更新 `specs/smart-item-picker/spec.md`（显示格式、多选模式）。
- [x] 7.3 新增 `specs/data-element-precision/spec.md`。
