-- 2026-08-28-persist-item-creation-mode
-- 在 scoped_identifier 表新增 identifier_creation_mode 与 version_creation_mode 两列，
-- 用于持久化创建时用户选择的 AUTO/MANUAL，编辑态据此锁定或开放输入控件。
-- 两列均可空（NULL = 既存记录 / 外部命名空间，按 MANUAL 路径等价处理）。

IF COL_LENGTH('scoped_identifier', 'identifier_creation_mode') IS NULL
    EXEC('ALTER TABLE scoped_identifier ADD identifier_creation_mode nvarchar(16) NULL');

IF COL_LENGTH('scoped_identifier', 'version_creation_mode') IS NULL
    EXEC('ALTER TABLE scoped_identifier ADD version_creation_mode nvarchar(16) NULL');