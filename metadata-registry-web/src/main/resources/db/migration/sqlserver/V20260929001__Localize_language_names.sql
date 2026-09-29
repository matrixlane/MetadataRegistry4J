-- 语言名称中文口径修正（zh-CN = 简体中文，用户裁定 2026-09-29）。
-- 与 V20260919002/03 的分工：本脚本面向既有库一次性迁移（Flyway 已应用不重跑），
-- 同一语句另存于 db/preset/<vendor>/localize-language-names.sql 供恢复出厂/
-- 初始化向导兜底重放，两通道语义 MUST 保持一致。

UPDATE ext.language_identification
SET language_name = N'简体中文'
WHERE language_id = (SELECT id FROM mdr.language_identification WHERE language_identifier = 'zh-CN');
