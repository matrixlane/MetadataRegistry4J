-- contact_account_binding 增加 is_default 标志(详见 sqlite 同名脚本头注释)。
IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='ext'
    AND TABLE_NAME='contact_account_binding' AND COLUMN_NAME='is_default')
    ALTER TABLE ext.contact_account_binding ADD is_default BIT NOT NULL DEFAULT 0;
