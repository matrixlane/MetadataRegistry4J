-- 内置 G20 二十二种语言一体化预置:语言代码(固定 UUID,LanguageInitializer 永远查到
-- 已存在而跳过,保留作历史库缺行兜底) + 中文语言名称/地区名称。
-- 与 V20260919002 的分工:02 面向既有库,按 language_identifier 关联标准表补种名称;
-- 03 面向全新库与恢复出厂场景,以固定 UUID 同时播种语言代码与名称(脚本即唯一数据源,
-- 恢复出厂由出厂流程重放本脚本,见 LanguagePresetSeeder)。两份脚本均幂等:
-- 代码按 language_identifier 防重,名称按 language_id 防重。
-- 预置数据 MUST 仅存在于 SQL 脚本,应用代码 MUST NOT 包含名称预置数据(用户裁定 2026-09-19)。


INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT '228df1d4-d1fb-4ec3-a4cc-dd58acd5f531', 'zh-CN', 'Hans', 'CN'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'zh-CN');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT '98570376-9fc7-4508-952a-db0106d3ea16', 'en-US', 'Latn', 'US'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'en-US');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT '1ea79672-da5f-41f1-9a5c-68db5e0e3bc9', 'ja-JP', 'Jpan', 'JP'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'ja-JP');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT '006f3010-9bae-4368-b565-d809ef1b7352', 'en-GB', 'Latn', 'GB'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'en-GB');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT '720df35b-a6e3-40f8-9106-6b62ad0b19da', 'en-CA', 'Latn', 'CA'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'en-CA');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT '35326fd2-fdfb-4e80-a0f7-6ea99c0c2ca4', 'fr-CA', 'Latn', 'CA'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'fr-CA');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT 'e2c49553-cf21-4ecd-8af3-896d987f3f7e', 'fr-FR', 'Latn', 'FR'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'fr-FR');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT '44f468c2-db67-4a02-b345-43c1a1d9102a', 'de-DE', 'Latn', 'DE'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'de-DE');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT 'e0197354-6f5d-4221-84b8-6a5cc50520c2', 'ru-RU', 'Cyrl', 'RU'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'ru-RU');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT '63a6321e-e386-41d9-9d43-08058351b5dd', 'ko-KR', 'Kore', 'KR'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'ko-KR');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT '2c549335-62ef-46a5-9211-febdedd9ea08', 'it-IT', 'Latn', 'IT'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'it-IT');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT 'ae21131f-6214-4ef5-8272-1f86c3f8b490', 'es-ES', 'Latn', 'ES'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'es-ES');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT 'f18f4f64-df54-4e3c-8a4d-51f600d40bcb', 'es-AR', 'Latn', 'AR'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'es-AR');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT '1f6975ab-f1d0-490f-a147-0889f8a32fe4', 'es-MX', 'Latn', 'MX'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'es-MX');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT '7e6fa393-2aae-431c-b526-09198cbf840c', 'pt-BR', 'Latn', 'BR'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'pt-BR');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT '413e0468-0805-499b-b77d-524413c7af39', 'hi-IN', 'Deva', 'IN'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'hi-IN');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT 'da694626-e555-43bb-bb37-b777829b63ad', 'en-IN', 'Latn', 'IN'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'en-IN');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT '44a43766-992b-4a8f-91fa-f6b3c6bc6ab8', 'en-AU', 'Latn', 'AU'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'en-AU');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT 'be2af107-da3d-49b8-b4a5-91a5cf7001ca', 'en-ZA', 'Latn', 'ZA'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'en-ZA');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT 'b07dddea-3706-49a2-984b-929657cd950d', 'ar-SA', 'Arab', 'SA'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'ar-SA');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT '634b9f5e-169b-4a4d-92b9-834c6bde99b2', 'tr-TR', 'Latn', 'TR'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'tr-TR');

INSERT INTO mdr.language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT 'f0ea7a50-18f4-4749-9d14-6f1b40ed5b08', 'id-ID', 'Latn', 'ID'
WHERE NOT EXISTS (SELECT 1 FROM mdr.language_identification WHERE language_identifier = 'id-ID');

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
