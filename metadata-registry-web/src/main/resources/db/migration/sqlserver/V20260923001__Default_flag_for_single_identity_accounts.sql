-- 单身份账号的存量绑定补标默认（详见 sqlite 同名脚本头注释）。纯 UPDATE，天然幂等。
UPDATE ext.contact_account_binding
   SET is_default = 1
 WHERE is_default = 0
   AND account_id IN (
       SELECT account_id FROM ext.contact_account_binding
       GROUP BY account_id
       HAVING COUNT(*) = 1
   );
