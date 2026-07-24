-- 联邦协作表（registry_node / item_link / sync_watermark / merge_request / merge_request_item）
-- 与 Hibernate 实体定义一致；时间列用 timestamp，UUID 用 blob。

CREATE TABLE registry_node (
    id blob NOT NULL PRIMARY KEY,
    name varchar(128) NOT NULL,
    base_url varchar(512),
    rai varchar(64) NOT NULL,
    auth_token_encrypted varchar(1024),
    enabled boolean NOT NULL,
    created_at timestamp NOT NULL,
    updated_at timestamp
);

CREATE TABLE item_link (
    id blob NOT NULL PRIMARY KEY,
    local_item_id blob NOT NULL,
    remote_node_id blob NOT NULL,
    remote_item_key varchar(256) NOT NULL,
    remote_item_uuid blob,
    established_by_mr_id blob,
    established_at timestamp NOT NULL,
    CONSTRAINT fk_item_link_node FOREIGN KEY (remote_node_id) REFERENCES registry_node (id)
);

CREATE TABLE sync_watermark (
    id blob NOT NULL PRIMARY KEY,
    node_id blob NOT NULL UNIQUE,
    last_merged_mr_id blob,
    last_merged_version integer,
    last_merged_at timestamp,
    created_at timestamp NOT NULL,
    updated_at timestamp,
    CONSTRAINT fk_sync_watermark_node FOREIGN KEY (node_id) REFERENCES registry_node (id)
);

CREATE TABLE merge_request (
    id blob NOT NULL PRIMARY KEY,
    source_node_id blob NOT NULL,
    target_namespace_id blob NOT NULL,
    status varchar(20) NOT NULL,
    bundle_ref varchar(512),
    submitted_by varchar(128),
    reviewed_by varchar(128),
    submitted_at timestamp,
    reviewed_at timestamp,
    merged_at timestamp,
    error_message varchar(2000),
    created_at timestamp NOT NULL,
    CONSTRAINT fk_merge_request_node FOREIGN KEY (source_node_id) REFERENCES registry_node (id)
);

CREATE TABLE merge_request_item (
    id blob NOT NULL PRIMARY KEY,
    merge_request_id blob NOT NULL,
    remote_item_key varchar(256) NOT NULL,
    remote_item_uuid blob,
    change_type varchar(20) NOT NULL,
    resolution varchar(20) NOT NULL,
    linked_local_item_id blob,
    payload_json text,
    base_snapshot_json text,
    CONSTRAINT fk_mr_item_mr FOREIGN KEY (merge_request_id) REFERENCES merge_request (id)
);
