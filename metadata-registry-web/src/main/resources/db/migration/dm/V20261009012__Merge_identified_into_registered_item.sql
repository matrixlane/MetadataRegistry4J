-- ============================================================================
-- V20261009012__Merge_identified_into_registered_item.sql  (达梦 DM8)
--
-- repair-administered-item-reference-documents-ui：受管链物理表合并。
-- 详见 SQL Server 版文件头。差异为全限定 schema 写法与达梦的
-- ALTER TABLE ... ADD CONSTRAINT / DROP CONSTRAINT 语法。
--
-- 【未实测】按项目既有惯例，达梦方言迁移长期未在真实达梦实例上验证
-- （见 .superpowers-memory/LEARNING_BACKLOG.md）。实际应用前需在达梦实例上先行验证，
-- 尤其「ALTER TABLE ADD COLUMN」与动态删 FK 的语法差异。
--
-- 【不可逆】删表后无法回滚，执行前务必备份。
-- ============================================================================

-- ① registered_item 增 item_id
ALTER TABLE MDR.REGISTERED_ITEM ADD ITEM_ID UUID;
/

-- ② 由 identified_item 回填
UPDATE MDR.REGISTERED_ITEM ri
   SET ri.ITEM_ID = (SELECT ii.ITEM_ID FROM MDR.IDENTIFIED_ITEM ii WHERE ii.ID = ri.ID)
 WHERE ri.ITEM_ID IS NULL
   AND EXISTS (SELECT 1 FROM MDR.IDENTIFIED_ITEM ii WHERE ii.ID = ri.ID);
/

-- ③ scoped_identifier 外键改指 registered_item
DELETE FROM MDR.SC_CONSTRAINT_CONS_V WHERE CONS_TYPE = 'R' AND R_CONSTRAINT_NAME IN (
    SELECT CONSTRAINT_NAME FROM MDR.USER_CONSTRAINTS
     WHERE TABLE_NAME = 'SCOPED_IDENTIFIER' AND R_CONSTRAINT_NAME = 'IDENTIFIED_ITEM'
);
/
ALTER TABLE MDR.SCOPED_IDENTIFIER
    ADD CONSTRAINT FK_SCOPED_IDENTIFIER_REGISTERED_ITEM
    FOREIGN KEY (IDENTIFIED_ITEM_ID) REFERENCES MDR.REGISTERED_ITEM(ID);
/

-- ④ 删旧表
DROP TABLE MDR.IDENTIFIED_ITEM;
/

-- ---- 落地校验 ----
SELECT 'REGISTERED_ITEM.ITEM_ID 未回填', COUNT(*) FROM MDR.REGISTERED_ITEM WHERE ITEM_ID IS NULL;
SELECT 'REGISTERED_ITEM', COUNT(*) FROM MDR.REGISTERED_ITEM;
SELECT 'SCOPED_IDENTIFIER', COUNT(*) FROM MDR.SCOPED_IDENTIFIER;
