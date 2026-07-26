-- ISO/IEC 11179-3:2023 9.4.10 Registry_Specification
CREATE TABLE registry_specification (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    name NVARCHAR(255) NOT NULL,
    comment NVARCHAR(4000) NULL,
    web_address NVARCHAR(512) NULL,
    default_language_id UNIQUEIDENTIFIER NULL,
    character_repertoire NVARCHAR(255) NOT NULL DEFAULT 'UTF-8',
    representation_class_scheme_id NVARCHAR(255) NULL,
    context_id UNIQUEIDENTIFIER NULL,
    standard NVARCHAR(255) NULL,
    degree_of_conformance NVARCHAR(32) NULL,
    conformance_level NVARCHAR(255) NULL,
    conformance_label NVARCHAR(255) NULL,
    included_features NVARCHAR(4000) NULL,
    excluded_features NVARCHAR(4000) NULL,
    default_time_zone NVARCHAR(8) NULL
);

CREATE TABLE registry_specification_alternative_language (
    registry_specification_id UNIQUEIDENTIFIER NOT NULL,
    language_id UNIQUEIDENTIFIER NOT NULL,
    PRIMARY KEY (registry_specification_id, language_id)
);

CREATE TABLE registry_specification_reference_doc_id_kind (
    registry_specification_id UNIQUEIDENTIFIER NOT NULL,
    identifier_kind NVARCHAR(255) NOT NULL
);
