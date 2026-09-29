-- 静态预置重放：语言名称中文口径修正（zh-CN = 简体中文）。
-- 本文件是 V20260929001 迁移的"活副本"：迁移面向既有库一次性执行，
-- 本文件由 LanguagePresetSeeder 在恢复出厂/初始化向导兜底时与
-- V20260919003 一起重放。两处语句语义 MUST 保持一致。幂等（重复 UPDATE 同值）。

UPDATE ext.language_identification
SET language_name = N'简体中文'
WHERE language_id = (SELECT id FROM mdr.language_identification WHERE language_identifier = 'zh-CN');
