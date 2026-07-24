-- Create system_setting table
CREATE TABLE system_setting (
    setting_key VARCHAR(128) NOT NULL PRIMARY KEY,
    setting_value VARCHAR(1024) NOT NULL
);

-- Create namespace_permission table
CREATE TABLE namespace_permission (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    namespace_id UNIQUEIDENTIFIER NULL,
    app_user_id UNIQUEIDENTIFIER NULL,
    organization_id UNIQUEIDENTIFIER NULL,
    permission_type VARCHAR(20) NOT NULL
);

-- Add user_id column to contact table (since user association moved from Registrar to Contact)
ALTER TABLE contact ADD user_id UNIQUEIDENTIFIER NULL;

-- Migrate existing registrar user_id values to contact table
-- (Under JOINED strategy, registrar.id is a foreign key pointing to contact.id)
IF EXISTS (
    SELECT * FROM sys.columns 
    WHERE object_id = OBJECT_ID('registrar') AND name = 'user_id'
)
BEGIN
    EXEC sp_executesql N'UPDATE c SET c.user_id = r.user_id FROM contact c INNER JOIN registrar r ON c.id = r.id;';
    
    -- Drop all indexes/constraints on registrar.user_id dynamically
    DECLARE @IndexName NVARCHAR(128);
    DECLARE index_cursor CURSOR FOR
        SELECT i.name
        FROM sys.indexes i
        INNER JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
        INNER JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
        WHERE i.object_id = OBJECT_ID('registrar') AND c.name = 'user_id';
        
    OPEN index_cursor;
    FETCH NEXT FROM index_cursor INTO @IndexName;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        IF EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(@IndexName) AND type = 'UQ')
        BEGIN
            DECLARE @DropUQ NVARCHAR(MAX) = N'ALTER TABLE registrar DROP CONSTRAINT ' + QUOTENAME(@IndexName);
            EXEC sp_executesql @DropUQ;
        END
        ELSE
        BEGIN
            DECLARE @DropIX NVARCHAR(MAX) = N'DROP INDEX ' + QUOTENAME(@IndexName) + N' ON registrar';
            EXEC sp_executesql @DropIX;
        END
        FETCH NEXT FROM index_cursor INTO @IndexName;
    END;
    CLOSE index_cursor;
    DEALLOCATE index_cursor;

    -- Drop the foreign key constraint on registrar.user_id if it exists
    DECLARE @ConstraintName NVARCHAR(128);
    SELECT @ConstraintName = name
    FROM sys.foreign_keys
    WHERE parent_object_id = OBJECT_ID('registrar')
      AND referenced_object_id = OBJECT_ID('registry_user');
      
    IF @ConstraintName IS NOT NULL
    BEGIN
        DECLARE @DropSql NVARCHAR(MAX) = N'ALTER TABLE registrar DROP CONSTRAINT ' + QUOTENAME(@ConstraintName);
        EXEC sp_executesql @DropSql;
    END

    -- Drop the user_id column from registrar table
    ALTER TABLE registrar DROP COLUMN user_id;
END
