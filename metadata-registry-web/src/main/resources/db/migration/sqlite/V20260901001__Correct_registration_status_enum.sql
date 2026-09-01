-- 修正迁移:RegistrationStatus 与 ISO/IEC 11179-3:2023 Table 30 / 11179-6:2023 Table 1 对齐(幂等)
UPDATE registration_state SET registration_status = 'CANDIDATE'
WHERE registration_status = 'SUBMITTED';
UPDATE registration_state SET registration_status = 'PREFERRED_STANDARD'
WHERE registration_status = 'PREFERRED';
