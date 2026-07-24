-- Add index on fhir_element.administered_item_id for compliance gap analysis queries (WI-012-3)
-- SQL Server dialect

CREATE INDEX idx_fhir_elm_admin_item ON fhir_element(administered_item_id);
