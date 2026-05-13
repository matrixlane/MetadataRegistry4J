-- FHIR Resource/Element import & browse tables for WI-012-1
-- SQL Server dialect

CREATE TABLE fhir_resource (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    fhir_id NVARCHAR(200) NOT NULL,
    url NVARCHAR(500),
    name NVARCHAR(200) NOT NULL,
    title NVARCHAR(500),
    status NVARCHAR(50),
    version NVARCHAR(50),
    fhir_version NVARCHAR(20) NOT NULL,
    kind NVARCHAR(50),
    is_abstract BIT,
    type NVARCHAR(200),
    description NVARCHAR(MAX),
    publisher NVARCHAR(500),
    date NVARCHAR(50),
    imported_at DATETIME2 NOT NULL,
    administered_item_id UNIQUEIDENTIFIER
);

CREATE INDEX idx_fhir_res_name ON fhir_resource(name);
CREATE INDEX idx_fhir_res_url ON fhir_resource(url);
CREATE INDEX idx_fhir_res_kind ON fhir_resource(kind);
CREATE INDEX idx_fhir_res_fhir_version ON fhir_resource(fhir_version);

CREATE TABLE fhir_element (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    fhir_resource_id UNIQUEIDENTIFIER NOT NULL,
    path NVARCHAR(500) NOT NULL,
    short_desc NVARCHAR(500),
    definition NVARCHAR(MAX),
    min INT,
    max NVARCHAR(20),
    element_type NVARCHAR(500),
    is_summary BIT,
    is_modifier BIT,
    order_index INT,
    alias NVARCHAR(500),
    condition NVARCHAR(50),
    administered_item_id UNIQUEIDENTIFIER,
    CONSTRAINT fk_fhir_element_resource FOREIGN KEY (fhir_resource_id) REFERENCES fhir_resource(id)
);

CREATE INDEX idx_fhir_elm_resource_id ON fhir_element(fhir_resource_id);
CREATE INDEX idx_fhir_elm_path ON fhir_element(path);
CREATE INDEX idx_fhir_elm_type ON fhir_element(element_type);

CREATE TABLE fhir_value_set (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    fhir_id NVARCHAR(200) NOT NULL,
    url NVARCHAR(500),
    name NVARCHAR(200) NOT NULL,
    title NVARCHAR(500),
    status NVARCHAR(50),
    version NVARCHAR(50),
    fhir_version NVARCHAR(20) NOT NULL,
    description NVARCHAR(MAX),
    publisher NVARCHAR(500),
    imported_at DATETIME2 NOT NULL
);

CREATE INDEX idx_fhir_vs_url ON fhir_value_set(url);
CREATE INDEX idx_fhir_vs_name ON fhir_value_set(name);
CREATE INDEX idx_fhir_vs_fhir_version ON fhir_value_set(fhir_version);

CREATE TABLE fhir_code_system (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    fhir_id NVARCHAR(200) NOT NULL,
    url NVARCHAR(500),
    name NVARCHAR(200) NOT NULL,
    title NVARCHAR(500),
    status NVARCHAR(50),
    version NVARCHAR(50),
    fhir_version NVARCHAR(20) NOT NULL,
    description NVARCHAR(MAX),
    publisher NVARCHAR(500),
    concept_json NVARCHAR(MAX),
    imported_at DATETIME2 NOT NULL
);

CREATE INDEX idx_fhir_cs_url ON fhir_code_system(url);
CREATE INDEX idx_fhir_cs_name ON fhir_code_system(name);
CREATE INDEX idx_fhir_cs_fhir_version ON fhir_code_system(fhir_version);

CREATE TABLE fhir_binding (
    id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
    fhir_element_id UNIQUEIDENTIFIER NOT NULL,
    fhir_value_set_id UNIQUEIDENTIFIER,
    strength NVARCHAR(50),
    description NVARCHAR(MAX),
    value_set_url NVARCHAR(500),
    CONSTRAINT fk_fhir_binding_element FOREIGN KEY (fhir_element_id) REFERENCES fhir_element(id),
    CONSTRAINT fk_fhir_binding_valueset FOREIGN KEY (fhir_value_set_id) REFERENCES fhir_value_set(id)
);

CREATE INDEX idx_fhir_bind_element ON fhir_binding(fhir_element_id);
CREATE INDEX idx_fhir_bind_valueset ON fhir_binding(fhir_value_set_id);
CREATE INDEX idx_fhir_bind_strength ON fhir_binding(strength);
