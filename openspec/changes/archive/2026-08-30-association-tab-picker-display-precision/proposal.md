# 变更提案：四实体关联管理界面、picker 显示格式与数据元精度（回补归档）

> 本变更为实施完成后的回补归档：代码已全部落地并验证，本提案用于补齐 openspec 记录。

## Why

1. SmartItemPicker 的关联实体搜索在真实数据库上持续报错：`MetadataSearchService` 生成的原生 SQL 存在五层连环缺陷（JOIN 片段粘连、参数超限、表名错误、引用不存在的列），而既有单元测试全部 mock 了 `JdbcTemplate`，从未执行真实 SQL，导致缺陷漏网。
2. 值域 / 数据元概念 / 概念域表单中以「被引用」方式呈现的反向关联不符合使用习惯：用户要求所有对其他实体的关联一视同仁，正向与反向是同一回事；同时缺少处理一对多关联（如一个值域关联多个数据元）的批量添加界面。
3. picker 候选项显示内容不清晰：缺少分隔符，创建时间与最后修改时间同时出现造成冗余。
4. 数据元缺少精度（precision）属性——ISO/IEC 11179-31:2023 §7.6.2.1.4 Table 23 定义的可选属性。
5. 搜索在已带 type 筛选时仍对全部 21 种实体表做 CASE 探测，存在不必要的性能开销。

## What Changes

- **修复搜索 SQL**（`MetadataSearchService`）：修复 JOIN 片段空格边界、移除 EXISTS 子查询的多余参数、更正 `registry_specification` 表名、`constraint_set_id` 改经 `registered_item` 中间表获取、`acceptability`/`has_preferred` 改由 designation/definition 表自身读取、namespace 过滤改经 `scoped_identifier`；新增回归断言（占位符数与参数数一致、SQL 片段边界、表名白名单）。
- **搜索优化**：带 type 筛选时 `item_type` 表达式仅对请求的类型子集做探测（单类型直接常量化），不再探测全部实体表。
- **四实体标准关联端点**：新增 `GET /api/v1/items/{itemId}/associations`（汇集正向+反向）与 `POST/DELETE /api/v1/associations/{relationType}`；`relationType` 取 `data-element-meaning` / `data-element-domain` / `value-domain-meaning` / `data-element-concept-domain`；`AssociationType` 新增 `VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN`（value_domain_meaning，§7.4.4.1，子类型一致 §7.4.5.2.1）。
- **关联管理界面**：数据元 / 数据元概念 / 概念域 / 值域四个编辑对话框新增「关联」tab（`ItemAssociationsPanel`），去方向化（不再出现「被引用」），一对多关联支持多选批量添加，未保存实体提示先保存。
- **picker 显示格式**：候选项与选中态标签各信息段以 ` | ` 分隔；时间取创建时间与最后修改时间中较晚者，显示为 `创建于：YYYY-MM-DD HH:mm` 或 `最后修改于：YYYY-MM-DD HH:mm`；SmartItemPicker 新增 `allow-multiple` 多选模式。
- **数据元精度**：`DataElement` 实体、`DataElementRequest`/`DataElementResponse`、Service 双向映射与数据元编辑对话框新增 `precision` 属性（0..1，Integer）。

## Capabilities

### New Capabilities

- `data-element-precision`：数据元精度属性的存储、API 贯通与界面编辑。

### Modified Capabilities

- `metadata-object-association`：补充四实体标准关联集合与基数约束、方向一视同仁原则、关联查询/维护 REST 端点、关联管理界面要求；修正「解除不存在的关联不抛异常」的旧场景（实现为抛出 `MetadataAssociationValidationException`）。
- `smart-item-picker`：候选项显示格式改为 ` | ` 分隔且时间取最晚者；新增多选模式要求。搜索 SQL 修复与探测优化不改变既有规范描述的外部行为，仅恢复/优化实现。

## Impact

- 影响模块：`metadata-registry-web`（搜索服务、关联端点、数据元 DTO/Service）、`metadata-registry-data-specification`（`DataElement.precision`、`AssociationType`）、`metadata-registry-ui`（`ItemAssociationsPanel`、四个列表视图、`SmartItemPicker`、`DataElementList`）。
- 数据模型：`data_element` 表新增 `precision` 列（可空 Integer）；其余关联复用既有外键/连接表。
- API：新增 3 个端点；`/metadata/search` 行为修复为符合既有规范，不破坏兼容。
- 标准依据：关联集合与基数、precision 属性均以 ISO/IEC 11179-31:2023 原文为准（经 Milvus 标准库核实）。
