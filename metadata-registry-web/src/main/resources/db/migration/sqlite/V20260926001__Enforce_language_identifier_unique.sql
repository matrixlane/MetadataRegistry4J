-- Enforce UNIQUE(language_identifier) on language_identification:
-- ISO/IEC 11179-3:2023 6.3.7 Language_Identification 的自然键是 language_identifier
-- (BCP 47 tag,例如 zh-CN/en-US/id-ID),业务上同一注册机构作用域内唯一。本次迁移施加
-- DB 唯一约束,防止外部代码 INSERT 重复行导致启动期 NonUniqueResultException 崩溃
-- (2026-09-25 23:59 事故链)。
--
-- 三方言一致:先按 language_identifier 分组保留 id 最小行,把各 FK 引用表的 language_id
-- 字段重定向到保留行,然后删除多余行,最后 CREATE UNIQUE INDEX。
-- SQLite 没有 schema 概念,所有表直接用。SQLite V20260723002 baseline 含 7 张 mdr 引用表
-- (definition/designation/language_extension/language_variant/reference_document_language/
-- registration_authority_doc_language/registry_specification)+ ext_language_identification
-- (V20260919001 创,SQLite 下划线替代点号)。registry_specification_alternative_language 在
-- SQLite 下由 Hibernate ddl-auto=update 运行时创建,本次迁移不主动 UPDATE——若该表存在
-- 但其 language_id 引用指向被丢弃的孤儿行,DB 层 FK 约束(若有)或应用层 lazy load 会报
-- EntityNotFoundException;该场景极少见且不影响本次启动阻塞修复,留作后续 follow-up。

-- 1) 重定向 8 张 FK 引用表的 language_id 指向保留行(MIN(id))

UPDATE reference_document_language
SET language_id = (
    SELECT MIN(li.id) FROM language_identification li
    WHERE li.language_identifier = (
        SELECT li2.language_identifier FROM language_identification li2 WHERE li2.id = reference_document_language.language_id
    )
)
WHERE language_id IS NOT NULL
  AND language_id NOT IN (SELECT MIN(li.id) FROM language_identification li GROUP BY li.language_identifier);

UPDATE registration_authority_doc_language
SET language_id = (
    SELECT MIN(li.id) FROM language_identification li
    WHERE li.language_identifier = (
        SELECT li2.language_identifier FROM language_identification li2 WHERE li2.id = registration_authority_doc_language.language_id
    )
)
WHERE language_id IS NOT NULL
  AND language_id NOT IN (SELECT MIN(li.id) FROM language_identification li GROUP BY li.language_identifier);

UPDATE registry_specification
SET default_language_id = (
    SELECT MIN(li.id) FROM language_identification li
    WHERE li.language_identifier = (
        SELECT li2.language_identifier FROM language_identification li2 WHERE li2.id = registry_specification.default_language_id
    )
)
WHERE default_language_id IS NOT NULL
  AND default_language_id NOT IN (SELECT MIN(li.id) FROM language_identification li GROUP BY li.language_identifier);

UPDATE definition
SET language_id = (
    SELECT MIN(li.id) FROM language_identification li
    WHERE li.language_identifier = (
        SELECT li2.language_identifier FROM language_identification li2 WHERE li2.id = definition.language_id
    )
)
WHERE language_id IS NOT NULL
  AND language_id NOT IN (SELECT MIN(li.id) FROM language_identification li GROUP BY li.language_identifier);

UPDATE designation
SET language_id = (
    SELECT MIN(li.id) FROM language_identification li
    WHERE li.language_identifier = (
        SELECT li2.language_identifier FROM language_identification li2 WHERE li2.id = designation.language_id
    )
)
WHERE language_id IS NOT NULL
  AND language_id NOT IN (SELECT MIN(li.id) FROM language_identification li GROUP BY li.language_identifier);

UPDATE language_extension
SET language_id = (
    SELECT MIN(li.id) FROM language_identification li
    WHERE li.language_identifier = (
        SELECT li2.language_identifier FROM language_identification li2 WHERE li2.id = language_extension.language_id
    )
)
WHERE language_id IS NOT NULL
  AND language_id NOT IN (SELECT MIN(li.id) FROM language_identification li GROUP BY li.language_identifier);

UPDATE language_variant
SET language_id = (
    SELECT MIN(li.id) FROM language_identification li
    WHERE li.language_identifier = (
        SELECT li2.language_identifier FROM language_identification li2 WHERE li2.id = language_variant.language_id
    )
)
WHERE language_id IS NOT NULL
  AND language_id NOT IN (SELECT MIN(li.id) FROM language_identification li GROUP BY li.language_identifier);

UPDATE ext_language_identification
SET language_id = (
    SELECT MIN(li.id) FROM language_identification li
    WHERE li.language_identifier = (
        SELECT li2.language_identifier FROM language_identification li2 WHERE li2.id = ext_language_identification.language_id
    )
)
WHERE language_id IS NOT NULL
  AND language_id NOT IN (SELECT MIN(li.id) FROM language_identification li GROUP BY li.language_identifier);

-- 2) 删除多余行(每组保留 MIN(id))
DELETE FROM language_identification
WHERE id NOT IN (SELECT MIN(id) FROM language_identification GROUP BY language_identifier);

-- 3) 建 UNIQUE INDEX(幂等:索引已存在则跳过)
CREATE UNIQUE INDEX IF NOT EXISTS uq_language_identification_identifier
    ON language_identification(language_identifier);
