-- 清除 mdr.individual 的两个非标准列，回归 ISO/IEC 11179-3:2023 §6.3.2：
-- Individual 的标准属性仅有 name / title / mail_address / email_address / phone_number（+ id）。
--   1. identifier —— 应用曾自行添加（V20260917001），标准无此属性，本次删除。
--   2. avatar     —— 头像语义归位为纯账号级，迁到 ext.account.avatar（account 本体在 ext schema）。
--
-- 搬迁语义：把已绑定到账号的 Individual 的 avatar 复制到对应 ext.account.avatar；
-- 未被任何账号引用的孤儿 Individual 其 avatar 随列删除一并丢失，属已知可接受。
-- ext 表按既有惯例全限定 + COL_LENGTH 守卫；mdr 表同样全限定以避免受 default_schema 影响。

-- 0. ext schema 兜底（V20260901014 已建，此处仅作幂等守卫）
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'ext')
    EXEC('CREATE SCHEMA ext');

-- 1. ext.account.avatar
IF COL_LENGTH('ext.account', 'avatar') IS NULL
    EXEC('ALTER TABLE ext.account ADD avatar NVARCHAR(MAX) NULL');

-- 2. 数据搬迁：Individual.avatar -> Account.avatar（放 EXEC 内，列不存在时整批不编译）
IF COL_LENGTH('mdr.individual', 'avatar') IS NOT NULL
    EXEC('UPDATE ext.account
             SET avatar = (SELECT i.avatar FROM mdr.individual i WHERE i.id = ext.account.individual_id)
           WHERE individual_id IS NOT NULL');

-- 3. 删除 mdr.individual 的两个非标准列
IF COL_LENGTH('mdr.individual', 'avatar') IS NOT NULL
    EXEC('ALTER TABLE mdr.individual DROP COLUMN avatar');

IF COL_LENGTH('mdr.individual', 'identifier') IS NOT NULL
    EXEC('ALTER TABLE mdr.individual DROP COLUMN identifier');
