-- 3.1 Contact.account 扩展关联剥为卫星实体(ISO/IEC 11179 标准实体只留标准属性)
-- 建表 + 从 contact 回填 + 删除旧列。
CREATE TABLE contact_account_binding (
    id blob not null,
    contact_id blob not null,
    account_id blob not null,
    primary key (id)
);
CREATE UNIQUE INDEX uk_cab_contact ON contact_account_binding (contact_id);
CREATE INDEX idx_cab_account ON contact_account_binding (account_id);

INSERT INTO contact_account_binding (id, contact_id, account_id)
SELECT randomblob(16), id, account_id
FROM contact
WHERE account_id IS NOT NULL;

ALTER TABLE contact DROP COLUMN account_id;
