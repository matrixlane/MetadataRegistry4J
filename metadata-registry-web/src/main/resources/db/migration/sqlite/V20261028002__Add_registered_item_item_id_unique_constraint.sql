-- registered_item.item_id 唯一索引
-- SQLite ALTER TABLE 不支持 ADD CONSTRAINT UNIQUE，使用唯一索引强制 IdentifiedItem.item 的 @OneToOne 语义。
-- 幂等：IF NOT EXISTS。

CREATE UNIQUE INDEX IF NOT EXISTS UQ_registered_item_item_id ON registered_item(item_id);
