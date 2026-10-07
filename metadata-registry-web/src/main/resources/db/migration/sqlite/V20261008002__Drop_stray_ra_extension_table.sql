-- sqlite profile 经 orm-sqlite.xml 显式物理名 ext_registration_authority，从不产生误表；
-- 迁移占位保持三方言版本号一致（守卫：表不存在时为空操作）。
DROP TABLE IF EXISTS ext_registration_authority_extension;
