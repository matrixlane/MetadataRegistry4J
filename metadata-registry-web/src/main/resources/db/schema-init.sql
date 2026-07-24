-- SQL Server：确保应用 schema 存在（ddl-auto 之前由 spring.datasource.schema 执行，
-- 该机制保证先于 EntityManagerFactory 运行）。
-- 达梦会报语法错误，由 continue-on-error 跳过（DM 用户即模式，无需创建）。
IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'mdr') EXEC('CREATE SCHEMA mdr')
