-- 注册流程（registration-process-designer）：迁移规则 + 审批记录。
-- 规则按注册机构作用域，标准默认值由应用层 RegistrationProcessService.ensureSeed 幂等播种。
-- 枚举 CHECK 约束与 sqlite 脚本对齐。
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'registration_transition_rule'
)
BEGIN
    CREATE TABLE mdr.registration_transition_rule (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        registration_authority_id UNIQUEIDENTIFIER NOT NULL,
        from_status NVARCHAR(32) NOT NULL,
        to_status NVARCHAR(32) NOT NULL,
        display_name NVARCHAR(255) NULL,
        requires_approval BIT NOT NULL,
        approver_mode NVARCHAR(32) NOT NULL,
        quorum_type NVARCHAR(16) NOT NULL,
        quorum_count INT NULL,
        enabled BIT NOT NULL,
        CONSTRAINT ck_rtr_from_status CHECK (from_status IN ('INCOMPLETE','CANDIDATE','RECORDED','QUALIFIED','STANDARD','PREFERRED_STANDARD','SUPERSEDED','RETIRED','HISTORICAL','APPLICATION')),
        CONSTRAINT ck_rtr_to_status CHECK (to_status IN ('INCOMPLETE','CANDIDATE','RECORDED','QUALIFIED','STANDARD','PREFERRED_STANDARD','SUPERSEDED','RETIRED','HISTORICAL','APPLICATION')),
        CONSTRAINT ck_rtr_approver_mode CHECK (approver_mode IN ('ALL_REGISTRARS','SELECTED_REGISTRARS')),
        CONSTRAINT ck_rtr_quorum_type CHECK (quorum_type IN ('ANY_ONE','AT_LEAST'))
    );
    CREATE UNIQUE INDEX uk_rtr_authority_from_to
        ON mdr.registration_transition_rule (registration_authority_id, from_status, to_status);
END;

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'registration_transition_rule_approver'
)
BEGIN
    CREATE TABLE mdr.registration_transition_rule_approver (
        rule_id UNIQUEIDENTIFIER NOT NULL,
        registrar_id UNIQUEIDENTIFIER NOT NULL
    );
    CREATE UNIQUE INDEX uk_rtra_rule_registrar
        ON mdr.registration_transition_rule_approver (rule_id, registrar_id);
END;

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'registration_transition_approval'
)
BEGIN
    CREATE TABLE mdr.registration_transition_approval (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        administered_item_id UNIQUEIDENTIFIER NOT NULL,
        from_status NVARCHAR(32) NOT NULL,
        to_status NVARCHAR(32) NOT NULL,
        registrar_id UNIQUEIDENTIFIER NOT NULL,
        account_id UNIQUEIDENTIFIER NULL,
        approved_at DATETIME2 NOT NULL,
        CONSTRAINT ck_rta_from_status CHECK (from_status IN ('INCOMPLETE','CANDIDATE','RECORDED','QUALIFIED','STANDARD','PREFERRED_STANDARD','SUPERSEDED','RETIRED','HISTORICAL','APPLICATION')),
        CONSTRAINT ck_rta_to_status CHECK (to_status IN ('INCOMPLETE','CANDIDATE','RECORDED','QUALIFIED','STANDARD','PREFERRED_STANDARD','SUPERSEDED','RETIRED','HISTORICAL','APPLICATION'))
    );
    CREATE UNIQUE INDEX uk_rta_item_from_to_registrar
        ON mdr.registration_transition_approval (administered_item_id, from_status, to_status, registrar_id);
END;
