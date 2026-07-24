-- Add enabled column to language_identification table
ALTER TABLE language_identification ADD enabled BIT NOT NULL DEFAULT 1;
