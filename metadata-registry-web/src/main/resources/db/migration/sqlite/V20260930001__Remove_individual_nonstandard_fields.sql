-- 清除 mdr.individual 的两个非标准列，回归 ISO/IEC 11179-3:2023 §6.3.2：
-- Individual 的标准属性仅有 name / title / mail_address / email_address / phone_number（+ id）。
--   1. identifier —— 应用曾自行添加（V20260917001），标准无此属性，本次删除。
--   2. avatar     —— 头像语义归位为纯账号级，迁到 account.avatar（account 本体就在 ext schema）。
--
-- 搬迁语义：把已绑定到账号的 Individual 的 avatar 复制到对应 account.avatar；
-- 未被任何账号引用的孤儿 Individual 其 avatar 随列删除一并丢失，属已知可接受。
-- SQLite 无 schema 概念，account/individual 同库同名，语句不加限定；
-- DROP COLUMN 不支持 IF EXISTS（Flyway 一次语义，无需幂等守卫）。
alter table account add column avatar text;

update account
   set avatar = (select i.avatar from individual i where i.id = account.individual_id)
 where individual_id is not null;

alter table individual drop column avatar;
alter table individual drop column identifier;
