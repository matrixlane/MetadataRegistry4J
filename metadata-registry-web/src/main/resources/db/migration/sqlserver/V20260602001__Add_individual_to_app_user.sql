-- Add individual_id column to app_user table and create foreign key constraint
ALTER TABLE app_user ADD individual_id UNIQUEIDENTIFIER NULL;
ALTER TABLE app_user ADD CONSTRAINT fk_app_user_individual FOREIGN KEY (individual_id) REFERENCES individual(id);
