create index if not exists idx_registration_state_administrative_status
    on registration_state(administrative_status);

create index if not exists idx_administered_item_stewardship_record_id
    on administered_item(stewardship_record_id);
