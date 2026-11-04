-- registered_item.item_id 唯一约束
-- 强制 IdentifiedItem.item 的 @OneToOne 语义，防止同一 Item 被多条 registered_item 引用。
-- 幂等：先检查索引/约束是否存在。

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes i
    JOIN sys.tables t ON i.object_id = t.object_id
    JOIN sys.schemas s ON t.schema_id = s.schema_id
    WHERE s.name = 'mdr'
      AND t.name = 'registered_item'
      AND i.name = 'UQ_registered_item_item_id'
)
    ALTER TABLE mdr.registered_item ADD CONSTRAINT UQ_registered_item_item_id UNIQUE (item_id);
