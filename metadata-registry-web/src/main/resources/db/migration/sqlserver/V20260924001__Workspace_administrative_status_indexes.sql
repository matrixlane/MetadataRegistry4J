-- 工作空间视图查询索引（管理状态过滤 / 主管归属 join）。
-- 表位于 mdr schema（Flyway schemas: mdr,ext），对象名必须 schema 限定；
-- 存在性判断按 mdr schema 过滤，避免误命中同名对象。
IF NOT EXISTS (SELECT 1 FROM sys.indexes i
               JOIN sys.tables t ON i.object_id = t.object_id
               JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = N'mdr' AND i.name = N'idx_registration_state_administrative_status')
    CREATE INDEX idx_registration_state_administrative_status ON mdr.registration_state(administrative_status);

IF NOT EXISTS (SELECT 1 FROM sys.indexes i
               JOIN sys.tables t ON i.object_id = t.object_id
               JOIN sys.schemas s ON t.schema_id = s.schema_id
               WHERE s.name = N'mdr' AND i.name = N'idx_administered_item_stewardship_record_id')
    CREATE INDEX idx_administered_item_stewardship_record_id ON mdr.administered_item(stewardship_record_id);
