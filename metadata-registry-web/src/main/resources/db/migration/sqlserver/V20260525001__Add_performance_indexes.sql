-- 针对核心外键关系添加索引，避免全表扫描
CREATE NONCLUSTERED INDEX idx_designation_item_id ON designation (item_id);
CREATE NONCLUSTERED INDEX idx_definition_item_id ON definition (item_id);

CREATE NONCLUSTERED INDEX idx_designation_language_id ON designation (language_id);
CREATE NONCLUSTERED INDEX idx_definition_language_id ON definition (language_id);

CREATE NONCLUSTERED INDEX idx_designation_context_relation_designation_id ON designation_context_relation (designation_id);
CREATE NONCLUSTERED INDEX idx_designation_context_relation_context_id ON designation_context_relation (context_id);

CREATE NONCLUSTERED INDEX idx_definition_context_relation_definition_id ON definition_context_relation (definition_id);
CREATE NONCLUSTERED INDEX idx_definition_context_relation_context_id ON definition_context_relation (context_id);
