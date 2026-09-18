# Design: add-individual-identifier

## Context

参见 proposal.md 与 `docs/superpowers/specs/2026-09-18-individual-identifier-design.md`。关键约束：`IndividualList.vue` 的 identifier 列与必填项在 WI-004 已存在但后端无对应字段（HEAD 状态即如此）；`identifier` 非 ISO/IEC 11179-3 Individual 标准属性，属应用扩展。

## Goals / Non-Goals

**Goals:** 端到端补全 identifier（实体→DTO→服务→UI）；留空自动生成；列表可搜索；Registrar 名称可展示。

**Non-Goals:** 唯一约束与并发分配策略；存量数据批量清洗；与 administered item scoped identifier 体系合并。

## Decisions

- **可空列 + 应用层生成**（非 DB 默认值/非触发器）：兼容全部存量空值并惰性回填；生成规则分两处——管理端 `individual-{hex8}`（`IndividualService.generateIdentifier`），资料页 `{username}-individual`（来源可追溯）。
- **不建唯一索引**：与既有数据零冲突；唯一性需要出现时再议。
- **Registrar 回填放在 `updateMyIndividual` 成功后**：只补空引用、不覆盖已有；与联系人（Contact）既有回填逻辑同位置、同风格。
- **名称渲染修正**：后端 `IndividualResponse.name` 本来就是扁平字符串，UI 误写成 `row.name?.sign`，一并修正。

## Risks / Trade-offs

- [并发创建可能生成重复 identifier] → 本期不保证唯一；冲突概率低且字段仅展示/搜索用途。
- [SQLite 下 Flyway 迁移与 ddl-auto 双重建列] → 项目既定 SQLite 策略即"ddl-auto 拥有 schema"，迁移脚本保持一致性声明，重复建列幂等无害。

## Migration Plan

1. 随版本发布 Flyway 迁移（SQL Server/达梦）；SQLite 由 ddl-auto 补列。
2. 存量行 identifier 为空，首次保存时惰性回填；无需停机。

## Open Questions

（无。）
