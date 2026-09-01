-- 3.2 LanguageIdentification.enabled 扩展属性剥为卫星实体(ISO/IEC 11179 标准实体只留标准属性)
-- 建表 + 从 language_identification 回填 + 删除旧列。
CREATE TABLE language_governance (
    id blob not null,
    language_id blob not null,
    enabled boolean not null default true,
    primary key (id)
);
CREATE UNIQUE INDEX uk_lg_language ON language_governance (language_id);

INSERT INTO language_governance (id, language_id, enabled)
SELECT randomblob(16), id, coalesce(enabled, true)
FROM language_identification;

ALTER TABLE language_identification DROP COLUMN enabled;
