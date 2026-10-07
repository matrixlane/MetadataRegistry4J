-- mdr.registration_authority.is_primary 迁出至 ext 卫星表（move-ra-primary-to-ext）：
-- is_primary 为应用隐式标志（ISO/IEC 11179-3:2023 表 24 无此属性），按数据模型分层规则
-- （mdr 只装标准列，应用扩展一律 ext schema 卫星表）剥离至 ext.registration_authority
-- （1:1 按 id；有意不建 JPA 关联，读写经值键，同 ContactAccountBinding 范式）。
-- 数据搬迁（INSERT SELECT）后删除 mdr 列；列存在性由 V20261005001 保证（同号先行）。
-- SQLite 无 schema 概念，物理名加 ext_ 前缀（与 orm/orm-sqlite.xml 覆盖一致）；
-- DROP COLUMN 不支持 IF EXISTS（Flyway 一次语义，无需幂等守卫，同 V20260930001 先例）。
create table ext_registration_authority (id blob not null primary key, is_primary boolean not null default false);

insert into ext_registration_authority select id, is_primary from registration_authority;

alter table registration_authority drop column is_primary;
