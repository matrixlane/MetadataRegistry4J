# Proposal: cleanup-stale-legacy-tables

## Why

mdr-standard-compliance-cleanup（V20261009005/09006）已按 ISO/IEC 11179 更名/删除一批旧名表，但 2026-10-08 一个携带旧实体模型的 jar 曾启动（sys.objects 建表时间 05:48:21 与 23:21:28，均晚于 09005 04:02、09006 04:10 的应用时间），把 11 张**已不在当前实体模型中**的表又建了回来（10 张空表 + `value_meaning_permissible_value_meaning` 4674 行旧 jar 并行写入的冗余映射行 + `ws_import_run` 1 行死数据）。

这些残留表：
- 不属于当前实体模型（全代码库 `@Table`/`@JoinTable`/迁移脚本零引用，逐一 grep 核实）；
- 其中 9 张是现行表的旧名（rename 目标已核实存在于 mdr 且承载现行数据）；
- 无现行表外键引用（sys.foreign_key_columns 查询为空，DROP 无阻塞）；
- 会误导后续开发与 Agent（如 registry-specification-model-correction 评审 B1 即被其存在所误导——真实库"碰巧成功"掩盖了迁移引用已删表的缺陷）。

## What Changes

- **三方言迁移 V20261009009**（全守卫、幂等、全新库空转）删除 11 张残留表：
  `conceptual_domain_dec`（→data_element_concept_domain）、`definition_context_relation`（→definition_context）、`designation_context_relation`（→designation_context）、`designation_naming_convention`（→naming_convention_conformance）、`item_mapping_subject`（→subject_mapping）、`item_mapping_object`（→object_mapping）、`namespace_naming_convention`（→naming_convention_utilization）、`unit_of_measure_measure_class`（→unit_of_measure_class）、`submission_record_registered_item`（→registered_item_submission_record）、`value_meaning_permissible_value_meaning`（09005 已声明冗余并删除，行数为旧 jar 并行写入）、`ws_import_run`（代码/迁移零引用的更老 WIP 残留）。
- 现行表（含 ext schema 的 ext.contact/ext.registrar 卫星——建表时间虽晚但属当前模型）**一律不动**。

非目标：

- 不改动任何现行表结构与数据；
- 不处理"旧 jar 再次运行会重建残留表"的部署纪律问题（属发布管理）。

## Capabilities

### New Capabilities

- `legacy-stale-table-cleanup`：数据库中不得残留已移出实体模型的历史旧表；发现旧模型进程重建的残留表时以守卫式迁移清除。

## Impact

- **代码**：仅 3 个方言迁移脚本（V20261009009）。
- **数据**：残留表内数据全部为死数据（10 张空表 + 冗余映射 4674 行 + 1 行 import 残留），现行数据由更名后的现行表承载，无损。
- **兼容**：全新库（Hibernate 只建当前模型）天然无这些表，迁移空转；SQLite/达梦历史副本经核实无残留（sqlite existing.db at 09007 确认）。
