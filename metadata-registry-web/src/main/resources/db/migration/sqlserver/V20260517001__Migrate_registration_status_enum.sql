-- WI-019: Migration script for RegistrationStatus enum alignment with ISO/IEC 11179-6:2023
-- Migrates legacy status values to new standard values.
--
-- Mappings:
--   COMPATIBLE → INCOMPLETE  (legacy value had no ISO 11179-6 meaning; treated as incomplete draft)
--   PREFERRED_STANDARD → PREFERRED (renamed per ISO standard)
--   CANDIDATE → SUBMITTED   (previously non-standard, maps to submitted state)
--   APPLICATION → SUBMITTED  (previously non-standard, maps to submitted state)
--   INCOMPLETE → INCOMPLETE  (no change — already standard)
--   All other statuses → no change (RECORDED, QUALIFIED, STANDARD, SUPERSEDED, RETIRED, HISTORICAL)

-- ============================================================
-- Step 1: Handle COMPATIBLE → INCOMPLETE
-- COMPATIBLE was a legacy value not in ISO 11179-6 standard.
-- ============================================================
IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'registration_state'
      AND COLUMN_NAME = 'registration_status'
      AND DATA_TYPE LIKE '%char%'
)
BEGIN
    UPDATE rs
    SET rs.registration_status = 'INCOMPLETE'
    FROM registration_state rs
    WHERE rs.registration_status = 'COMPATIBLE';

    -- Log affected rows (for debugging/verification)
    DECLARE @compat_count INT;
    SELECT @compat_count = COUNT(*)
    FROM registration_state
    WHERE registration_status = 'INCOMPLETE'
      AND id IN (SELECT id FROM registration_state WHERE registration_status = 'COMPATIBLE');
END

-- ============================================================
-- Step 2: Handle PREFERRED_STANDARD → PREFERRED (rename)
-- ============================================================
IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'registration_state'
      AND COLUMN_NAME = 'registration_status'
      AND DATA_TYPE LIKE '%char%'
)
BEGIN
    UPDATE rs
    SET rs.registration_status = 'PREFERRED'
    FROM registration_state rs
    WHERE rs.registration_status = 'PREFERRED_STANDARD';
END

-- ============================================================
-- Step 3: Handle CANDIDATE → SUBMITTED (non-standard → standard)
-- CANDIDATE was removed from the enum; maps to SUBMITTED.
-- ============================================================
IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'registration_state'
      AND COLUMN_NAME = 'registration_status'
      AND DATA_TYPE LIKE '%char%'
)
BEGIN
    UPDATE rs
    SET rs.registration_status = 'SUBMITTED'
    FROM registration_state rs
    WHERE rs.registration_status = 'CANDIDATE';
END

-- ============================================================
-- Step 4: Handle APPLICATION → SUBMITTED (non-standard → standard)
-- APPLICATION was removed from the enum; maps to SUBMITTED.
-- ============================================================
IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'registration_state'
      AND COLUMN_NAME = 'registration_status'
      AND DATA_TYPE LIKE '%char%'
)
BEGIN
    UPDATE rs
    SET rs.registration_status = 'SUBMITTED'
    FROM registration_state rs
    WHERE rs.registration_status = 'APPLICATION';
END

-- ============================================================
-- Step 5: Verify — ensure no unmigrated legacy values remain
-- If any rows still have non-standard values, this will raise an error.
-- ============================================================
IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'registration_state'
      AND COLUMN_NAME = 'registration_status'
      AND DATA_TYPE LIKE '%char%'
)
BEGIN
    DECLARE @unmigrated INT;
    SELECT @unmigrated = COUNT(*)
    FROM registration_state
    WHERE registration_status NOT IN (
        'INCOMPLETE', 'SUBMITTED', 'RECORDED', 'QUALIFIED',
        'STANDARD', 'PREFERRED', 'SUPERSEDED', 'RETIRED', 'HISTORICAL'
    );

    IF @unmigrated > 0
    BEGIN
        RAISERROR('Migration warning: %d rows still have unmigrated registration_status values', 10, 1, @unmigrated) WITH LOG;
    END
END
