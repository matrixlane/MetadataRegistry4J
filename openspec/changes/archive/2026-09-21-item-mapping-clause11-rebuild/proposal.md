# 提案：移除"映射方案"并按 ISO/IEC 11179-3:2023 第 11 章重建 Item_Mapping

## Why

"映射方案"(Mapping Scheme) 不是 ISO/IEC 11179-3 的概念。经 Milvus 对标准全文检索核实：第 11 章为 "Item_Mapping package"，含 Item_Mapping（Item 子类）、Subject_Mapping/Object_Mapping 关联类、mapping_hierarchy 自反关联与 14 值 degree 枚举；全文无 Mapping_Scheme / mapping scheme。当前实现的 MappingScheme（版本 lineage + DRAFT/PUBLISHED/ARCHIVED 状态机）是自创容器，且 2026-09-01 审计（`docs/audits/iso11179-clause11-itemmapping-audit-2026-09-01.md`）已确认现行映射模型不符合标准元模型，用户决策"归 MDR 并立项重建"，遵从目标 "Basic Registry with mapping"。当前所有映射相关表为空，是彻底重建的最佳时机。

## What Changes

- **BREAKING** 移除"映射方案"全部功能：后端实体/仓储/服务/控制器/DTO/枚举、前端 `MappingSchemeList.vue` 与 `MappingDiffViewer.vue` 两页面、`/mapping-schemes*` 全部 REST 端点、menu 入口、`mapping_scheme` 数据库表（ext schema）。
- **BREAKING** 移除仅服务方案的功能：映射质量报告（`GET /mapping-schemes/{id}/quality/report`）、方案版本 diff（`/{id}/versions`、`/{id}/diff`）。
- **BREAKING** 移除映射订阅功能整体：`SubscriptionType`（SCHEME/ITEM）、`mapping_subscription` 表及 subscribe/unsubscribe/list 端点。订阅能力后续作为更全面的独立功能重新设计（用户决策 2026-09-20）。
- **BREAKING** 移除 subject/object 的 externalUri 扩展：标准 11.4 要求映射双方为注册 Item；外部标准互操作由 ext 侧 `fhir_element_mapping`、`interop_mapping` 独立承担。
- **BREAKING** `item_mapping` 表按第 11 章重建：`mapping_type`（自创 4 值）替换为标准 14 值 `degree`；新增 `transformation_rule_notation`；删除 `mapping_scheme_id`、`created_at`；主键转为 `item` 表外键（Item 子类化）。
- **BREAKING** `item_mapping_subject` / `item_mapping_object` 重建为标准关联类形态：JPA 关联注册 Item + `mapping_variable_name`，删除 `itemId` 裸 UUID 与 `externalUri` 字段。
- 新增 `mapping_hierarchy` 表与实体：superset/subset 映射层级（标准 11.5），应用层防环校验。
- 新增 `ItemMappingController`（`/api/v1/item-mappings`）：映射条目分页查询、创建（可选携带注册请求走统一注册管线）、更新、删除、层级维护。
- 新增前端"项目映射"管理页（`/item-mappings`）：列表、筛选、新建/编辑（subject/object 条目选择、degree 下拉、变量名、转换规则与记法、描述）。
- 移除 `fhir_element_mapping.mapping_scheme_id` 孤儿列（无 FK、无调用方）。
- 保留不动：映射网络图（`/mappings/network`）、影响分析、interop 匹配结果管理。映射订阅功能整体移除（含 `mapping_subscription` 表），后续作为独立功能重新设计。

## Capabilities

### New Capabilities

- `item-mapping`: ISO/IEC 11179-3:2023 第 11 章 Item_Mapping 包的标准能力——映射条目作为 Item 子类被标识/注册/管理，subject/object 关联注册 Item 并携带 mapping_variable_name，14 值 degree，transformation_rule(+notation)，mapping_hierarchy 层级维护，以及 REST API 与界面管理。

### Modified Capabilities

- `standardize-menu-layout`: "项目映射"菜单项的目标由映射方案工作区 `/mapping-schemes` 变更为项目映射管理页 `/item-mappings`。

## Impact

- **后端模块**：`metadata-registry-item-mapping`（实体/枚举/仓储/服务重建与删除）、`metadata-registry-web`（控制器/DTO/服务/配置删除与新增）。
- **数据库**：新迁移脚本 2 个（`V20260920001` 删 mapping_scheme 及相关列、`V20260920002` 重建 item_mapping 三表 + 新增 mapping_hierarchy），覆盖 sqlite/sqlserver/dm 三方言。
- **前端**：删除 2 页面 + `mappingSchemeApi`；新增 `ItemMappingList.vue` + `itemMappingApi`；路由、菜单、i18n（zh-CN/en-US）更新。
- **API 文档**：`docs/technical/api/openapi.json` 删除 `/mapping-schemes*` 端点、新增 `/item-mappings*`。
- **文档**：README.md 页面清单、`docs/architecture/ARCHITECTURE.md`（重新生成）、审计报告关联说明。
- **遵从声明**：注册表 profile 目标由 "Basic Registry" 转向 "Basic Registry with mapping"（标准 4.4.2）。
