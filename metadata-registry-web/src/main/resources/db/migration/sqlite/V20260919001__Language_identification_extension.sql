-- 语言名称/地区名称扩展卫星表:SQLite 无 schema 概念,
-- 以 ext_ 物理名前缀模拟 ext schema(与 orm/orm-sqlite.xml 覆盖一致)。
-- 幂等守卫:ddl-auto(经 defer-datasource-initialization)先于 Flyway 执行,表可能
-- 已由 Hibernate 创建,故 DDL 须带 IF NOT EXISTS 才可安全重入(与 sqlserver/dm 脚本一致)。
CREATE TABLE IF NOT EXISTS ext_language_identification (
    id blob not null,
    language_id blob not null,
    language_name varchar(255),
    territory_name varchar(255),
    primary key (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_liext_language ON ext_language_identification (language_id);
