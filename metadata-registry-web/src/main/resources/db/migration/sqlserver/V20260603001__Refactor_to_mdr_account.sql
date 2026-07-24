-- 1. Create mdr schema if not exists
IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'mdr')
BEGIN
    EXEC('CREATE SCHEMA mdr');
END
GO

-- 2. Dynamically transfer all user tables from dbo to mdr schema (except flyway_schema_history)
DECLARE @TableName NVARCHAR(256);
DECLARE @Sql NVARCHAR(MAX);

DECLARE table_cursor CURSOR FOR
SELECT TABLE_NAME 
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = 'dbo' 
  AND TABLE_TYPE = 'BASE TABLE'
  AND TABLE_NAME <> 'flyway_schema_history';

OPEN table_cursor;
FETCH NEXT FROM table_cursor INTO @TableName;

WHILE @@FETCH_STATUS = 0
BEGIN
    IF EXISTS (SELECT * FROM sys.tables WHERE name = @TableName AND schema_id = SCHEMA_ID('dbo'))
    BEGIN
        SET @Sql = 'ALTER SCHEMA mdr TRANSFER dbo.' + QUOTENAME(@TableName);
        EXEC sp_executesql @Sql;
    END
    FETCH NEXT FROM table_cursor INTO @TableName;
END

CLOSE table_cursor;
DEALLOCATE table_cursor;
GO

-- 3. Dynamically transfer all sequences from dbo to mdr schema
DECLARE @SequenceName NVARCHAR(256);
DECLARE @SqlSeq NVARCHAR(MAX);

DECLARE seq_cursor CURSOR FOR
SELECT name 
FROM sys.sequences 
WHERE schema_id = SCHEMA_ID('dbo');

OPEN seq_cursor;
FETCH NEXT FROM seq_cursor INTO @SequenceName;

WHILE @@FETCH_STATUS = 0
BEGIN
    SET @SqlSeq = 'ALTER SCHEMA mdr TRANSFER dbo.' + QUOTENAME(@SequenceName);
    EXEC sp_executesql @SqlSeq;
    FETCH NEXT FROM seq_cursor INTO @SequenceName;
END

CLOSE seq_cursor;
DEALLOCATE seq_cursor;
GO

-- 4. Drop registry_user table and user_role_assignment table
IF EXISTS (SELECT * FROM sys.tables WHERE name = 'registry_user' AND schema_id = SCHEMA_ID('mdr'))
BEGIN
    DROP TABLE mdr.registry_user;
END

IF EXISTS (SELECT * FROM sys.tables WHERE name = 'user_role_assignment' AND schema_id = SCHEMA_ID('mdr'))
BEGIN
    DROP TABLE mdr.user_role_assignment;
END

-- 5. Rename app_user to account
IF EXISTS (SELECT * FROM sys.tables WHERE name = 'app_user' AND schema_id = SCHEMA_ID('mdr'))
BEGIN
    EXEC sp_rename 'mdr.app_user', 'account';
END
GO

-- 6. Clean and alter account table
IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('mdr.account') AND name = 'display_name')
BEGIN
    ALTER TABLE mdr.account DROP COLUMN display_name;
END

IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('mdr.account') AND name = 'email')
BEGIN
    ALTER TABLE mdr.account DROP COLUMN email;
END

IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('mdr.account') AND name = 'role')
BEGIN
    ALTER TABLE mdr.account DROP COLUMN role;
END

-- Add sys_role column if it does not exist
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('mdr.account') AND name = 'sys_role')
BEGIN
    ALTER TABLE mdr.account ADD sys_role VARCHAR(32) NULL;
END

-- 7. Rename columns in other tables to refer to account_id
IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('mdr.namespace_permission') AND name = 'app_user_id')
BEGIN
    EXEC sp_rename 'mdr.namespace_permission.app_user_id', 'account_id', 'COLUMN';
END

IF EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('mdr.contact') AND name = 'user_id')
BEGIN
    EXEC sp_rename 'mdr.contact.user_id', 'account_id', 'COLUMN';
END
