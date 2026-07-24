-- Add force_password_reset and temp_password_used to mdr.account table
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('mdr.account') AND name = 'force_password_reset')
BEGIN
    ALTER TABLE mdr.account ADD force_password_reset BIT NOT NULL DEFAULT 0;
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('mdr.account') AND name = 'temp_password_used')
BEGIN
    ALTER TABLE mdr.account ADD temp_password_used BIT NOT NULL DEFAULT 0;
END
GO
