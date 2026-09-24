# 实现计划：item-mapping-clause11-rebuild

日期：2026-09-20
依据：设计稿 `docs/superpowers/specs/2026-09-20-item-mapping-clause11-rebuild-design.md`（方案 A）+ OpenSpec `openspec/changes/item-mapping-clause11-rebuild/`（proposal/design/specs/tasks，已确认）。用户 2026-09-20 裁定：映射订阅功能整体移除。

## 执行顺序

1. **阶段 1+2 移除**（tasks 1.1–2.3）：删除 MappingScheme 全栈 + 映射订阅全栈 + FHIR scheme 孤儿引用；`CaseInsensitiveEnumConfig`/`DatabaseEnvironment`/`orm-team.xml` 清理；`V20260920001__Drop_mapping_scheme.sql` 三方言。验证：两模块编译 + 相关测试删除后套件通过。
2. **阶段 3 重建后端**（tasks 3.1–3.5，TDD）：`MappingDegree` 14 值枚举 → `ItemMapping extends Item` + subject/object 关联类 + `MappingHierarchy` → service 适配 → web CRUD Service/Controller/DTO → 校验（存在性、notation 条件必填、防环）→ `ensureAdministeredItem` 集成。
3. **阶段 4 重建迁移**（tasks 4.1–4.3）：`V20260920002__Rebuild_item_mapping_clause11.sql` 三方言。验证：全新库重放 + 现有库迁移。
4. **阶段 5 前端**（tasks 5.1–5.3）：删两页面 + scheme api/路由/i18n；新增 `ItemMappingList.vue` + `itemMappingApi`；菜单/e2e。
5. **阶段 6 收尾**（tasks 6.1–6.3）：openapi.json、README、ARCHITECTURE.md（GitNexus 重新生成）、全量验证（后端测试、前端 build/e2e、smoke）、detect-changes、记忆更新。

## API 契约（前后端对接基准）

- 基础路径：`/api/v1/item-mappings`
- `GET /api/v1/item-mappings`：分页参数 `page/size/sort`；过滤 `subjectItemId`、`objectItemId`、`degree`（标准小写值，如 `related to`，见 11.6.1 Table 35）。响应页 `{content:[ItemMappingResponse], totalElements, ...}`
- `POST /api/v1/item-mappings`：体 `{subjectItemId, subjectVariableName?, objectItemId, objectVariableName?, degree, description?, transformationRule?, transformationRuleNotation?, registration?:{identifier?, namespaceId?, designation?, definition?}}`
- `GET /api/v1/item-mappings/{id}`、`PUT /api/v1/item-mappings/{id}`（同 POST 体 minus registration）、`DELETE /api/v1/item-mappings/{id}`
- `GET /api/v1/item-mappings/{id}/hierarchy` → `{superset: ItemMappingSummary|null, subsets:[ItemMappingSummary]}`
- `PUT /api/v1/item-mappings/{id}/hierarchy`：体 `{supersetMappingId: UUID|null}`（null=清除）
- ItemMappingResponse：`{id, degree, description, transformationRule, transformationRuleNotation, subject:{itemId, itemName, variableName}, object:{...}, registrationStatus?, name?}`
- 错误：条目不存在/校验失败 → 400 + message；notation 缺 → 400；层级成环 → 400。

## 关键约定

- `Item` 关联全部 `FetchType.LAZY`（JOINED 根陷阱）。
- degree 全链路标准小写值（评审修正 2026-09-20）：JPA 经 `MappingDegreeConverter` 落库、`@JsonValue`/`@JsonCreator` 序列化、`WebMvcConfig` 查询参数转换器；`CaseInsensitiveEnumConfig` 不再注册。
- 迁移遵循既有先例：sqlite 无守卫直写；sqlserver/dm schema 限定 + INFORMATION_SCHEMA 守卫。
- 既有未提交变更属于其他 change，不触碰；本 change 文件独立成组。
