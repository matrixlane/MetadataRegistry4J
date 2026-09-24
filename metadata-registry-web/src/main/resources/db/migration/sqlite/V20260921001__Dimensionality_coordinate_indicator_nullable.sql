-- Dimensionality.coordinate_indicator 放宽为可空:
-- 三态 Boolean——TRUE=坐标测量、FALSE=区间测量、
-- NULL=非物理单位(Not applicable)。审计 #7(2026-09-01)已将实体改为 Boolean,
-- 但 V20260723002 基线存量列仍为 NOT NULL,"不适用"维度无法落库。
-- SQLite 不支持 ALTER COLUMN,按 V20260920002 模式重建表(幂等:IF EXISTS 守卫)。
drop table if exists dimensionality_new;

create table dimensionality_new (
    coordinate_indicator boolean,
    id blob not null,
    primary key (id)
);

insert into dimensionality_new (id, coordinate_indicator)
select id, coordinate_indicator from dimensionality;

drop table dimensionality;

alter table dimensionality_new rename to dimensionality;
