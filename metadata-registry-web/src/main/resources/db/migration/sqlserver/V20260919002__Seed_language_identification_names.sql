-- 语言名称/地区名称预置数据(G20 二十二种语言,中文名称)。
-- 用户裁定 2026-09-19:名称数据只允许存在于 SQL 预置与应用内编辑,
-- 应用代码 MUST NOT 包含名称预置数据、不做运行时 Locale 解析。
-- 语言 UUID 为运行时生成,故按 language_identifier 关联标准表取 id;
-- 既有库(初始化器已补种 22 行)由 NOT EXISTS 幂等跳过,不清表重插。
-- 应用代码(ApplicationRunner)与 Flyway 的执行顺序:本脚本先于语言播种执行,
-- 全新库首批启动时无标准语言行可关联,预置名称在下次迁移/升级或手工补录时生效。

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'中文', N'中国'
FROM mdr.language_identification l
WHERE l.language_identifier = 'zh-CN'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'英语', N'美国'
FROM mdr.language_identification l
WHERE l.language_identifier = 'en-US'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'日语', N'日本'
FROM mdr.language_identification l
WHERE l.language_identifier = 'ja-JP'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'英语', N'英国'
FROM mdr.language_identification l
WHERE l.language_identifier = 'en-GB'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'英语', N'加拿大'
FROM mdr.language_identification l
WHERE l.language_identifier = 'en-CA'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'法语', N'加拿大'
FROM mdr.language_identification l
WHERE l.language_identifier = 'fr-CA'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'法语', N'法国'
FROM mdr.language_identification l
WHERE l.language_identifier = 'fr-FR'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'德语', N'德国'
FROM mdr.language_identification l
WHERE l.language_identifier = 'de-DE'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'俄语', N'俄罗斯'
FROM mdr.language_identification l
WHERE l.language_identifier = 'ru-RU'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'韩语', N'韩国'
FROM mdr.language_identification l
WHERE l.language_identifier = 'ko-KR'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'意大利语', N'意大利'
FROM mdr.language_identification l
WHERE l.language_identifier = 'it-IT'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'西班牙语', N'西班牙'
FROM mdr.language_identification l
WHERE l.language_identifier = 'es-ES'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'西班牙语', N'阿根廷'
FROM mdr.language_identification l
WHERE l.language_identifier = 'es-AR'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'西班牙语', N'墨西哥'
FROM mdr.language_identification l
WHERE l.language_identifier = 'es-MX'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'葡萄牙语', N'巴西'
FROM mdr.language_identification l
WHERE l.language_identifier = 'pt-BR'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'印地语', N'印度'
FROM mdr.language_identification l
WHERE l.language_identifier = 'hi-IN'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'英语', N'印度'
FROM mdr.language_identification l
WHERE l.language_identifier = 'en-IN'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'英语', N'澳大利亚'
FROM mdr.language_identification l
WHERE l.language_identifier = 'en-AU'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'英语', N'南非'
FROM mdr.language_identification l
WHERE l.language_identifier = 'en-ZA'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'阿拉伯语', N'沙特阿拉伯'
FROM mdr.language_identification l
WHERE l.language_identifier = 'ar-SA'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'土耳其语', N'土耳其'
FROM mdr.language_identification l
WHERE l.language_identifier = 'tr-TR'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);

INSERT INTO ext.language_identification (id, language_id, language_name, territory_name)
SELECT NEWID(), l.id, N'印度尼西亚语', N'印度尼西亚'
FROM mdr.language_identification l
WHERE l.language_identifier = 'id-ID'
  AND NOT EXISTS (SELECT 1 FROM ext.language_identification e WHERE e.language_id = l.id);
