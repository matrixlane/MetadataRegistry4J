-- 语言名称/地区名称预置数据(G20 二十二种语言,中文名称)。
-- 用户裁定 2026-09-19:名称数据只允许存在于 SQL 预置与应用内编辑,
-- 应用代码 MUST NOT 包含名称预置数据、不做运行时 Locale 解析。
-- 语言 UUID 为运行时生成,故按 language_identifier 关联标准表取 id;
-- 既有库(初始化器已补种 22 行)由 NOT EXISTS 幂等跳过,不清表重插。
-- 应用代码(ApplicationRunner)与 Flyway 的执行顺序:本脚本先于语言播种执行,
-- 全新库首批启动时无标准语言行可关联,预置名称在下次迁移/升级或手工补录时生效。


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
