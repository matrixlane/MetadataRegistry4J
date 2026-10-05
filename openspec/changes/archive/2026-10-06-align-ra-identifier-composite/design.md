# Design: align-ra-identifier-composite

## Context

`Registration_Authority_Identifier`（ISO/IEC 11179-3:2023 §6.3.9）为复合标识：international_code_designator（1，必备）、organization_identifier（1，必备）、organization_part_identifier（0..1）。实体 `RegistrationAuthorityIdentifier` 中 icd/orgId 已是 `@Column(nullable=false)`，但创建对话框只采集 orgId，icd 走向导缺省/为空，空值落入实体非空列产生 500。

## Decisions

- **应用层校验**（项目先例 decision-2026-09-13-valid-period-app-layer）：DTO `@NotBlank` 两处必填，不迁移 DB 列。
- **不做数据回填**：Hibernate 生成的实际库列本为非空，不存在历史 null 行；基线 SQL 文件的可空定义仅作参考对齐（后续基线维护时再统一，超出本次范围）。
- **OPI source 不进 UI**：向导亦不暴露，保持 API 字段不变。

## Risks / Trade-offs

- [旧客户端/脚本未传 ICD] → 由 400 校验错误替代 500，语义更明确，属可接受破坏。
