-- 内置 G20 二十二种语言一体化预置:语言代码(固定 UUID,LanguageInitializer 永远查到
-- 已存在而跳过,保留作历史库缺行兜底) + 中文语言名称/地区名称。
-- 与 V20260919002 的分工:02 面向既有库,按 language_identifier 关联标准表补种名称;
-- 03 面向全新库与恢复出厂场景,以固定 UUID 同时播种语言代码与名称(脚本即唯一数据源,
-- 恢复出厂由出厂流程重放本脚本,见 LanguagePresetSeeder)。两份脚本均幂等:
-- 代码按 language_identifier 防重,名称按 language_id 防重。
-- 预置数据 MUST 仅存在于 SQL 脚本,应用代码 MUST NOT 包含名称预置数据(用户裁定 2026-09-19)。


INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'228df1d4d1fb4ec3a4ccdd58acd5f531', 'zh-CN', 'Hans', 'CN'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'zh-CN');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'985703769fc74508952adb0106d3ea16', 'en-US', 'Latn', 'US'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'en-US');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'1ea79672da5f41f19a5c68db5e0e3bc9', 'ja-JP', 'Jpan', 'JP'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'ja-JP');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'006f30109bae4368b565d809ef1b7352', 'en-GB', 'Latn', 'GB'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'en-GB');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'720df35ba6e340f891066b62ad0b19da', 'en-CA', 'Latn', 'CA'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'en-CA');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'35326fd2fdfb4e80a0f76ea99c0c2ca4', 'fr-CA', 'Latn', 'CA'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'fr-CA');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'e2c49553cf214ecd8af3896d987f3f7e', 'fr-FR', 'Latn', 'FR'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'fr-FR');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'44f468c2db674a02b34543c1a1d9102a', 'de-DE', 'Latn', 'DE'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'de-DE');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'e01973546f5d422184b86a5cc50520c2', 'ru-RU', 'Cyrl', 'RU'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'ru-RU');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'63a6321ee38641d99d4308058351b5dd', 'ko-KR', 'Kore', 'KR'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'ko-KR');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'2c54933562ef46a59211febdedd9ea08', 'it-IT', 'Latn', 'IT'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'it-IT');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'ae21131f62144ef582721f86c3f8b490', 'es-ES', 'Latn', 'ES'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'es-ES');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'f18f4f64df544e3c8a4d51f600d40bcb', 'es-AR', 'Latn', 'AR'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'es-AR');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'1f6975abf1d0490fa1470889f8a32fe4', 'es-MX', 'Latn', 'MX'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'es-MX');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'7e6fa3932aae431cb52609198cbf840c', 'pt-BR', 'Latn', 'BR'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'pt-BR');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'413e04680805499bb77d524413c7af39', 'hi-IN', 'Deva', 'IN'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'hi-IN');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'da694626e55543bbbb37b777829b63ad', 'en-IN', 'Latn', 'IN'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'en-IN');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'44a43766992b4a8f91faf6b3c6bc6ab8', 'en-AU', 'Latn', 'AU'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'en-AU');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'be2af107da3d49b8b4a591a5cf7001ca', 'en-ZA', 'Latn', 'ZA'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'en-ZA');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'b07dddea370649a2984b929657cd950d', 'ar-SA', 'Arab', 'SA'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'ar-SA');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'634b9f5e169b4a4d92b9834c6bde99b2', 'tr-TR', 'Latn', 'TR'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'tr-TR');

INSERT INTO language_identification (id, language_identifier, script_identifier, geopolitical_territory_identifier)
SELECT x'f0ea7a5018f447499d146f1b40ed5b08', 'id-ID', 'Latn', 'ID'
WHERE NOT EXISTS (SELECT 1 FROM language_identification WHERE language_identifier = 'id-ID');

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '中文', '中国'
FROM language_identification l
WHERE l.language_identifier = 'zh-CN'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '英语', '美国'
FROM language_identification l
WHERE l.language_identifier = 'en-US'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '日语', '日本'
FROM language_identification l
WHERE l.language_identifier = 'ja-JP'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '英语', '英国'
FROM language_identification l
WHERE l.language_identifier = 'en-GB'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '英语', '加拿大'
FROM language_identification l
WHERE l.language_identifier = 'en-CA'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '法语', '加拿大'
FROM language_identification l
WHERE l.language_identifier = 'fr-CA'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '法语', '法国'
FROM language_identification l
WHERE l.language_identifier = 'fr-FR'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '德语', '德国'
FROM language_identification l
WHERE l.language_identifier = 'de-DE'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '俄语', '俄罗斯'
FROM language_identification l
WHERE l.language_identifier = 'ru-RU'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '韩语', '韩国'
FROM language_identification l
WHERE l.language_identifier = 'ko-KR'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '意大利语', '意大利'
FROM language_identification l
WHERE l.language_identifier = 'it-IT'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '西班牙语', '西班牙'
FROM language_identification l
WHERE l.language_identifier = 'es-ES'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '西班牙语', '阿根廷'
FROM language_identification l
WHERE l.language_identifier = 'es-AR'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '西班牙语', '墨西哥'
FROM language_identification l
WHERE l.language_identifier = 'es-MX'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '葡萄牙语', '巴西'
FROM language_identification l
WHERE l.language_identifier = 'pt-BR'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '印地语', '印度'
FROM language_identification l
WHERE l.language_identifier = 'hi-IN'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '英语', '印度'
FROM language_identification l
WHERE l.language_identifier = 'en-IN'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '英语', '澳大利亚'
FROM language_identification l
WHERE l.language_identifier = 'en-AU'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '英语', '南非'
FROM language_identification l
WHERE l.language_identifier = 'en-ZA'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '阿拉伯语', '沙特阿拉伯'
FROM language_identification l
WHERE l.language_identifier = 'ar-SA'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '土耳其语', '土耳其'
FROM language_identification l
WHERE l.language_identifier = 'tr-TR'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);

INSERT INTO ext_language_identification (id, language_id, language_name, territory_name)
SELECT randomblob(16), l.id, '印度尼西亚语', '印度尼西亚'
FROM language_identification l
WHERE l.language_identifier = 'id-ID'
  AND NOT EXISTS (SELECT 1 FROM ext_language_identification e WHERE e.language_id = l.id);
