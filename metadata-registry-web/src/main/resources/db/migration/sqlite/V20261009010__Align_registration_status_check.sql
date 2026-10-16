-- ============================================================================
-- V20261009010__Align_registration_status_check.sql  (SQLite)
--
-- repair-administered-item-closure（2026-10-09）：registration_status 的 CHECK
-- 约束对齐 ISO/IEC 11179-3:2023 表 30（9.6.2）/ ISO/IEC 11179-6:2023 表 1（4.3.2）
-- 的十个标准状态。与 SQL Server 版同源，差异仅在 SQLite 不支持
-- ALTER TABLE ... ADD/DROP CONSTRAINT，须重建表。
--
-- 【阻断项】实测 SQLite 库现约束同样为旧值集：
--     check (registration_status in ('INCOMPLETE','SUBMITTED','RECORDED','QUALIFIED',
--                                      'STANDARD','PREFERRED','SUPERSEDED','RETIRED',
--                                      'HISTORICAL','APPLICATION'))
-- 缺 CANDIDATE / PREFERRED_STANDARD，多出标准中不存在的 SUBMITTED / PREFERRED。
-- PREFERRED_STANDARD 写入被拒。
--
-- 步骤：
--   1) 重建表（建新表 → INSERT…SELECT → DROP → RENAME → 补索引）；
--   2) 归位在 INSERT…SELECT 的投影里完成（CASE 表达式）。
--
-- 顺序要点（2026-10-09 验证期修正）：旧 CHECK 的允许集里没有
-- CANDIDATE / PREFERRED_STANDARD，若先 UPDATE 归位再重建，会被旧约束自身拒绝
-- （CHECK constraint failed）形成死锁；SQLite 又无法单独 DROP 约束。
-- 故归位 MUST 折进拷贝投影：新表的 CHECK 校验的是已归位的值，两边都不冲突。
-- SQL Server 版同理采用"先删约束、再归位、再加约束"的次序。
--
-- 可重入性：无条件重建。第二次执行时源表已是标准值集且不含 SUBMITTED/PREFERRED，
-- CASE 分支不命中，重建结果与第一次一致，故重跑安全（幂等指"结果不变"，
-- 非"第二次不做事"）。Flyway 按版本号只执行一次，本段仅为手工重跑兜底。
--
-- 保留项：id 主键与 previous_state_id 的 UNIQUE（列声明自动生成
-- sqlite_autoindex_*）；administrative_status 索引显式 IF NOT EXISTS 重建。
-- 实测（pragma_foreign_key_list）该表无任何外键声明，administration_item 仅以
-- registration_state_id 列名引用、不带 REFERENCES，故 DROP TABLE 不触发外键级联。
--
-- 书写形式：与 SQL Server 版一致，upper(replace(_, ' ')) 归一后比对，
-- 使枚举常量名（PREFERRED_STANDARD）与标准书写形式（Preferred Standard）均放行。
-- ============================================================================

-- 1. 重建表
DROP TABLE IF EXISTS registration_state_v20261009010_new;

CREATE TABLE registration_state_v20261009010_new (
    id blob not null,
    administrative_note varchar(255),
    administrative_status varchar(255),
    effective_date timestamp,
    registration_status varchar(255) check (
        upper(replace(registration_status, '_', ' ')) in (
            'INCOMPLETE', 'CANDIDATE', 'RECORDED', 'QUALIFIED', 'STANDARD',
            'PREFERRED STANDARD', 'SUPERSEDED', 'RETIRED', 'HISTORICAL', 'APPLICATION'
        )
    ),
    unresolved_issue varchar(255),
    until_date timestamp,
    previous_state_id blob unique,
    primary key (id)
);

-- 归位折进拷贝投影：SUBMITTED→CANDIDATE、PREFERRED→PREFERRED_STANDARD。
-- 新表的 CHECK 校验的是已归位的值，源表的旧 CHECK 则在 DROP 时随表消失，
-- 两个约束在任何时刻都不会看到对方不接受的中间态。
INSERT INTO registration_state_v20261009010_new
    (id, administrative_note, administrative_status, effective_date,
     registration_status, unresolved_issue, until_date, previous_state_id)
SELECT id, administrative_note, administrative_status, effective_date,
       CASE UPPER(registration_status)
           WHEN 'SUBMITTED' THEN 'CANDIDATE'
           WHEN 'PREFERRED' THEN 'PREFERRED_STANDARD'
           ELSE registration_status
       END,
       unresolved_issue, until_date, previous_state_id
  FROM registration_state;

DROP TABLE registration_state;

ALTER TABLE registration_state_v20261009010_new RENAME TO registration_state;

-- 2. 索引重建（PRIMARY KEY 与 previous_state_id UNIQUE 由 sqlite_autoindex 自动生成）
CREATE INDEX IF NOT EXISTS idx_registration_state_administrative_status
    ON registration_state (administrative_status);