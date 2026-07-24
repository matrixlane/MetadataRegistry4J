
    create table account (
        enabled boolean not null,
        force_password_reset boolean not null,
        temp_password_used boolean not null,
        created_at timestamp,
        last_login_at timestamp,
        sys_role varchar(32) check (sys_role in ('SUPER_ADMIN')),
        username varchar(64) not null unique,
        password_hash varchar(256) not null,
        id blob not null,
        individual_id blob unique,
        primary key (id)
    );

    create table administered_item (
        creation_date_time timestamp,
        last_change_date_time timestamp,
        change_description varchar(255),
        explanatory_comment varchar(255),
        id blob not null,
        origin varchar(255),
        registration_authority_id blob,
        registration_state_id blob unique,
        stewardship_record_id blob,
        primary key (id)
    );

    create table approval_process_instance (
        current_step integer not null,
        completed_at timestamp,
        created_at timestamp not null,
        version bigint,
        id blob not null,
        template_id blob not null,
        status varchar(20) not null check (status in ('PENDING','APPROVED','REJECTED','TIMEOUT')),
        submitter varchar(100) not null,
        administered_item_id blob not null,
        primary key (id)
    );

    create table approval_record (
        approved_at timestamp,
        submitted_at timestamp not null,
        version bigint,
        id blob not null,
        result varchar(20) not null check (result in ('PENDING','APPROVED','REJECTED')),
        approver varchar(100),
        submitter varchar(100) not null,
        opinion varchar(2000),
        administered_item_id blob not null,
        primary key (id)
    );

    create table approval_step (
        step_number integer not null,
        timeout_hours integer,
        version bigint,
        id blob not null,
        template_id blob not null,
        assignee_rule varchar(20) not null check (assignee_rule in ('BY_USER','BY_ROLE','BY_ORGANIZATION')),
        reject_strategy varchar(20) check (reject_strategy in ('REJECT_ALL','REJECT_TO_PREV')),
        assignee_value varchar(200) not null,
        step_name varchar(200) not null,
        description varchar(1000),
        primary key (id)
    );

    create table approval_step_record (
        step_number integer not null,
        approved_at timestamp,
        assigned_at timestamp not null,
        timeout_at timestamp,
        version bigint,
        id blob not null,
        process_instance_id blob not null,
        assignee_rule varchar(20) not null check (assignee_rule in ('BY_USER','BY_ROLE','BY_ORGANIZATION')),
        status varchar(20) not null check (status in ('PENDING','APPROVED','REJECTED','TIMEOUT','TRANSFERRED','SKIPPED')),
        approver varchar(100),
        assignee_value varchar(200) not null,
        step_name varchar(200) not null,
        opinion varchar(2000),
        primary key (id)
    );

    create table approval_template (
        default_timeout_hours integer,
        enabled boolean not null,
        locked boolean not null,
        step_count integer not null,
        created_at timestamp not null,
        updated_at timestamp,
        id blob not null,
        default_reject_strategy varchar(20) check (default_reject_strategy in ('REJECT_ALL','REJECT_TO_PREV')),
        name varchar(200) not null,
        description varchar(2000),
        primary key (id)
    );

    create table attached_item (
        administered_item_id blob,
        id blob not null,
        primary key (id)
    );

    create table classification_node (
        sort_order integer,
        created_at timestamp not null,
        updated_at timestamp not null,
        id blob not null,
        scheme_id blob not null,
        definition TEXT,
        description TEXT,
        node_code varchar(255) not null,
        node_name varchar(255) not null,
        parent_id blob,
        primary key (id)
    );

    create table classification_scheme (
        created_at timestamp not null,
        updated_at timestamp not null,
        id blob not null,
        status varchar(20),
        version varchar(50),
        description TEXT,
        scheme_name varchar(255) not null,
        primary key (id)
    );

    create table concept (
        id blob not null,
        source_uri varchar(255),
        primary key (id)
    );

    create table conceptual_domain_dec (
        conceptual_domain_id blob not null,
        data_element_concept_id blob not null
    );

    create table conceptual_domain (
        id blob not null,
        primary key (id)
    );

    create table contact (
        account_id blob,
        id blob not null,
        individual_id blob,
        organization_id blob,
        role_id blob,
        primary key (id)
    );

    create table context (
        id blob not null,
        primary key (id)
    );

    create table data_quality_task (
        progress_pct integer not null,
        completed_at timestamp,
        created_at timestamp not null,
        id blob not null,
        status varchar(20) not null check (status in ('PENDING','RUNNING','COMPLETED','FAILED')),
        schema_name varchar(50),
        data_source_key varchar(100) not null,
        step_name varchar(100),
        created_by blob not null,
        error_message clob,
        request_params clob,
        result_json clob,
        table_name varchar(255) not null,
        primary key (id)
    );

    create table data_element (
        data_element_concept_id blob,
        id blob not null,
        value_domain_id blob,
        primary key (id)
    );

    create table data_element_concept (
        id blob not null,
        object_class_id blob,
        property_id blob,
        primary key (id)
    );

    create table data_type (
        data_type_scheme_id blob,
        id blob not null,
        primary key (id)
    );

    create table data_type_scheme (
        id blob not null,
        reference varchar(255),
        primary key (id)
    );

    create table definition (
        source_id blob,
        id blob not null,
        item_id blob,
        language_id blob,
        value varchar(255),
        primary key (id)
    );

    create table definition_audit_log (
        operated_at timestamp not null,
        id blob not null,
        action varchar(20) not null,
        language_identifier varchar(50),
        operated_by varchar(100),
        new_value_summary varchar(500),
        old_value_summary varchar(500),
        definition_id blob,
        item_id blob not null,
        primary key (id)
    );

    create table definition_context (
        context_id blob not null,
        definition_id blob not null
    );

    create table definition_context_relation (
        id blob not null,
        acceptability varchar(255) check (acceptability in ('PREFERRED','ADMITTED','DEPRECATED','SUPERSEDED','OBSOLETE')),
        context_id blob,
        definition_id blob,
        primary key (id)
    );

    create table definition_namespace (
        definition_id blob not null,
        namespace_id blob not null
    );

    create table described_conceptual_domain (
        id blob not null,
        primary key (id)
    );

    create table described_value_domain (
        described_conceptual_domain_id blob,
        id blob not null,
        primary key (id)
    );

    create table designation (
        source_id blob,
        id blob not null,
        item_id blob,
        language_id blob,
        value varchar(255),
        primary key (id)
    );

    create table designation_context (
        context_id blob not null,
        designation_id blob not null
    );

    create table designation_context_relation (
        id blob not null,
        acceptability varchar(255) check (acceptability in ('PREFERRED','ADMITTED','DEPRECATED','SUPERSEDED','OBSOLETE')),
        context_id blob,
        designation_id blob,
        primary key (id)
    );

    create table designation_namespace (
        designation_id blob not null,
        namespace_id blob not null
    );

    create table designation_definition_pairing (
        id blob not null,
        acceptability varchar(255) check (acceptability in ('PREFERRED','ADMITTED','DEPRECATED','SUPERSEDED','OBSOLETE')),
        context_id blob,
        definition_id blob,
        designation_id blob,
        primary key (id)
    );

    create table dimensionality (
        coordinate_indicator boolean not null,
        id blob not null,
        primary key (id)
    );

    create table dimensionality_measure_class (
        dimensionality_id blob not null,
        measure_class_id blob not null
    );

    create table document_type (
        id blob not null,
        description varchar(255),
        identifier varchar(255),
        scheme_reference varchar(255),
        primary key (id)
    );

    create table enumerated_conceptual_domain (
        id blob not null,
        primary key (id)
    );

    create table enumerated_value_domain (
        id blob not null,
        primary key (id)
    );

    create table export_task (
        processed_count integer not null,
        total_count integer not null,
        completed_at timestamp,
        created_at timestamp not null,
        expires_at timestamp,
        format varchar(10) not null,
        id blob not null,
        status varchar(20) not null check (status in ('PENDING','PROCESSING','COMPLETED','FAILED')),
        result_file_path varchar(500),
        created_by blob not null,
        error_message clob,
        request_params clob,
        primary key (id)
    );

    create table fhir_code_system (
        imported_at timestamp not null,
        id blob not null,
        fhir_version varchar(20) not null,
        content varchar(50),
        status varchar(50),
        version varchar(50),
        fhir_id varchar(200) not null,
        name varchar(200),
        publisher varchar(200),
        title varchar(500),
        url varchar(500),
        description clob,
        primary key (id)
    );

    create table fhir_element (
        is_modifier boolean not null,
        is_summary boolean not null,
        min integer,
        imported_at timestamp not null,
        id blob not null,
        parent_id blob,
        resource_id blob not null,
        fhir_version varchar(20) not null,
        max varchar(20),
        binding_strength varchar(50),
        element_type varchar(200),
        binding_value_set varchar(500),
        element_path varchar(500) not null,
        path varchar(500) not null,
        binding_description varchar(1000),
        short_description varchar(1000),
        administered_item_id blob,
        definition clob,
        primary key (id)
    );

    create table fhir_element_mapping (
        created_at timestamp not null,
        updated_at timestamp not null,
        version bigint not null,
        mapping_type varchar(16) not null check (mapping_type in ('ONE_TO_ONE','ONE_TO_MANY')),
        fhir_equivalence varchar(20) not null check (fhir_equivalence in ('EQUIVALENT','EQUAL','WIDER','NARROWER','SUBSUMES','SPECIALIZES','RELATEDTO','INEXACT','UNMATCHED','DISJOINT')),
        fhir_resource_type varchar(50) not null,
        mapping_variable varchar(100),
        fhir_path varchar(500) not null,
        description varchar(2000),
        created_by blob,
        id blob not null,
        local_item_id blob not null,
        mapping_scheme_id blob,
        updated_by blob,
        primary key (id)
    );

    create table fhir_resource (
        is_abstract boolean,
        imported_at timestamp not null,
        id blob not null,
        fhir_version varchar(20) not null,
        date varchar(50),
        kind varchar(50),
        status varchar(50),
        version varchar(50),
        fhir_id varchar(200) not null,
        name varchar(200) not null,
        type varchar(200),
        publisher varchar(500),
        title varchar(500),
        url varchar(500),
        administered_item_id blob,
        description clob,
        primary key (id)
    );

    create table fhir_value_set (
        experimental boolean not null,
        imported_at timestamp not null,
        id blob not null,
        fhir_version varchar(20) not null,
        status varchar(50),
        version varchar(50),
        fhir_id varchar(200) not null,
        name varchar(200),
        publisher varchar(200),
        title varchar(500),
        url varchar(500),
        description clob,
        purpose clob,
        primary key (id)
    );

    create table identified_item (
        id blob not null,
        item_id blob unique,
        primary key (id)
    );

    create table identifier_sequence_registry (
        increment_by integer not null,
        pad_length integer not null,
        created_at timestamp not null,
        current_value bigint not null,
        id integer,
        last_allocated_at timestamp not null,
        modified_at timestamp,
        start_value bigint not null,
        identifier_type varchar(32) not null check (identifier_type in ('INTERNAL','URN','OID','LOINC','SNOMED','CUSTOM')),
        prefix varchar(32),
        created_by varchar(128),
        modified_by varchar(128),
        namespace_id blob not null,
        primary key (id)
    );

    create table identifier_strategy_config (
        is_global_default boolean not null,
        version integer not null,
        created_at timestamp not null,
        effective_from timestamp not null,
        effective_until timestamp,
        id integer,
        modified_at timestamp,
        sequence_scope varchar(16) not null,
        status varchar(16) not null check (status in ('DRAFT','PUBLISHED','DISABLED')),
        identifier_type varchar(32) not null check (identifier_type in ('INTERNAL','URN','OID','LOINC','SNOMED','CUSTOM')),
        strategy_type varchar(32) not null,
        created_by varchar(128),
        modified_by varchar(128),
        config_json TEXT,
        namespace_id blob,
        primary key (id)
    );

    create table identifier_version_history (
        timestamp timestamp,
        version_status varchar(16) check (version_status in ('DRAFT','PUBLISHED','DEPRECATED','OBSOLETE')),
        version varchar(32),
        operator varchar(128),
        change_summary varchar(1024),
        id blob not null,
        scoped_identifier_id blob not null,
        primary key (id)
    );

    create table individual (
        id blob not null,
        mail_address varchar(255),
        name varchar(255),
        title varchar(255),
        primary key (id)
    );

    create table individual_email (
        individual_id blob not null,
        value varchar(255)
    );

    create table individual_phone (
        individual_id blob not null,
        value varchar(255)
    );

    create table interop_data_element (
        required boolean,
        created_at timestamp not null,
        updated_at timestamp not null,
        data_set_id blob not null,
        id blob not null,
        mapping_status varchar(20) check (mapping_status in ('UNMAPPED','MAPPED','CONFLICT')),
        data_type varchar(100),
        de_identifier varchar(100) not null,
        representation_format varchar(100),
        de_name varchar(500),
        de_definition TEXT,
        mapped_item_id blob,
        permissible_values TEXT,
        primary key (id)
    );

    create table interop_data_set (
        element_count integer,
        imported_at timestamp,
        updated_at timestamp,
        id blob not null,
        version varchar(50),
        code varchar(100),
        name varchar(500),
        source_file_name varchar(500),
        primary key (id)
    );

    create table interop_mapping (
        confidence_score numeric(3,2) not null,
        confirmed_at timestamp,
        created_at timestamp not null,
        version bigint not null,
        previous_status varchar(20) check (previous_status in ('AUTO_MATCHED','PENDING','CONFIRMED','REJECTED','UNMATCHED')),
        status varchar(20) not null check (status in ('AUTO_MATCHED','PENDING','CONFIRMED','REJECTED','UNMATCHED')),
        strategy varchar(50) not null,
        reason varchar(1000),
        batch_operation_id blob,
        confirmed_by blob,
        dataset_id blob,
        id blob not null,
        interop_data_element_id blob not null,
        local_item_id blob not null,
        previous_local_item_id blob,
        primary key (id),
        check (confidence_score >= 0.00 AND confidence_score <= 1.00 AND status IN ('AUTO_MATCHED','PENDING','CONFIRMED','REJECTED'))
    );

    create table item (
        id blob not null,
        primary key (id)
    );

    create table item_mapping (
        created_at timestamp not null,
        mapping_type varchar(32) not null check (mapping_type in ('EQUIVALENT','DERIVED','AGGREGATED','SUBDIVIDED')),
        description varchar(2000),
        transformation_rule varchar(4000),
        id blob not null,
        mapping_scheme_id blob,
        object_id blob not null,
        subject_id blob not null,
        primary key (id)
    );

    create table item_mapping_object (
        external_uri varchar(2048),
        id blob not null,
        item_id blob,
        primary key (id)
    );

    create table item_mapping_subject (
        external_uri varchar(2048),
        id blob not null,
        item_id blob,
        primary key (id)
    );

    create table language_extension (
        extension varchar(255),
        language_id blob not null
    );

    create table language_variant (
        language_id blob not null,
        variant varchar(255)
    );

    create table language_identification (
        enabled boolean,
        geopolitical_territory_identifier varchar(255),
        id blob not null,
        language_identifier varchar(255),
        private_use_qualifier varchar(255),
        script_identifier varchar(255),
        primary key (id)
    );

    create table local_enumerated_conceptual_domain (
        id blob not null,
        primary key (id)
    );

    create table local_enumerated_value_domain (
        id blob not null,
        primary key (id)
    );

    create table mapping_scheme (
        created_at timestamp not null,
        updated_at timestamp not null,
        status varchar(16) not null check (status in ('DRAFT','PUBLISHED','ARCHIVED')),
        version varchar(32) not null,
        source_uri varchar(2048) not null,
        target_uri varchar(2048) not null,
        description varchar(4000),
        id blob not null,
        name varchar(255) not null,
        parent_id blob,
        primary key (id)
    );

    create table mapping_subscription (
        created_at timestamp not null,
        subscription_type varchar(16) not null check (subscription_type in ('SCHEME','ITEM')),
        user_id varchar(64) not null,
        id blob not null,
        target_id blob not null,
        primary key (id)
    );

    create table measure_class (
        id blob not null,
        primary key (id)
    );

    create table namespace (
        mandatory_naming_convention_indicator boolean,
        one_item_per_name_indicator boolean,
        one_name_per_item_indicator boolean,
        namespace_status varchar(20) check (namespace_status in ('DRAFT','SUBMITTED','ACTIVE','DEPRECATED','OBSOLETE')),
        shorthand_prefix varchar(32),
        naming_authority varchar(128),
        id blob not null,
        naming_authority_id blob,
        naming_convention_id blob,
        parent_id blob,
        scheme_reference varchar(255),
        primary key (id)
    );

    create table namespace_permission (
        permission_type varchar(20) not null check (permission_type in ('READ_ONLY','READ_WRITE','NONE')),
        account_id blob,
        id blob not null,
        namespace_id blob,
        organization_id blob,
        primary key (id)
    );

    create table notification_preference (
        enabled boolean not null,
        updated_at timestamp,
        channel varchar(16) not null check (channel in ('IN_APP','EMAIL')),
        id blob not null,
        notification_type varchar(32) not null check (notification_type in ('APPROVAL_SUBMITTED','APPROVAL_APPROVED','APPROVAL_REJECTED','APPROVAL_TIMEOUT','MAPPING_CHANGED')),
        user_id varchar(64) not null,
        primary key (id)
    );

    create table notification_record (
        max_retries integer not null,
        retry_count integer not null,
        created_at timestamp not null,
        next_retry_at timestamp,
        sent_at timestamp,
        channel varchar(16) not null check (channel in ('IN_APP','EMAIL')),
        id blob not null,
        status varchar(16) not null check (status in ('PENDING','SENT','FAILED')),
        notification_type varchar(32) not null check (notification_type in ('APPROVAL_SUBMITTED','APPROVAL_APPROVED','APPROVAL_REJECTED','APPROVAL_TIMEOUT','MAPPING_CHANGED')),
        recipient_id varchar(64) not null,
        related_business_id varchar(64),
        title varchar(256) not null,
        error_message varchar(2000),
        content varchar(4000) not null,
        primary key (id)
    );

    create table object_class (
        id blob not null,
        primary key (id)
    );

    create table organization (
        id blob not null,
        mail_address varchar(255),
        web_address varchar(255),
        primary key (id)
    );

    create table organization_email (
        organization_id blob not null,
        value varchar(255)
    );

    create table organization_name (
        organization_id blob not null,
        value varchar(255)
    );

    create table organization_phone (
        organization_id blob not null,
        value varchar(255)
    );

    create table permissible_value_pvm (
        permissible_value_id blob not null,
        permissible_value_meaning_id blob not null
    );

    create table permissible_value (
        id blob not null,
        primary key (id)
    );

    create table permissible_value_meaning (
        end_date_time timestamp,
        start_date_time timestamp,
        id blob not null,
        value_meaning_id blob,
        primary key (id)
    );

    create table permissible_value_set (
        end_date_time timestamp,
        start_date_time timestamp,
        id blob not null,
        local_enumerated_value_domain_id blob,
        permissible_value_id blob,
        primary key (id)
    );

    create table property (
        id blob not null,
        primary key (id)
    );

    create table reference_document_language (
        reference_document_id blob not null,
        language_id blob not null
    );

    create table reference_document_provider (
        reference_document_id blob not null,
        provider_id blob not null
    );

    create table reference_document (
        id blob not null,
        type_description_id blob,
        identifier varchar(255),
        notation_value varchar(255),
        title_value varchar(255),
        uri varchar(255),
        primary key (id)
    );

    create table reference_enumerated_conceptual_domain (
        id blob not null,
        primary key (id)
    );

    create table reference_enumerated_value_domain (
        id blob not null,
        primary key (id)
    );

    create table registered_item (
        id blob not null,
        primary key (id)
    );

    create table registrar (
        id blob not null,
        identifier varchar(255),
        registration_authority_id blob,
        primary key (id)
    );

    create table registration_authority_doc_language (
        language_id blob not null,
        registration_authority_id blob not null
    );

    create table registration_authority (
        id blob not null,
        international_code_designator varchar(255),
        namespace_id blob unique,
        opi_source varchar(255),
        organization_identifier varchar(255),
        organization_part_identifier varchar(255),
        primary key (id)
    );

    create table registration_state (
        effective_date timestamp,
        until_date timestamp,
        administrative_note varchar(255),
        administrative_status varchar(255),
        id blob not null,
        previous_state_id blob unique,
        registration_status varchar(255) check (registration_status in ('INCOMPLETE','SUBMITTED','RECORDED','QUALIFIED','STANDARD','PREFERRED','SUPERSEDED','RETIRED','HISTORICAL')),
        unresolved_issue varchar(255),
        primary key (id)
    );

    create table registry_specification (
        auto_generate_identifier boolean not null,
        mandatory_classification boolean not null,
        default_language varchar(255) not null,
        description varchar(255),
        id blob not null,
        identifier_pattern varchar(255),
        identifier_prefix varchar(255),
        registry_name varchar(255) not null,
        versioning_policy varchar(255),
        primary key (id)
    );

    create table role (
        email_address varchar(255),
        id blob not null,
        mail_address varchar(255),
        phone_number varchar(255),
        role_name varchar(255),
        primary key (id)
    );

    create table scoped_identifier (
        created_at timestamp,
        deprecated_at timestamp,
        effective_strategy_id bigint,
        obsolete_at timestamp,
        published_at timestamp,
        version_status varchar(16) check (version_status in ('DRAFT','PUBLISHED','DEPRECATED','OBSOLETE')),
        identifier_type varchar(32) not null check (identifier_type in ('INTERNAL','URN','OID','LOINC','SNOMED','CUSTOM')),
        version varchar(32),
        id blob not null,
        identified_item_id blob,
        identifier varchar(255) not null,
        namespace_id blob,
        primary key (id)
    );

    create table slot (
        id blob not null,
        item_id blob,
        name varchar(255),
        type varchar(255),
        value varchar(255),
        value_datatype varchar(255),
        primary key (id)
    );

    create table stewardship_record (
        contact_id blob,
        id blob not null,
        organization_id blob,
        primary key (id)
    );

    create table submission_record_registered_item (
        registered_item_id blob not null,
        submission_record_id blob not null
    );

    create table submission_record (
        contact_id blob,
        id blob not null,
        organization_id blob,
        primary key (id)
    );

    create table system_setting (
        setting_key varchar(128) not null,
        setting_value varchar(1024) not null,
        primary key (setting_key)
    );

    create table unit_of_measure_measure_class (
        measure_class_id blob not null,
        unit_of_measure_id blob not null
    );

    create table unit_of_measure (
        id blob not null,
        primary key (id)
    );

    create table value_meaning_permissible_value_meaning (
        permissible_value_meaning_id blob not null,
        value_meaning_id blob not null
    );

    create table value_domain (
        maximum_character_quantity integer,
        end_date_time timestamp,
        start_date_time timestamp,
        data_type_id blob,
        format varchar(255),
        id blob not null,
        unit_of_measure_id blob,
        primary key (id)
    );

    create table value_meaning (
        id blob not null,
        primary key (id)
    );

    create table value_meaning_set (
        end_date_time timestamp,
        start_date_time timestamp,
        id blob not null,
        local_enumerated_conceptual_domain_id blob,
        value_meaning_id blob,
        primary key (id)
    );

    create table version_history (
        version_number integer not null,
        changed_at timestamp not null,
        id integer,
        change_type varchar(16) not null,
        item_type varchar(64) not null,
        changed_by varchar(128) not null,
        change_summary varchar(512),
        item_id blob not null,
        snapshot_json clob not null,
        primary key (id)
    );

    create index idx_definition_item_id 
       on definition (item_id);

    create index idx_definition_language_id 
       on definition (language_id);

    create index idx_definition_context_relation_definition_id 
       on definition_context_relation (definition_id);

    create index idx_definition_context_relation_context_id 
       on definition_context_relation (context_id);

    create index idx_designation_item_id 
       on designation (item_id);

    create index idx_designation_language_id 
       on designation (language_id);

    create index idx_designation_context_relation_designation_id 
       on designation_context_relation (designation_id);

    create index idx_designation_context_relation_context_id 
       on designation_context_relation (context_id);

    create index idx_fhir_cs_name 
       on fhir_code_system (name);

    create index idx_fhir_cs_status 
       on fhir_code_system (status);

    create index idx_fhir_element_resource 
       on fhir_element (resource_id);

    create index idx_fhir_element_parent 
       on fhir_element (parent_id);

    create index idx_fhir_element_path 
       on fhir_element (path);

    create index idx_fhir_element_type 
       on fhir_element (element_type);

    create index idx_fem_local_item 
       on fhir_element_mapping (local_item_id);

    create index idx_fem_fhir_path 
       on fhir_element_mapping (fhir_path);

    create index idx_fem_resource_type 
       on fhir_element_mapping (fhir_resource_type);

    create index idx_fem_scheme 
       on fhir_element_mapping (mapping_scheme_id);

    create index idx_fem_local_fhir 
       on fhir_element_mapping (local_item_id, fhir_path);

    create index idx_fhir_res_name 
       on fhir_resource (name);

    create index idx_fhir_res_url 
       on fhir_resource (url);

    create index idx_fhir_res_kind 
       on fhir_resource (kind);

    create index idx_fhir_res_fhir_version 
       on fhir_resource (fhir_version);

    create index idx_fhir_vs_name 
       on fhir_value_set (name);

    create index idx_fhir_vs_status 
       on fhir_value_set (status);
