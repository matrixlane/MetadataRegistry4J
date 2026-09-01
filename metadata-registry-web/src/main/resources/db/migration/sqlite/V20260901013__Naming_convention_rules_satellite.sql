-- 3.3 NamingConvention.rulesJson 扩展属性剥为卫星实体(ISO/IEC 11179 标准实体只留标准属性)
-- 建表 + 从 naming_convention 回填 + 删除旧列。
CREATE TABLE naming_convention_rules (
    id blob not null,
    naming_convention_id blob not null,
    rules_json text,
    primary key (id)
);
CREATE UNIQUE INDEX uk_ncr_convention ON naming_convention_rules (naming_convention_id);

INSERT INTO naming_convention_rules (id, naming_convention_id, rules_json)
SELECT randomblob(16), id, rules_json
FROM naming_convention
WHERE rules_json IS NOT NULL;

ALTER TABLE naming_convention DROP COLUMN rules_json;
