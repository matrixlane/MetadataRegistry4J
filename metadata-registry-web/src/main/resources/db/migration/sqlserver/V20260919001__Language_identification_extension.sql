-- 语言名称/地区名称扩展卫星表:ext.language_identification,与标准表 mdr.language_identification
-- 同名不同 schema(设计 2026-09-19-language-identification-extension)。
-- 缺失行语义:名称返回 null(2026-09-19 用户裁定,代码 MUST NOT 运行时解析;预置数据见 V20260919002)。
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'ext')
    EXEC('CREATE SCHEMA ext');

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'ext' AND TABLE_NAME = 'language_identification'
)
BEGIN
    CREATE TABLE ext.language_identification (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        language_id UNIQUEIDENTIFIER NOT NULL,
        language_name NVARCHAR(255) NULL,
        territory_name NVARCHAR(255) NULL
    );
    CREATE UNIQUE INDEX uk_liext_language ON ext.language_identification (language_id);
END;
