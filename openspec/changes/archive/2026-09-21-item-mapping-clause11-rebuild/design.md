# 设计：Item_Mapping 第 11 章重建与映射方案移除

详细设计草稿见 `docs/superpowers/specs/2026-09-20-item-mapping-clause11-rebuild-design.md`（含方案 A/B/C 对比与取舍）。本文档聚焦技术决策与迁移路径。

## Context

- 标准依据（Milvus 原文核实）：ISO/IEC 11179-3:2023 第 11 章 "Item_Mapping package"。Item_Mapping 超类为 Item（11.3.1）；Table 32 属性 degree/description/transformation_rule/transformation_rule_notation；Subject_Mapping/Object_Mapping 关联类携带 mapping_variable_name（11.4）；mapping_hierarchy 自反关联（11.5）；Item_Mapping_Degree 14 值（11.6，Table 35）。全文无 Mapping_Scheme。
- 条目基础设施：`Item` 为抽象 JOINED 根（表 `item`），条目类型 `extends Item` 共享主键；注册信息由 `AdministeredItem` 外壳承载；统一注册入口 `ItemAdministrationService.ensureAdministeredItem`。
- 现状：所有映射相关表为空（无数据迁移负担）；DB 层无指向 mapping_scheme 的外键；`ItemMapping.mappingScheme` 为 JPA 硬耦合。
- 约束：`Item` JOINED 根子类众多，所有指向 `Item` 的关联必须 `FetchType.LAZY`（否则多态连接超出 SQLite FROM 项上限）。

## Goals / Non-Goals

**Goals**

- 彻底移除 MappingScheme 前后端代码、`mapping_scheme` 表及仅服务它的质量/diff/SCHEME 订阅功能。
- 按第 11 章元模型重建：ItemMapping 子类化 Item、标准关联类形态的 subject/object（含 mapping_variable_name）、14 值 degree、transformation_rule_notation 条件必填、mapping_hierarchy。
- 提供 `/api/v1/item-mappings` REST API 与前端项目映射管理页。
- 三方言（sqlite/sqlserver/dm）迁移脚本与既有约定一致。

**Non-Goals**

- 不改动 interop 匹配结果管理、FHIR 元素映射本体（仅删其 scheme 孤儿列）、映射网络图、影响分析。映射订阅功能整体移除（用户决策 2026-09-20：后续作为更全面的独立功能重新设计）。
- 不为映射条目新建专用注册状态端点（复用统一条目注册管线）。
- 不实现 degree 值列表的自定义扩展管理界面（标准允许扩展，留待后续）。
- 不做旧数据迁移（映射表均为空，物理重建即可）。

## Decisions

### D1：一次性"移除 + 重建"（方案 A），而非分阶段

彻底移除与标准重建在同一 change 内完成。备选：B（仅界面移除，后端保留废弃）会留下非标准模型，违背用户"重新回归 Item_Mapping 来设计功能"的当次指令；C（拆两个 change）流程开销翻倍且当前映射功能除方案页外本就无可触达 UI，真空期无实际收益。所有映射表为空，一次性重建代价最低。

### D2：ItemMapping 以 JOINED 继承子类化 Item

`ItemMapping extends Item`，子表 `item_mapping` 主键兼 FK → `item`。备选：独立实体 + 裸 item_id 列（现状模式）不符合标准"Item_Mapping 直接超类为 Item"，无法获得命名/定义/注册能力。风险：JOINED 根多态查询开销 → 所有 Item 关联强制 LAZY，网络图用定向查询。

### D3：subject/object 严格引用注册 Item，删除 externalUri

标准 11.4 + Annex B 示例要求映射双方为注册 Item。外部标准（FHIR 等）互操作由 ext 侧 `fhir_element_mapping`、`interop_mapping` 独立承担，不经过 item_mapping。删除 `createItemToExternalMapping` 等 API 与对应测试。应用层仅校验被引用 Item 存在（不强制 AdministeredItem 外壳，与标准 NOTE 1 一致：Item_Mapping 可在注册流程完成前创建、之后被 administered）。

### D3b：subject/object 为 1..*（忠实 11.4 多对多关联类，评审裁定 2026-09-20）

初版 MVP 为每映射单主体/单客体（关联表加 item_mapping_id 唯一约束）。用户裁定按原标准忠实实现：Item 与 Item_Mapping 为多对多，Subject_Mapping/Object_Mapping 关联类每行绑定一个条目并携带 mapping_variable_name（忠实 Annex B isodate_mapping1 三主体一客体示例）。落实：去掉关联表唯一约束（新迁移 V20260920003）；`ItemMapping.subjects/objects` 为 `@OneToMany` 集合；API 的 subject/object 为端点列表（各 ≥1，应用层校验非空）；映射网络/影响分析按映射涉及的全部条目展开。

### D4：degree 存标准小写值，全链路与原文档枚举值一致（评审修正）

初稿沿用项目既有约定（如 RegistrationStatus 存 Java 常量名）。**评审裁定（2026-09-20，用户）**：取值英文用小写、空格、不加下划线，与 ISO/IEC 11179-3:2023 Table 35 原文枚举值一致（如 `related to`）。据此修订：`MappingDegree` 每常量携带 `standardValue`；JPA 经 `MappingDegreeConverter`（autoApply）落库标准小写值（参照 AcceptabilityConverter 先例，从 `CaseInsensitiveEnumConfig` 移除）；JSON 经 `@JsonValue`/`@JsonCreator` 走标准值；查询参数经 `WebMvcConfig` 注册的 `MappingDegree::fromStandardValue` 转换器解析（兼容历史大写枚举名）；迁移 CHECK 约束列标准小写值。旧 `MappingType` 删除。

### D5：迁移分两脚本

`V20260920001__Drop_mapping_scheme.sql`：删表/删列/清孤儿；`V20260920002__Rebuild_item_mapping_clause11.sql`：item_mapping 三表重建 + mapping_hierarchy 新表。分开使回滚定位更容易（SQLite 无 DDL 回滚，回滚=还原备份）。sqlserver/dm 用 schema 限定 + INFORMATION_SCHEMA 存在性守卫；sqlite 直写（3.35+ 支持 DROP COLUMN，不用 IF EXISTS 守卫，遵循 V20260727001/V20260901008 先例）。

### D6：质量/diff/映射订阅随方案整体移除

质量报告与版本 diff 仅服务 MappingScheme 且无其他下游消费者（已核实：前端无 quality 调用方、diff 仅方案页使用）。映射订阅（SCHEME + ITEM）整条通知分发链路未接线、无前端 UI、无生产调用方；用户 2026-09-20 裁定整体移除，`mapping_subscription` 表随 `V20260920001` 一并删除，订阅能力后续作为更全面的独立功能重新设计。

## Risks / Trade-offs

- [JOINED 根多态查询超 SQLite FROM 项上限] → Item 关联全部 LAZY；网络图查询做定向 fetch；测试覆盖。
- [达梦方言未实测] → 脚本按 sqlserver 结构类比编写并标注待实测（KNOWN_FAILURES 已有同款遗留条目，不阻塞）。
- [删除 externalUri 切断未来外部映射预期] → 设计文档明示取舍；外部互操作走 ext 独立链路，需求重现时另行设计。
- [前端产物残留旧页面 chunk] → 重新构建并随签入更新 `metadata-registry-web/src/main/resources/static/`。

## Migration Plan

1. 后端代码删除/重建与迁移脚本同步开发（TDD）。
2. 全新 SQLite 库：baseline 全量重放 + 两个新脚本，验证建表结果。
3. 现有 `metadata_registry.db`：应用启动 Flyway 自动迁移，验证旧表已重建、新表存在、mapping_scheme 消失。
4. 前端构建 + e2e 菜单断言更新。
5. 端到端 smoke：新建命名空间 → 两个数据元 → 创建 Item_Mapping（same as）→ 注册流转 → 网络图可见。
6. 回滚策略：无 DDL 事务回滚（SQLite）；回滚 = 代码 revert + 数据库还原备份；空表状态使重放代价为零。

## Open Questions

无。degree 值展示文案、条目选择器复用细节属实现层决策，可在实现中确定。
