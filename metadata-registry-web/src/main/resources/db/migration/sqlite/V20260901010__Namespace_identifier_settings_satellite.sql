-- 2.3 Namespace 标识符治理扩展属性剥为卫星实体(ISO/IEC 11179 标准实体只留标准属性)
-- 建表 + 从 namespace 回填 + 删除 4 个旧列。
CREATE TABLE namespace_identifier_settings (
    id blob not null,
    namespace_id blob not null,
    versioning_policy varchar(32),
    identifier_input_mode varchar(32),
    version_input_mode varchar(32),
    manual_identifier_pattern varchar(255),
    primary key (id)
);
CREATE UNIQUE INDEX uk_nis_namespace ON namespace_identifier_settings (namespace_id);

INSERT INTO namespace_identifier_settings
    (id, namespace_id, versioning_policy, identifier_input_mode,
     version_input_mode, manual_identifier_pattern)
SELECT randomblob(16), id, versioning_policy, identifier_input_mode,
       version_input_mode, manual_identifier_pattern
FROM namespace;

ALTER TABLE namespace DROP COLUMN versioning_policy;
ALTER TABLE namespace DROP COLUMN identifier_input_mode;
ALTER TABLE namespace DROP COLUMN version_input_mode;
ALTER TABLE namespace DROP COLUMN manual_identifier_pattern;
