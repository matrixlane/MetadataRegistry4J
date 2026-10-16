-- 针对核心外键关系添加索引，避免全表扫描
-- 幂等守卫：空库（Hibernate 尚未建表）时静默跳过，避免冷启动失败。
-- 已知限制：空库首启后这些索引缺失（结构归 Hibernate 拥有，Flyway 先于其执行），
-- 需要索引的既有库不受影响（索引随迁移正常建立）。
IF OBJECT_ID('designation', 'U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_designation_item_id' AND object_id = OBJECT_ID('designation'))
    CREATE NONCLUSTERED INDEX idx_designation_item_id ON designation (item_id);
IF OBJECT_ID('definition', 'U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_definition_item_id' AND object_id = OBJECT_ID('definition'))
    CREATE NONCLUSTERED INDEX idx_definition_item_id ON definition (item_id);

IF OBJECT_ID('designation', 'U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_designation_language_id' AND object_id = OBJECT_ID('designation'))
    CREATE NONCLUSTERED INDEX idx_designation_language_id ON designation (language_id);
IF OBJECT_ID('definition', 'U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_definition_language_id' AND object_id = OBJECT_ID('definition'))
    CREATE NONCLUSTERED INDEX idx_definition_language_id ON definition (language_id);

IF OBJECT_ID('designation_context_relation', 'U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_designation_context_relation_designation_id' AND object_id = OBJECT_ID('designation_context_relation'))
    CREATE NONCLUSTERED INDEX idx_designation_context_relation_designation_id ON designation_context_relation (designation_id);
IF OBJECT_ID('designation_context_relation', 'U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_designation_context_relation_context_id' AND object_id = OBJECT_ID('designation_context_relation'))
    CREATE NONCLUSTERED INDEX idx_designation_context_relation_context_id ON designation_context_relation (context_id);

IF OBJECT_ID('definition_context_relation', 'U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_definition_context_relation_definition_id' AND object_id = OBJECT_ID('definition_context_relation'))
    CREATE NONCLUSTERED INDEX idx_definition_context_relation_definition_id ON definition_context_relation (definition_id);
IF OBJECT_ID('definition_context_relation', 'U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_definition_context_relation_context_id' AND object_id = OBJECT_ID('definition_context_relation'))
    CREATE NONCLUSTERED INDEX idx_definition_context_relation_context_id ON definition_context_relation (context_id);
