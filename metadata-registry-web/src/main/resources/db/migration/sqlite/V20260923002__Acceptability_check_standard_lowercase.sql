-- Acceptability CHECK 与标准小写落库对齐：
-- AcceptabilityConverter 按 ISO/IEC 11179-3:2023 表 20（8.7.1.2）将可接受性落库为
-- 小写（preferred/admitted/deprecated/superseded/obsolete），而 V20260723002 基线的
-- CHECK 仅允许大写——任何设置可接受性的写入在存量库上必然违反 CHECK。
-- 重建 5 张表的 CHECK 为大小写不敏感（lower() 归一）。SQLite 不支持 ALTER COLUMN，
-- 按 V20260921001 模式重建表（幂等：IF EXISTS 守卫）；无其他表的外键引用这 5 张表。
drop table if exists designation_new;

create table designation_new (
    id blob not null,
    acceptability varchar(255) check (lower(acceptability) in ('preferred','admitted','deprecated','superseded','obsolete')),
    value varchar(255),
    item_id blob,
    language_id blob,
    source_id blob,
    primary key (id)
);

insert into designation_new (id, acceptability, value, item_id, language_id, source_id)
select id, acceptability, value, item_id, language_id, source_id from designation;

drop table designation;

alter table designation_new rename to designation;

create index idx_designation_item_id on designation (item_id);
create index idx_designation_language_id on designation (language_id);

drop table if exists definition_new;

create table definition_new (
    id blob not null,
    acceptability varchar(255) check (lower(acceptability) in ('preferred','admitted','deprecated','superseded','obsolete')),
    value varchar(255),
    item_id blob,
    language_id blob,
    source_id blob,
    primary key (id)
);

insert into definition_new (id, acceptability, value, item_id, language_id, source_id)
select id, acceptability, value, item_id, language_id, source_id from definition;

drop table definition;

alter table definition_new rename to definition;

create index idx_definition_item_id on definition (item_id);
create index idx_definition_language_id on definition (language_id);

drop table if exists designation_context_relation_new;

create table designation_context_relation_new (
    id blob not null,
    acceptability varchar(255) check (lower(acceptability) in ('preferred','admitted','deprecated','superseded','obsolete')),
    context_id blob,
    designation_id blob,
    primary key (id)
);

insert into designation_context_relation_new (id, acceptability, context_id, designation_id)
select id, acceptability, context_id, designation_id from designation_context_relation;

drop table designation_context_relation;

alter table designation_context_relation_new rename to designation_context_relation;

create index idx_designation_context_relation_designation_id on designation_context_relation (designation_id);
create index idx_designation_context_relation_context_id on designation_context_relation (context_id);

drop table if exists definition_context_relation_new;

create table definition_context_relation_new (
    id blob not null,
    acceptability varchar(255) check (lower(acceptability) in ('preferred','admitted','deprecated','superseded','obsolete')),
    context_id blob,
    definition_id blob,
    primary key (id)
);

insert into definition_context_relation_new (id, acceptability, context_id, definition_id)
select id, acceptability, context_id, definition_id from definition_context_relation;

drop table definition_context_relation;

alter table definition_context_relation_new rename to definition_context_relation;

create index idx_definition_context_relation_definition_id on definition_context_relation (definition_id);
create index idx_definition_context_relation_context_id on definition_context_relation (context_id);

drop table if exists designation_definition_pairing_new;

create table designation_definition_pairing_new (
    id blob not null,
    acceptability varchar(255) check (lower(acceptability) in ('preferred','admitted','deprecated','superseded','obsolete')),
    context_id blob,
    definition_id blob,
    designation_id blob,
    primary key (id)
);

insert into designation_definition_pairing_new (id, acceptability, context_id, definition_id, designation_id)
select id, acceptability, context_id, definition_id, designation_id from designation_definition_pairing;

drop table designation_definition_pairing;

alter table designation_definition_pairing_new rename to designation_definition_pairing;
