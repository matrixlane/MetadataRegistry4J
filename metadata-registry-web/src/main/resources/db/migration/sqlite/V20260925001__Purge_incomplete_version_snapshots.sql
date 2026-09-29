-- Purge version_history snapshots for items still in Incomplete registration state.
-- ISO/IEC 11179-6:2023 4.3.3.1.2 规定 Incomplete 不入版本控制。
-- 本迁移清理 11179-6 字面对齐前已为 Incomplete 项写入的 CREATE 快照，使库状态与新规则一致。
DELETE FROM version_history
WHERE item_id IN (
    SELECT ai.id
    FROM administered_item ai
    INNER JOIN registration_state rs ON rs.id = ai.registration_state_id
    WHERE rs.registration_status = 'INCOMPLETE'
);
