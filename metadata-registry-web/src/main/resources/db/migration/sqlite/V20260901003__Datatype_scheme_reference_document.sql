-- 类型修正:DataTypeScheme.reference → scheme_reference Reference_Document(11179-31:2023 表 11)。
-- SQLite 不支持 DROP COLUMN IF EXISTS:全新库按实体定义建表本无 reference 列,
-- 旧库残留的 reference 列不再被实体映射,保留无害。新列由 ddl-auto=update 补齐。
-- 本脚本为占位,保持三方言迁移版本号一致。
SELECT 1;
