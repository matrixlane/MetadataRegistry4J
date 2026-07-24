CREATE TABLE registry_specification (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    registry_name NVARCHAR(255) NOT NULL,
    identifier_prefix NVARCHAR(50) NULL,
    auto_generate_identifier BIT NOT NULL DEFAULT 1,
    identifier_pattern NVARCHAR(255) NULL,
    versioning_policy NVARCHAR(50) NULL,
    mandatory_classification BIT NOT NULL DEFAULT 0,
    default_language NVARCHAR(50) NOT NULL DEFAULT 'zh-CN',
    description NVARCHAR(1000) NULL
);
