-- orm-team.xml 对 RegistrationAuthorityExtension 的 <table schema="ext"/> 缺 name，
-- Hibernate 按实体名隐式建了空壳 ext.registration_authority_extension（真实库 2026-10-08 实测），
-- 启动回填曾向该误表写入 1 行。本迁移：误表数据回流正确表（NOT EXISTS 守卫）后删除误表。
-- 仅 SQL Server 真实库存在该表；守卫保证其他环境为空操作。
IF COL_LENGTH('ext.registration_authority_extension', 'id') IS NOT NULL
BEGIN
    EXEC('INSERT INTO ext.registration_authority (id, is_primary)
          SELECT s.id, s.is_primary FROM ext.registration_authority_extension s
          WHERE NOT EXISTS (SELECT 1 FROM ext.registration_authority e WHERE e.id = s.id)');
    EXEC('DROP TABLE ext.registration_authority_extension');
END;
