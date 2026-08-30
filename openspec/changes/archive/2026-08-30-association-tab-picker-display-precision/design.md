# 设计说明

## 关联方向模型

ISO/IEC 11179-31:2023 对四实体关联定义了固定的「源 → 目标」方向（如 data_element_domain 为 Data_Element 0..* → 0..1 Value_Domain）。写操作必须按标准方向落库（外键/连接表维护在固定的拥有方）；但查询与展示不区分方向——同一关联从任一端看都是同一个事实。因此：

- `StandardAssociationType` 枚举显式标注每个关联的标准方向与基数，`fromPath` 解析 REST path 值。
- `ItemAssociationService.listAssociations` 以焦点实体为中心，同时汇集其作为 source 与 target 的关联实例。
- 前端 `ItemAssociationsPanel` 按目标实体类型分组展示，不出现「正向/反向/被引用」字样。

## 关联集合（以标准原文为准）

| 关联 | 标准条款 | 基数 | 拥有方 |
| --- | --- | --- | --- |
| data_element_meaning | §7.2.3.3 / §7.6.3.2 | DE 0..* → 0..1 DEC | DataElement |
| data_element_domain | §7.2.3.2 / §7.6.3.1 | DE 0..* → 0..1 VD | DataElement |
| value_domain_meaning | §7.2.3.1 / §7.4.4.1 | VD 0..* → 0..1 CD | ValueDomain（子类型一致 §7.4.5.2.1） |
| data_element_concept_domain | §7.2.3.4 | DEC 0..* ↔ 0..* CD | 连接表 |

## 搜索 SQL 修复的教训

既有 `MetadataSearchServiceTest` 全部 mock `JdbcTemplate`，从不执行真实 SQL，导致五层 SQL 缺陷（片段粘连、参数计数、错误表名、不存在的列）全部漏网。回归防线改为断言生成 SQL 的结构不变量（占位符数==参数数、片段词边界、表名白名单），并在真实 SQLite 库上人工验证。

## 带 type 时的探测优化

JPA JOINED 继承无 discriminator 列，`item_type` 原本需对 21 种实体表做 CASE WHEN EXISTS 探测。当请求已带 type 筛选时，WHERE 已把结果锁定在请求类型内，SELECT 中的 `item_type` 表达式只需覆盖该子集：单类型直接常量化，多类型仅对子集生成 CASE，无 type 时保持全量探测。
