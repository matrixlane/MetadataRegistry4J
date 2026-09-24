-- 注册流程（registration-process-designer）：迁移规则 + 审批记录。
-- 规则按注册机构作用域，标准默认值由应用层 RegistrationProcessService.ensureSeed 幂等播种；
-- 本脚本只建表与索引（personal profile ddl-auto:none，此脚本为唯一建表来源，须与 JPA 实体映射一致）。
CREATE TABLE registration_transition_rule (
    id blob not null,
    registration_authority_id blob not null,
    from_status varchar(32) not null check (from_status in ('INCOMPLETE','CANDIDATE','RECORDED','QUALIFIED','STANDARD','PREFERRED_STANDARD','SUPERSEDED','RETIRED','HISTORICAL','APPLICATION')),
    to_status varchar(32) not null check (to_status in ('INCOMPLETE','CANDIDATE','RECORDED','QUALIFIED','STANDARD','PREFERRED_STANDARD','SUPERSEDED','RETIRED','HISTORICAL','APPLICATION')),
    display_name varchar(255),
    requires_approval boolean not null,
    approver_mode varchar(32) not null check (approver_mode in ('ALL_REGISTRARS','SELECTED_REGISTRARS')),
    quorum_type varchar(16) not null check (quorum_type in ('ANY_ONE','AT_LEAST')),
    quorum_count integer,
    enabled boolean not null,
    primary key (id)
);
CREATE UNIQUE INDEX uk_rtr_authority_from_to ON registration_transition_rule (registration_authority_id, from_status, to_status);

-- SELECTED_REGISTRARS 时的指定审核人集合（JPA ElementCollection 子表）。
CREATE TABLE registration_transition_rule_approver (
    rule_id blob not null,
    registrar_id blob not null
);
CREATE UNIQUE INDEX uk_rtra_rule_registrar ON registration_transition_rule_approver (rule_id, registrar_id);

-- 审批记录：同一 Registrar 对同一 (item, from, to) 只计一次批准。
CREATE TABLE registration_transition_approval (
    id blob not null,
    administered_item_id blob not null,
    from_status varchar(32) not null check (from_status in ('INCOMPLETE','CANDIDATE','RECORDED','QUALIFIED','STANDARD','PREFERRED_STANDARD','SUPERSEDED','RETIRED','HISTORICAL','APPLICATION')),
    to_status varchar(32) not null check (to_status in ('INCOMPLETE','CANDIDATE','RECORDED','QUALIFIED','STANDARD','PREFERRED_STANDARD','SUPERSEDED','RETIRED','HISTORICAL','APPLICATION')),
    registrar_id blob not null,
    account_id blob,
    approved_at datetime not null,
    primary key (id)
);
CREATE UNIQUE INDEX uk_rta_item_from_to_registrar ON registration_transition_approval (administered_item_id, from_status, to_status, registrar_id);
