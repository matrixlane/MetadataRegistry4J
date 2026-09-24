-- Item_Mapping 重建（用户决策 2026-09-01 审计 / 2026-09-20 立项）：
-- 旧表（自创 mapping_type 4 值 + external_uri + mapping_scheme_id）物理重建为
-- 标准模型——degree 14 值、关联类携带 mapping_variable_name、
-- mapping_hierarchy 层级；id 与 item 根表共享主键（Item 子类化）。
-- 2026-09-20 核实映射表数据为空，直接 DROP/CREATE，无数据迁移。
-- 幂等：SQLite 上 Flyway 的 DDL 回滚不可靠（实测半执行后仅回滚历史行），
-- 故 drop 一律 IF EXISTS，索引一律 IF NOT EXISTS，任何半执行残留态重跑均可收敛。
drop table if exists item_mapping_subject;
drop table if exists item_mapping_object;
drop table if exists item_mapping;
drop table if exists mapping_hierarchy;

create table item_mapping (
    degree varchar(32) not null check (degree in ('related to','same as','semantically equivalent','semantically similar','derived from','has derivation','broader than','narrower than','child of','parent of','contains','contained in','instance of','has instance')),
    description varchar(2000),
    transformation_rule varchar(4000),
    transformation_rule_notation varchar(255),
    id blob not null,
    primary key (id)
);

create table item_mapping_subject (
    id blob not null,
    item_mapping_id blob not null,
    item_id blob not null,
    mapping_variable_name varchar(255),
    primary key (id)
);

create table item_mapping_object (
    id blob not null,
    item_mapping_id blob not null,
    item_id blob not null,
    mapping_variable_name varchar(255),
    primary key (id)
);

create table mapping_hierarchy (
    id blob not null,
    superset_mapping_id blob not null,
    subset_mapping_id blob not null,
    primary key (id)
);

-- 每条映射恰好一个主体/客体（MVP 1:1）；每条映射至多一个上层（subset 唯一）。
create unique index if not exists uk_ims_mapping on item_mapping_subject (item_mapping_id);
create unique index if not exists uk_imo_mapping on item_mapping_object (item_mapping_id);
create unique index if not exists uk_mh_subset on mapping_hierarchy (subset_mapping_id);
create index if not exists idx_im_degree on item_mapping (degree);
create index if not exists idx_ims_item on item_mapping_subject (item_id);
create index if not exists idx_imo_item on item_mapping_object (item_id);
create index if not exists idx_mh_superset on mapping_hierarchy (superset_mapping_id);
