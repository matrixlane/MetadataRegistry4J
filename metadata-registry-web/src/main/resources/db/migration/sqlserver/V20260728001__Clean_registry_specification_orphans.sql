-- 清理 registry_specification 中在 item 表找不到对应记录的孤儿行，
-- 以修复 Hibernate 启动时添加 FK 约束失败的问题。
DELETE FROM registry_specification WHERE id NOT IN (SELECT id FROM item);
