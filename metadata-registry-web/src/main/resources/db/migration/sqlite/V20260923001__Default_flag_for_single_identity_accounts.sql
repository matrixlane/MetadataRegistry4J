-- 单身份账号的存量绑定补标默认（add-submitter-contact-identity）：
-- 账号仅有唯一联系人/Registrar 身份时，该身份即默认。纯 UPDATE，天然幂等。
UPDATE contact_account_binding
   SET is_default = 1
 WHERE is_default = 0
   AND account_id IN (
       SELECT account_id FROM contact_account_binding
       GROUP BY account_id
       HAVING COUNT(*) = 1
   );
