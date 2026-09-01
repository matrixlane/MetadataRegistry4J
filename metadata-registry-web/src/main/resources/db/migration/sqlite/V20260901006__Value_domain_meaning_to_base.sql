-- 5.6 value_domain_meaning 提升到 ValueDomain 基类(ISO/IEC 11179-31:2023 7.2.3.1 / 7.4.4.1)
-- SQLite 不支持 DROP COLUMN 的 IF EXISTS 守卫,且 ddl-auto=update 不会删旧列;
-- 新列由 ddl-auto 自动补建,存量回填参考 sqlserver 方言脚本。此处为占位。
SELECT 1;
