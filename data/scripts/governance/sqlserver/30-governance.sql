-- ============================================================================
-- 30-governance.sql
--
-- 用途: 注册治理基准：注册规范（默认注册规范，standard=ISO/IEC 11179-3:2023，context=Registry Context，默认语言 zh-CN）、默认注册机构（mdr+ext、机构行与名称、文档语言 zh-CN）、CTX 命名空间（AUTO_WITH_OVERRIDE/SEMVER 设置、指称“默认命名空间”）、默认注册人员（ext.is_default=1，挂“注册管理员”角色与联系人）。来源：线上部署治理配置（metadata_registry-pre-relink-20261029.bak，2026-10-30 注入金库）。依赖 00 语言与 20 的 Registry Context，必须在 00→10→20 之后执行。
--
-- 数据集: WS/T 363-2023《卫生健康信息数据元目录》/ WS/T 364-2023
--   《卫生健康信息数据元值域代码》（2026-10-12 重印版；2023-10-07 发布、
--   2024-04-01 实施）。行数声明: data_element 2212 / value_domain 2156 / conceptual_domain 2157 / permissible_value 4650 / value_meaning 4650 / data_type 8 / reference_document 34 / registered_item 11185。
--
-- 同码两义处置（2026-10-12 闭环；组2/3 于 2026-10-29 按标准现行文本撤销，
--   scratch/ws-verify/report_duplicate_codes_errata.md）:
--   组1 CV03.00.203 加速器产生的电离辐射=4004（X 射线装置保留 4003，金库双行完整）
--   组2/3 CV04.30.002 按标准现行文本恢复在位（撤销 2026-10-12 整域移除裁决，
--     DE04.30.036.00.value_domain_id 重挂该值域；标准仍未修复 2 组重码
--     BP0KZZZZA/BQ2FZUZZZ 及左腕关节 5 行部位字母，系标准原文缺陷随文收录）
--   组4 CV05.01.016 多趾—右侧=1702（多指—右侧保留 1701）
--   组5 CV09.00.101 纯净水=0205（2023-12-08 标准勘误版已自行修复）
--   空壳处置（2026-10-29 修订，决策 decision-2026-10-29-no-empty-shells）：
--     枚举值域不允许 0 码值空壳——KNOWN_GAP 5 壳（CV04.50.021/CV06.00.239/
--     CV08.10.101/CV08.50.004/CV08.50.102）已整域删除；其引用数据元
--     DE08.50.040.00/DE06.00.339.00/DE08.10.081.00/DE08.50.025.00/
--     DE08.50.003.00 的 value_domain_id 置 NULL（364 无对应代码表或被
--     标准删除，属标准 dangling 引用，不再以空壳占位）
--
-- 生成: 2026-11-05，由 scratch/baseline-rebuild/gen_scripts_sqlserver.py
--   自 scratch/baseline-rebuild/golden-new.db（transform_golden_v3.py 产物，
--   全部不变式通过）导出；行序按主键/业务键固定，同日重跑字节一致。
--
-- 前提: 目标库表结构由应用建立（先启动一次应用：Hibernate + Flyway 自
--   20261009012 基线及以后）。本脚本不含 DDL、不含 SET IDENTITY_INSERT
--   （目标表均 UNIQUEIDENTIFIER 主键，无 IDENTITY 列）。
--
-- 幂等: 每行 INSERT ... SELECT ... WHERE NOT EXISTS（主键 id 或业务键判重），
--   UPDATE 带 IS NULL 守卫；全量重跑零副作用，中途失败重跑自动补齐。
--
-- 执行（Windows 集成认证示例，应用须停止）:
--   sqlcmd -E -S <实例> -d <目标库> -f 65001 -b -I -i 30-governance.sql
--
-- 方言: T-SQL；中文以 N'' 字面量写入。目标库 Chinese_PRC_CI_AS 下 varchar 列
--   按 GBK 存储（标准原文的私用区/波线等个别字符将存为 '?'，nvarchar 列无损失）。
--   不适用于 SQLite / 达梦。
-- 内容分段: 注册规范 + 默认注册机构 + CTX 命名空间 + 注册人员/角色
-- ============================================================================

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
GO

IF SCHEMA_ID('mdr') IS NULL OR SCHEMA_ID('ext') IS NULL
    THROW 50001, N'基准脚本前置失败：mdr/ext schema 不存在——请先启动应用建表', 1;
IF OBJECT_ID('mdr.context','U') IS NULL THROW 50002, N'基准脚本前置失败：表 mdr.context 不存在——请先在目标库启动一次应用（Hibernate+Flyway 建表）再执行本脚本', 1;
IF OBJECT_ID('mdr.language_identification','U') IS NULL THROW 50002, N'基准脚本前置失败：表 mdr.language_identification 不存在——请先在目标库启动一次应用（Hibernate+Flyway 建表）再执行本脚本', 1;
IF OBJECT_ID('mdr.item','U') IS NULL THROW 50002, N'基准脚本前置失败：表 mdr.item 不存在——请先在目标库启动一次应用（Hibernate+Flyway 建表）再执行本脚本', 1;
IF OBJECT_ID('mdr.organization','U') IS NULL THROW 50002, N'基准脚本前置失败：表 mdr.organization 不存在——请先在目标库启动一次应用（Hibernate+Flyway 建表）再执行本脚本', 1;
IF OBJECT_ID('mdr.registration_authority','U') IS NULL THROW 50002, N'基准脚本前置失败：表 mdr.registration_authority 不存在——请先在目标库启动一次应用（Hibernate+Flyway 建表）再执行本脚本', 1;
GO

SET XACT_ABORT ON;
BEGIN TRANSACTION;

-- ---- mdr.item（1 行）----
INSERT INTO mdr.item ([id]) SELECT 'ED6732AA-8CFE-419B-967B-887B850286DD' WHERE NOT EXISTS (SELECT 1 FROM mdr.item WHERE [id] = 'ED6732AA-8CFE-419B-967B-887B850286DD');
-- ---- mdr.organization（1 行）----
INSERT INTO mdr.organization ([id], [mail_address], [web_address]) SELECT '078EC7C8-A15A-4D1E-8D03-0D220F2039D5', NULL, NULL WHERE NOT EXISTS (SELECT 1 FROM mdr.organization WHERE [id] = '078EC7C8-A15A-4D1E-8D03-0D220F2039D5');
-- ---- mdr.organization_name（1 行）----
INSERT INTO mdr.organization_name ([organization_id], [value]) SELECT '078EC7C8-A15A-4D1E-8D03-0D220F2039D5', N'默认注册机构' WHERE NOT EXISTS (SELECT 1 FROM mdr.organization_name WHERE [organization_id] = '078EC7C8-A15A-4D1E-8D03-0D220F2039D5' AND [value] = N'默认注册机构');
-- ---- mdr.registration_authority（1 行）----
INSERT INTO mdr.registration_authority ([international_code_designator], [opi_source], [organization_identifier], [organization_part_identifier], [id]) SELECT N'0000', NULL, N'RA', NULL, '078EC7C8-A15A-4D1E-8D03-0D220F2039D5' WHERE NOT EXISTS (SELECT 1 FROM mdr.registration_authority WHERE [id] = '078EC7C8-A15A-4D1E-8D03-0D220F2039D5');
-- ---- ext.registration_authority（1 行）----
INSERT INTO ext.registration_authority ([id], [is_primary]) SELECT '078EC7C8-A15A-4D1E-8D03-0D220F2039D5', 1 WHERE NOT EXISTS (SELECT 1 FROM ext.registration_authority WHERE [id] = '078EC7C8-A15A-4D1E-8D03-0D220F2039D5');
-- ---- mdr.registration_authority_doc_language（1 行）----
INSERT INTO mdr.registration_authority_doc_language ([registration_authority_id], [language_id]) SELECT '078EC7C8-A15A-4D1E-8D03-0D220F2039D5', '228DF1D4-D1FB-4EC3-A4CC-DD58ACD5F531' WHERE NOT EXISTS (SELECT 1 FROM mdr.registration_authority_doc_language WHERE [registration_authority_id] = '078EC7C8-A15A-4D1E-8D03-0D220F2039D5' AND [language_id] = '228DF1D4-D1FB-4EC3-A4CC-DD58ACD5F531');
-- ---- mdr.role（1 行）----
INSERT INTO mdr.role ([id], [mail_address], [role_name]) SELECT 'B585F1A6-FBBD-419B-88AD-2E7F4FF8B504', NULL, N'注册管理员' WHERE NOT EXISTS (SELECT 1 FROM mdr.role WHERE [id] = 'B585F1A6-FBBD-419B-88AD-2E7F4FF8B504');
-- ---- mdr.contact（1 行）----
INSERT INTO mdr.contact ([id], [individual_id], [organization_id], [role_id]) SELECT '6A78987B-5559-4132-9CBA-0175A82A3655', NULL, '078EC7C8-A15A-4D1E-8D03-0D220F2039D5', 'B585F1A6-FBBD-419B-88AD-2E7F4FF8B504' WHERE NOT EXISTS (SELECT 1 FROM mdr.contact WHERE [id] = '6A78987B-5559-4132-9CBA-0175A82A3655');
-- ---- mdr.registrar（1 行）----
INSERT INTO mdr.registrar ([id], [registration_authority_id]) SELECT '6A78987B-5559-4132-9CBA-0175A82A3655', '078EC7C8-A15A-4D1E-8D03-0D220F2039D5' WHERE NOT EXISTS (SELECT 1 FROM mdr.registrar WHERE [id] = '6A78987B-5559-4132-9CBA-0175A82A3655');
-- ---- ext.registrar（1 行）----
INSERT INTO ext.registrar ([id], [is_default]) SELECT '6A78987B-5559-4132-9CBA-0175A82A3655', 1 WHERE NOT EXISTS (SELECT 1 FROM ext.registrar WHERE [id] = '6A78987B-5559-4132-9CBA-0175A82A3655');
-- ---- mdr.namespace（1 行）----
INSERT INTO mdr.namespace ([mandatory_naming_convention_indicator], [one_item_per_name_indicator], [one_name_per_item_indicator], [scheme_reference], [shorthand_prefix], [id], [naming_authority_id], [registration_authority_id]) SELECT 0, 1, 0, NULL, NULL, 'ED6732AA-8CFE-419B-967B-887B850286DD', '078EC7C8-A15A-4D1E-8D03-0D220F2039D5', '078EC7C8-A15A-4D1E-8D03-0D220F2039D5' WHERE NOT EXISTS (SELECT 1 FROM mdr.namespace WHERE [id] = 'ED6732AA-8CFE-419B-967B-887B850286DD');
-- ---- ext.namespace_identifier_settings（1 行）----
INSERT INTO ext.namespace_identifier_settings ([id], [identifier_input_mode], [manual_identifier_pattern], [namespace_id], [version_input_mode], [versioning_policy]) SELECT '28650837-E0BE-4A9E-BB37-49957953E912', N'AUTO_WITH_OVERRIDE', NULL, 'ED6732AA-8CFE-419B-967B-887B850286DD', N'AUTO_WITH_OVERRIDE', N'SEMVER' WHERE NOT EXISTS (SELECT 1 FROM ext.namespace_identifier_settings WHERE [namespace_id] = 'ED6732AA-8CFE-419B-967B-887B850286DD');
-- ---- mdr.registry_specification（1 行）----
INSERT INTO mdr.registry_specification ([character_repertoire], [comment], [conformance_label], [conformance_level], [default_time_zone], [degree_of_conformance], [excluded_features], [included_features], [name], [representation_class_scheme_id], [standard], [web_address], [id], [context_id], [default_language_id]) SELECT N'UTF-8', NULL, NULL, NULL, N'+0800', NULL, NULL, NULL, N'默认注册规范', NULL, N'ISO/IEC 11179-3:2023', NULL, 'FAC3F66C-A863-45EE-8139-69C0AFEA1489', '00000000-0000-0000-0000-000000000002', '228DF1D4-D1FB-4EC3-A4CC-DD58ACD5F531' WHERE NOT EXISTS (SELECT 1 FROM mdr.registry_specification WHERE [id] = 'FAC3F66C-A863-45EE-8139-69C0AFEA1489');
-- ---- mdr.designation（3 行）----
INSERT INTO mdr.designation ([id], [value], [item_id], [language_id], [source_id]) SELECT '16784DA5-5737-4EAA-AAAF-22F67EB09A15', N'注册表上下文', '00000000-0000-0000-0000-000000000002', '228DF1D4-D1FB-4EC3-A4CC-DD58ACD5F531', NULL WHERE NOT EXISTS (SELECT 1 FROM mdr.designation WHERE [id] = '16784DA5-5737-4EAA-AAAF-22F67EB09A15');
INSERT INTO mdr.designation ([id], [value], [item_id], [language_id], [source_id]) SELECT '32D4EE91-8A5A-4E1B-AA47-4564B854AD71', N'Registry Context', '00000000-0000-0000-0000-000000000002', '98570376-9FC7-4508-952A-DB0106D3EA16', NULL WHERE NOT EXISTS (SELECT 1 FROM mdr.designation WHERE [id] = '32D4EE91-8A5A-4E1B-AA47-4564B854AD71');
INSERT INTO mdr.designation ([id], [value], [item_id], [language_id], [source_id]) SELECT 'D4233AF4-83AF-46F0-A627-62029E9E5C63', N'默认命名空间', 'ED6732AA-8CFE-419B-967B-887B850286DD', NULL, NULL WHERE NOT EXISTS (SELECT 1 FROM mdr.designation WHERE [id] = 'D4233AF4-83AF-46F0-A627-62029E9E5C63');
-- ---- mdr.definition（2 行）----
INSERT INTO mdr.definition ([id], [value], [item_id], [language_id], [source_id]) SELECT '54947A78-D447-4E70-9DC3-DF7BF1C8C6F1', N'The default context for this registry: the setting in which designations are used to designate and definitions are used to define registry items.', '00000000-0000-0000-0000-000000000002', '98570376-9FC7-4508-952A-DB0106D3EA16', NULL WHERE NOT EXISTS (SELECT 1 FROM mdr.definition WHERE [id] = '54947A78-D447-4E70-9DC3-DF7BF1C8C6F1');
INSERT INTO mdr.definition ([id], [value], [item_id], [language_id], [source_id]) SELECT 'C55B69D7-6A6C-45A7-9088-E016EFCAB4CD', N'本注册表的上下文：指称用于指称注册项、定义用于定义注册项的语境。', '00000000-0000-0000-0000-000000000002', '228DF1D4-D1FB-4EC3-A4CC-DD58ACD5F531', NULL WHERE NOT EXISTS (SELECT 1 FROM mdr.definition WHERE [id] = 'C55B69D7-6A6C-45A7-9088-E016EFCAB4CD');
-- ---- mdr.designation_context（2 行）----
INSERT INTO mdr.designation_context ([id], [acceptability], [context_id], [designation_id]) SELECT '181067FF-8DF4-45CD-B869-344E37BBE149', N'preferred', '00000000-0000-0000-0000-000000000002', '32D4EE91-8A5A-4E1B-AA47-4564B854AD71' WHERE NOT EXISTS (SELECT 1 FROM mdr.designation_context WHERE [id] = '181067FF-8DF4-45CD-B869-344E37BBE149');
INSERT INTO mdr.designation_context ([id], [acceptability], [context_id], [designation_id]) SELECT '6797A1AB-2902-4CEF-BFC4-AF256A385017', N'preferred', '00000000-0000-0000-0000-000000000002', '16784DA5-5737-4EAA-AAAF-22F67EB09A15' WHERE NOT EXISTS (SELECT 1 FROM mdr.designation_context WHERE [id] = '6797A1AB-2902-4CEF-BFC4-AF256A385017');
-- ---- mdr.definition_context（2 行）----
INSERT INTO mdr.definition_context ([id], [acceptability], [context_id], [definition_id]) SELECT '07B58B04-E010-4AF6-A833-5026DB7B89BB', N'preferred', '00000000-0000-0000-0000-000000000002', '54947A78-D447-4E70-9DC3-DF7BF1C8C6F1' WHERE NOT EXISTS (SELECT 1 FROM mdr.definition_context WHERE [id] = '07B58B04-E010-4AF6-A833-5026DB7B89BB');
INSERT INTO mdr.definition_context ([id], [acceptability], [context_id], [definition_id]) SELECT '91F55F65-9E61-493E-BD26-B27254565286', N'preferred', '00000000-0000-0000-0000-000000000002', 'C55B69D7-6A6C-45A7-9088-E016EFCAB4CD' WHERE NOT EXISTS (SELECT 1 FROM mdr.definition_context WHERE [id] = '91F55F65-9E61-493E-BD26-B27254565286');

COMMIT;
GO
PRINT N'本脚本执行完成';
GO
