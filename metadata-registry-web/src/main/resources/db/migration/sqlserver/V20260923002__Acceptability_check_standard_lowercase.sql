-- Acceptability CHECK 与标准小写落库对齐（详见 sqlite 同名脚本头注释）：
-- 基线 CHECK 仅允许大写，与 AcceptabilityConverter 按 ISO/IEC 11179-3:2023 表 20
-- 落库小写矛盾。重建 5 张表 acceptability 列的 CHECK 为大小写不敏感（LOWER 归一）。
-- 动态 SQL 按 sys 视图发现既有 CHECK 约束并替换，幂等可重入。
DECLARE @drop_sql nvarchar(max) = N'';
SELECT @drop_sql += N'ALTER TABLE ' + QUOTENAME(s.name) + N'.' + QUOTENAME(t.name)
    + N' DROP CONSTRAINT ' + QUOTENAME(cc.name) + N';'
FROM sys.check_constraints cc
JOIN sys.tables t ON cc.parent_object_id = t.object_id
JOIN sys.schemas s ON t.schema_id = s.schema_id
JOIN sys.columns c ON c.object_id = t.object_id AND c.column_id = cc.parent_column_id
WHERE s.name = N'mdr'
  AND t.name IN (N'designation', N'definition', N'designation_context_relation',
                 N'definition_context_relation', N'designation_definition_pairing')
  AND c.name = N'acceptability';
EXEC sp_executesql @drop_sql;

DECLARE @add_sql nvarchar(max) = N'';
SELECT @add_sql += N'IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = N''CK_' + v.t + N'_acceptability_std'')
    ALTER TABLE mdr.' + QUOTENAME(v.t) + N' ADD CONSTRAINT CK_' + v.t + N'_acceptability_std
    CHECK (LOWER(acceptability) IN (''preferred'',''admitted'',''deprecated'',''superseded'',''obsolete''));'
FROM (VALUES (N'designation'), (N'definition'), (N'designation_context_relation'),
             (N'definition_context_relation'), (N'designation_definition_pairing')) v(t);
EXEC sp_executesql @add_sql;
