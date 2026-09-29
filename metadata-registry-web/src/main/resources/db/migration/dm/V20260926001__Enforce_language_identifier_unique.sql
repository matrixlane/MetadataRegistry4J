-- Enforce UNIQUE(language_identifier) on MDR.language_identification:
-- ISO/IEC 11179-3:2023 6.3.7 Language_Identification 的自然键是 language_identifier
-- (BCP 47 tag,例如 zh-CN/en-US/id-ID),业务上同一注册机构作用域内唯一。本次迁移施加
-- DB 唯一约束,防止外部代码 INSERT 重复行导致启动期 NonUniqueResultException 崩溃
-- (2026-09-25 23:59 事故链)。
--
-- 三方言一致:先按 language_identifier 分组保留 id 最小行,把 9 张 FK 引用表的
-- language_id 字段重定向到保留行,然后删除多余行,最后 CREATE UNIQUE INDEX。
-- 达梦方言 PL/SQL 块,DM8 支持窗口函数;与 SQL Server 语义对齐。

BEGIN
  EXECUTE IMMEDIATE q'[
    ;WITH ranked AS (
        SELECT id, language_identifier,
               ROW_NUMBER() OVER (PARTITION BY language_identifier ORDER BY id) AS rn
        FROM MDR.language_identification
    ),
    keepers AS (SELECT id, language_identifier FROM ranked WHERE rn = 1),
    losers  AS (SELECT id, language_identifier FROM ranked WHERE rn > 1)

    UPDATE MDR.reference_document_language rdl
    SET language_id = (SELECT k.id FROM keepers k WHERE k.language_identifier = (SELECT li.language_identifier FROM MDR.language_identification li WHERE li.id = rdl.language_id))
    WHERE rdl.language_id IN (SELECT id FROM losers);

    UPDATE MDR.registration_authority_doc_language radl
    SET language_id = (SELECT k.id FROM keepers k WHERE k.language_identifier = (SELECT li.language_identifier FROM MDR.language_identification li WHERE li.id = radl.language_id))
    WHERE radl.language_id IN (SELECT id FROM losers);

    UPDATE MDR.registry_specification_alternative_language rsal
    SET language_id = (SELECT k.id FROM keepers k WHERE k.language_identifier = (SELECT li.language_identifier FROM MDR.language_identification li WHERE li.id = rsal.language_id))
    WHERE rsal.language_id IN (SELECT id FROM losers);

    UPDATE MDR.registry_specification rs
    SET default_language_id = (SELECT k.id FROM keepers k WHERE k.language_identifier = (SELECT li.language_identifier FROM MDR.language_identification li WHERE li.id = rs.default_language_id))
    WHERE rs.default_language_id IN (SELECT id FROM losers);

    UPDATE MDR.definition d
    SET language_id = (SELECT k.id FROM keepers k WHERE k.language_identifier = (SELECT li.language_identifier FROM MDR.language_identification li WHERE li.id = d.language_id))
    WHERE d.language_id IN (SELECT id FROM losers);

    UPDATE MDR.designation ds
    SET language_id = (SELECT k.id FROM keepers k WHERE k.language_identifier = (SELECT li.language_identifier FROM MDR.language_identification li WHERE li.id = ds.language_id))
    WHERE ds.language_id IN (SELECT id FROM losers);

    UPDATE MDR.language_extension le
    SET language_id = (SELECT k.id FROM keepers k WHERE k.language_identifier = (SELECT li.language_identifier FROM MDR.language_identification li WHERE li.id = le.language_id))
    WHERE le.language_id IN (SELECT id FROM losers);

    UPDATE MDR.language_variant lv
    SET language_id = (SELECT k.id FROM keepers k WHERE k.language_identifier = (SELECT li.language_identifier FROM MDR.language_identification li WHERE li.id = lv.language_id))
    WHERE lv.language_id IN (SELECT id FROM losers);

    UPDATE EXT.language_identification eli
    SET language_id = (SELECT k.id FROM keepers k WHERE k.language_identifier = (SELECT li.language_identifier FROM MDR.language_identification li WHERE li.id = eli.language_id))
    WHERE eli.language_id IN (SELECT id FROM losers);

    DELETE FROM MDR.language_identification vh
    WHERE vh.id IN (SELECT id FROM losers);
  ]';
EXCEPTION WHEN OTHERS THEN
  RAISE;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX uq_language_identification_identifier ON MDR.language_identification(language_identifier)';
EXCEPTION WHEN OTHERS THEN
  IF SQLCODE != -5105 THEN RAISE; END IF;  -- 索引已存在则忽略
END;
/
