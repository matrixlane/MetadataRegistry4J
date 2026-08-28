-- 2026-08-28-persist-item-creation-mode
-- SQLite 3.35+ 支持 ADD COLUMN，无需守卫。

ALTER TABLE scoped_identifier ADD COLUMN identifier_creation_mode TEXT;
ALTER TABLE scoped_identifier ADD COLUMN version_creation_mode TEXT;