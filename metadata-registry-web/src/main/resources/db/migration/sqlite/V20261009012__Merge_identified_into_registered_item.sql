-- ============================================================================
-- V20261009012__Merge_identified_into_registered_item.sql  (SQLite)
--
-- repair-administered-item-reference-documents-ui：受管链物理表合并。
-- 详见 SQL Server 版文件头（含标准依据、步骤、幂等与语义后果说明）。
-- 元模型不变：IdentifiedItem 降为 @MappedSuperclass，RegisteredItem 仍是唯一实体层。
--
-- SQLite 差异：不支持 ALTER TABLE ADD CONSTRAINT / DROP CONSTRAINT，
-- scoped_identifier 的外键需在建新表时重新声明；identified_item 的删除需
-- 先解除其上的关联（本库实际只有 scoped_identifier 以列名引用、不带 REFERENCES
-- 子句，见基线 DDL，故可直接 DROP TABLE）。
-- ============================================================================

-- ① registered_item 增 item_id（SQLite 无 ALTER TABLE ADD COLUMN IF NOT EXISTS，
--    故先判存在性再走 PRAGMA 动态重建路径；本基线中该列恒不存在，直接重建）
PRAGMA foreign_keys = OFF;

-- ② 重建 registered_item，承接 identified_item.item_id
DROP TABLE IF EXISTS registered_item_v20261009012;

CREATE TABLE registered_item_v20261009012 (
    id blob not null,
    item_id blob,
    constraint_set_id blob,
    primary key (id)
);

INSERT INTO registered_item_v20261009012 (id, item_id, constraint_set_id)
SELECT ri.id, ii.item_id, ri.constraint_set_id
  FROM registered_item ri
  LEFT JOIN identified_item ii ON ii.id = ri.id;

-- 兜底：registered_item 有行而 identified_item 无对应行时由 scoped_identifier 反查
UPDATE registered_item_v20261009012
   SET item_id = (
        SELECT MIN(si.identified_item_id) FROM scoped_identifier si
         WHERE si.identified_item_id = registered_item_v20261009012.id)
 WHERE item_id IS NULL;

DROP TABLE registered_item;

ALTER TABLE registered_item_v20261009012 RENAME TO registered_item;

-- 索引重建
CREATE INDEX IF NOT EXISTS idx_registered_item_constraint_set
    ON registered_item (constraint_set_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_registered_item_item
    ON registered_item (item_id);

-- ③ scoped_identifier 重指向 registered_item（重建以替换外键声明）
--    本基线中 scoped_identifier 未声明 REFERENCES 子句（外键由 Hibernate
--    ddl-auto 维护），故仅需保留列名；此处重建以确保列序与 NOT NULL 约束不变。
CREATE TABLE scoped_identifier_v20261009012 (
    id blob not null,
    identifier varchar(255) not null,
    version varchar(32) not null,
    identified_item_id blob,
    namespace_id blob,
    language_id blob,
    sign_id blob,
    acceptability varchar(64),
    primary key (id)
);

INSERT INTO scoped_identifier_v20261009012
    (id, identifier, version, identified_item_id, namespace_id, language_id,
     sign_id, acceptability)
SELECT id, identifier, version, identified_item_id, namespace_id, language_id,
       sign_id, acceptability
  FROM scoped_identifier;

DROP TABLE scoped_identifier;

ALTER TABLE scoped_identifier_v20261009012 RENAME TO scoped_identifier;

CREATE INDEX IF NOT EXISTS idx_scoped_identifier_identified_item
    ON scoped_identifier (identified_item_id);
CREATE INDEX IF NOT EXISTS idx_scoped_identifier_namespace
    ON scoped_identifier (namespace_id);

-- ④ 删旧表
DROP TABLE IF EXISTS identified_item;

PRAGMA foreign_keys = ON;

-- ---- 落地校验 ----
SELECT 'registered_item.item_id 未回填', COUNT(*) FROM registered_item WHERE item_id IS NULL;
SELECT 'registered_item', COUNT(*) FROM registered_item;
SELECT 'scoped_identifier', COUNT(*) FROM scoped_identifier;
SELECT 'identified_item 是否已删除',
       CASE WHEN (SELECT COUNT(*) FROM sqlite_master
                   WHERE type='table' AND name='identified_item') = 0
            THEN 'YES' ELSE 'NO' END;
