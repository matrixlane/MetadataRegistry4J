# legacy-stale-table-cleanup Specification

## Purpose
TBD - created by archiving change cleanup-stale-legacy-tables. Update Purpose after archive.

## Requirements

### Requirement: 数据库不得残留已移出实体模型的历史旧表
数据库 MUST NOT 残留已不在当前实体模型中的历史旧表：凡被迁移更名或删除后又被旧模型进程重建的表（如 conceptual_domain_dec、designation_context_relation、value_meaning_permissible_value_meaning、ws_import_run 等），系统 MUST 以守卫式、幂等可重入的迁移予以清除；现行表（含 ext schema 卫星表与更名目标表）MUST NOT 受影响。清理范围 MUST 以"当前实体模型零引用 + 迁移史已处置"双证据判定，不得仅凭建表时间推断。

#### Scenario: 残留表被清除且现行表无损
- **WHEN** 在含旧模型进程重建的残留表的库上应用清理迁移
- **THEN** 全部残留表被删除，更名目标表与 ext schema 卫星表保持原状、行数不变

#### Scenario: 干净库空转
- **WHEN** 在全新库（Hibernate 仅建当前模型）上重放全部迁移
- **THEN** 清理迁移全部空转，应用启动成功

#### Scenario: 重放幂等
- **WHEN** 清理迁移在同一库上重复执行
- **THEN** 不报错、不产生变化
