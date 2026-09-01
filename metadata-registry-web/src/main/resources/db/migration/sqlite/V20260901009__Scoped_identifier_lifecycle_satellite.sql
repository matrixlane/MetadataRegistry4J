-- 2.4 Scoped_Identifier 扩展属性剥为卫星实体(ISO/IEC 11179 标准实体只留标准属性)
-- 建表 + 从 scoped_identifier 回填 + 删除 7 个旧列。
CREATE TABLE scoped_identifier_lifecycle (
    id blob not null,
    scoped_identifier_id blob not null,
    identifier_creation_mode varchar(16),
    version_creation_mode varchar(16),
    version_status varchar(16),
    created_at datetime,
    published_at datetime,
    deprecated_at datetime,
    obsolete_at datetime,
    primary key (id)
);
CREATE UNIQUE INDEX uk_sil_scoped_identifier ON scoped_identifier_lifecycle (scoped_identifier_id);

INSERT INTO scoped_identifier_lifecycle
    (id, scoped_identifier_id, identifier_creation_mode, version_creation_mode,
     version_status, created_at, published_at, deprecated_at, obsolete_at)
SELECT randomblob(16), id, identifier_creation_mode, version_creation_mode,
       version_status, created_at, published_at, deprecated_at, obsolete_at
FROM scoped_identifier;

ALTER TABLE scoped_identifier DROP COLUMN identifier_creation_mode;
ALTER TABLE scoped_identifier DROP COLUMN version_creation_mode;
ALTER TABLE scoped_identifier DROP COLUMN version_status;
ALTER TABLE scoped_identifier DROP COLUMN created_at;
ALTER TABLE scoped_identifier DROP COLUMN published_at;
ALTER TABLE scoped_identifier DROP COLUMN deprecated_at;
ALTER TABLE scoped_identifier DROP COLUMN obsolete_at;
