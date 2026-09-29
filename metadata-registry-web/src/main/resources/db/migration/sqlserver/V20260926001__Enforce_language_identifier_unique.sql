-- Enforce UNIQUE(language_identifier) on mdr.language_identification:
-- ISO/IEC 11179-3:2023 6.3.7 Language_Identification 的自然键是 language_identifier
-- (BCP 47 tag,例如 zh-CN/en-US/id-ID),业务上同一注册机构作用域内唯一。本次迁移施加
-- DB 唯一约束,防止外部代码 INSERT 重复行导致启动期 NonUniqueResultException 崩溃
-- (2026-09-25 23:59 事故链)。
--
-- 三方言一致:先按 language_identifier 分组保留 id 最小行,把 9 张 FK 引用表的
-- language_id 字段重定向到保留行,然后删除多余行,最后 CREATE UNIQUE INDEX。
-- SQL Server 有 mdr./ext. schema 分隔;CTE 仅在单个语句内有效,每个 UPDATE / DELETE
-- 独立嵌入子查询派生表。

-- 1) 重定向 9 张 FK 引用表的 language_id 指向保留行(MIN(id))

UPDATE rdl SET rdl.language_id = k.id
FROM mdr.reference_document_language rdl
JOIN (
    SELECT li.id, li.language_identifier
    FROM mdr.language_identification li
    WHERE li.id IN (
        SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
    )
) k ON k.language_identifier = (
    SELECT li2.language_identifier FROM mdr.language_identification li2 WHERE li2.id = rdl.language_id
)
WHERE rdl.language_id IS NOT NULL
  AND rdl.language_id NOT IN (
      SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
  );

UPDATE radl SET radl.language_id = k.id
FROM mdr.registration_authority_doc_language radl
JOIN (
    SELECT li.id, li.language_identifier
    FROM mdr.language_identification li
    WHERE li.id IN (
        SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
    )
) k ON k.language_identifier = (
    SELECT li2.language_identifier FROM mdr.language_identification li2 WHERE li2.id = radl.language_id
)
WHERE radl.language_id IS NOT NULL
  AND radl.language_id NOT IN (
      SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
  );

UPDATE rsal SET rsal.language_id = k.id
FROM mdr.registry_specification_alternative_language rsal
JOIN (
    SELECT li.id, li.language_identifier
    FROM mdr.language_identification li
    WHERE li.id IN (
        SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
    )
) k ON k.language_identifier = (
    SELECT li2.language_identifier FROM mdr.language_identification li2 WHERE li2.id = rsal.language_id
)
WHERE rsal.language_id IS NOT NULL
  AND rsal.language_id NOT IN (
      SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
  );

UPDATE rs SET rs.default_language_id = k.id
FROM mdr.registry_specification rs
JOIN (
    SELECT li.id, li.language_identifier
    FROM mdr.language_identification li
    WHERE li.id IN (
        SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
    )
) k ON k.language_identifier = (
    SELECT li2.language_identifier FROM mdr.language_identification li2 WHERE li2.id = rs.default_language_id
)
WHERE rs.default_language_id IS NOT NULL
  AND rs.default_language_id NOT IN (
      SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
  );

UPDATE d SET d.language_id = k.id
FROM mdr.definition d
JOIN (
    SELECT li.id, li.language_identifier
    FROM mdr.language_identification li
    WHERE li.id IN (
        SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
    )
) k ON k.language_identifier = (
    SELECT li2.language_identifier FROM mdr.language_identification li2 WHERE li2.id = d.language_id
)
WHERE d.language_id IS NOT NULL
  AND d.language_id NOT IN (
      SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
  );

UPDATE ds SET ds.language_id = k.id
FROM mdr.designation ds
JOIN (
    SELECT li.id, li.language_identifier
    FROM mdr.language_identification li
    WHERE li.id IN (
        SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
    )
) k ON k.language_identifier = (
    SELECT li2.language_identifier FROM mdr.language_identification li2 WHERE li2.id = ds.language_id
)
WHERE ds.language_id IS NOT NULL
  AND ds.language_id NOT IN (
      SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
  );

UPDATE le SET le.language_id = k.id
FROM mdr.language_extension le
JOIN (
    SELECT li.id, li.language_identifier
    FROM mdr.language_identification li
    WHERE li.id IN (
        SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
    )
) k ON k.language_identifier = (
    SELECT li2.language_identifier FROM mdr.language_identification li2 WHERE li2.id = le.language_id
)
WHERE le.language_id IS NOT NULL
  AND le.language_id NOT IN (
      SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
  );

UPDATE lv SET lv.language_id = k.id
FROM mdr.language_variant lv
JOIN (
    SELECT li.id, li.language_identifier
    FROM mdr.language_identification li
    WHERE li.id IN (
        SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
    )
) k ON k.language_identifier = (
    SELECT li2.language_identifier FROM mdr.language_identification li2 WHERE li2.id = lv.language_id
)
WHERE lv.language_id IS NOT NULL
  AND lv.language_id NOT IN (
      SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
  );

UPDATE eli SET eli.language_id = k.id
FROM ext.language_identification eli
JOIN (
    SELECT li.id, li.language_identifier
    FROM mdr.language_identification li
    WHERE li.id IN (
        SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
    )
) k ON k.language_identifier = (
    SELECT li2.language_identifier FROM mdr.language_identification li2 WHERE li2.id = eli.language_id
)
WHERE eli.language_id IS NOT NULL
  AND eli.language_id NOT IN (
      SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
  );

-- 2) 删除多余行(每组保留 MIN(id))
DELETE vh
FROM mdr.language_identification vh
WHERE vh.id NOT IN (
    SELECT MIN(id) FROM mdr.language_identification GROUP BY language_identifier
);

-- 3) 建 UNIQUE INDEX(幂等:索引已存在则跳过)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'uq_language_identification_identifier' AND object_id = OBJECT_ID('mdr.language_identification'))
BEGIN
    CREATE UNIQUE INDEX uq_language_identification_identifier
        ON mdr.language_identification(language_identifier);
END
