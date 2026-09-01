-- 修正迁移:RegistrationStatus 与 ISO/IEC 11179-3:2023 Table 30 / 11179-6:2023 Table 1 对齐
-- 此前的 V20260517001 方向错误(把标准状态 CANDIDATE/PREFERRED_STANDARD 迁往标准外的 SUBMITTED/PREFERRED)。
-- 本脚本反向纠正,恢复标准值集。标准值集:
--   Incomplete / Candidate / Recorded / Qualified / Standard / Preferred Standard /
--   Superseded / Retired / Historical / Application
-- 幂等:仅在相应旧值存在时更新。现库若已为 'Standard' 等标准格式则为 no-op。

-- SUBMITTED → CANDIDATE(11179-3 Table 30 第二级;SUBMITTED 标准中不存在)
IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'registration_state' AND COLUMN_NAME = 'registration_status'
)
BEGIN
    UPDATE registration_state SET registration_status = 'CANDIDATE'
    WHERE registration_status = 'SUBMITTED';
END

-- PREFERRED → PREFERRED_STANDARD(标准状态名 "Preferred Standard",Java 枚举名 PREFERRED_STANDARD)
IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'registration_state' AND COLUMN_NAME = 'registration_status'
)
BEGIN
    UPDATE registration_state SET registration_status = 'PREFERRED_STANDARD'
    WHERE registration_status = 'PREFERRED';
END
