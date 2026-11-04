-- ============================================================================
-- 30-governance.sql（SQLite）
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
--   20261009012 基线及以后）。本脚本不含 DDL 与事务/过程指令，纯 DML
--   （注释 + INSERT/UPDATE），sqlite3 executescript 与应用启动链路
--   （ScriptUtils 按分号拆分）均可执行。前置表（须已存在）：
--   - context
--   - language_identification
--   - item
--   - organization
--   - registration_authority
--
-- 幂等: 与 sqlserver 版同一守卫——每行 INSERT ... SELECT ... WHERE NOT EXISTS
--   （主键 id 或业务键判重），UPDATE 带 IS NULL 守卫；全量重跑零副作用，
--   中途失败重跑自动补齐。
--
-- 执行（应用须停止）:
--   sqlite3 "<目标库文件>" ".read data/scripts/data-standards/ws/sqlite/30-governance.sql"
--   或运行 data/init-baseline.bat（按 application-local.yml 数据源配置自动
--   收集对应方言脚本执行）。
--
-- 方言: SQLite——裸表名（ext 卫星物理名带 ext_ 前缀，如 ext_registration_authority、
--   scoped_identifier_lifecycle）；UUID 为 x'..' 16 字节 BLOB 字面量（与金库存储
--   形态一致，生成期 typeof 嗅探断言）；时间戳为 epoch 毫秒 INTEGER；文本为
--   UTF-8 普通字符串（无 GBK 码页损失）。不适用于 SQL Server / 达梦。
-- 内容分段: 注册规范 + 默认注册机构 + CTX 命名空间 + 注册人员/角色
-- ============================================================================

-- ---- item（1 行）----
INSERT INTO item (id) SELECT x'ed6732aa8cfe419b967b887b850286dd' WHERE NOT EXISTS (SELECT 1 FROM item WHERE id = x'ed6732aa8cfe419b967b887b850286dd');
-- ---- organization（1 行）----
INSERT INTO organization (id, mail_address, web_address) SELECT x'078ec7c8a15a4d1e8d030d220f2039d5', NULL, NULL WHERE NOT EXISTS (SELECT 1 FROM organization WHERE id = x'078ec7c8a15a4d1e8d030d220f2039d5');
-- ---- organization_name（1 行）----
INSERT INTO organization_name (organization_id, value) SELECT x'078ec7c8a15a4d1e8d030d220f2039d5', '默认注册机构' WHERE NOT EXISTS (SELECT 1 FROM organization_name WHERE organization_id = x'078ec7c8a15a4d1e8d030d220f2039d5' AND value = '默认注册机构');
-- ---- registration_authority（1 行）----
INSERT INTO registration_authority (international_code_designator, opi_source, organization_identifier, organization_part_identifier, id) SELECT '0000', NULL, 'RA', NULL, x'078ec7c8a15a4d1e8d030d220f2039d5' WHERE NOT EXISTS (SELECT 1 FROM registration_authority WHERE id = x'078ec7c8a15a4d1e8d030d220f2039d5');
-- ---- ext_registration_authority（1 行）----
INSERT INTO ext_registration_authority (id, is_primary) SELECT x'078ec7c8a15a4d1e8d030d220f2039d5', 1 WHERE NOT EXISTS (SELECT 1 FROM ext_registration_authority WHERE id = x'078ec7c8a15a4d1e8d030d220f2039d5');
-- ---- registration_authority_doc_language（1 行）----
INSERT INTO registration_authority_doc_language (registration_authority_id, language_id) SELECT x'078ec7c8a15a4d1e8d030d220f2039d5', x'228df1d4d1fb4ec3a4ccdd58acd5f531' WHERE NOT EXISTS (SELECT 1 FROM registration_authority_doc_language WHERE registration_authority_id = x'078ec7c8a15a4d1e8d030d220f2039d5' AND language_id = x'228df1d4d1fb4ec3a4ccdd58acd5f531');
-- ---- role（1 行）----
INSERT INTO role (id, mail_address, role_name) SELECT x'b585f1a6fbbd419b88ad2e7f4ff8b504', NULL, '注册管理员' WHERE NOT EXISTS (SELECT 1 FROM role WHERE id = x'b585f1a6fbbd419b88ad2e7f4ff8b504');
-- ---- contact（1 行）----
INSERT INTO contact (id, individual_id, organization_id, role_id) SELECT x'6a78987b555941329cba0175a82a3655', NULL, x'078ec7c8a15a4d1e8d030d220f2039d5', x'b585f1a6fbbd419b88ad2e7f4ff8b504' WHERE NOT EXISTS (SELECT 1 FROM contact WHERE id = x'6a78987b555941329cba0175a82a3655');
-- ---- registrar（1 行）----
INSERT INTO registrar (id, registration_authority_id) SELECT x'6a78987b555941329cba0175a82a3655', x'078ec7c8a15a4d1e8d030d220f2039d5' WHERE NOT EXISTS (SELECT 1 FROM registrar WHERE id = x'6a78987b555941329cba0175a82a3655');
-- ---- ext_registrar（1 行）----
INSERT INTO ext_registrar (id, is_default) SELECT x'6a78987b555941329cba0175a82a3655', 1 WHERE NOT EXISTS (SELECT 1 FROM ext_registrar WHERE id = x'6a78987b555941329cba0175a82a3655');
-- ---- namespace（1 行）----
INSERT INTO namespace (mandatory_naming_convention_indicator, one_item_per_name_indicator, one_name_per_item_indicator, scheme_reference, shorthand_prefix, id, naming_authority_id, registration_authority_id) SELECT 0, 1, 0, NULL, NULL, x'ed6732aa8cfe419b967b887b850286dd', x'078ec7c8a15a4d1e8d030d220f2039d5', x'078ec7c8a15a4d1e8d030d220f2039d5' WHERE NOT EXISTS (SELECT 1 FROM namespace WHERE id = x'ed6732aa8cfe419b967b887b850286dd');
-- ---- namespace_identifier_settings（1 行）----
INSERT INTO namespace_identifier_settings (id, identifier_input_mode, manual_identifier_pattern, namespace_id, version_input_mode, versioning_policy) SELECT x'28650837e0be4a9ebb3749957953e912', 'AUTO_WITH_OVERRIDE', NULL, x'ed6732aa8cfe419b967b887b850286dd', 'AUTO_WITH_OVERRIDE', 'SEMVER' WHERE NOT EXISTS (SELECT 1 FROM namespace_identifier_settings WHERE namespace_id = x'ed6732aa8cfe419b967b887b850286dd');
-- ---- registry_specification（1 行）----
INSERT INTO registry_specification (character_repertoire, comment, conformance_label, conformance_level, default_time_zone, degree_of_conformance, excluded_features, included_features, name, representation_class_scheme_id, standard, web_address, id, context_id, default_language_id) SELECT 'UTF-8', NULL, NULL, NULL, '+0800', NULL, NULL, NULL, '默认注册规范', NULL, 'ISO/IEC 11179-3:2023', NULL, x'fac3f66ca86345ee813969c0afea1489', x'00000000000000000000000000000002', x'228df1d4d1fb4ec3a4ccdd58acd5f531' WHERE NOT EXISTS (SELECT 1 FROM registry_specification WHERE id = x'fac3f66ca86345ee813969c0afea1489');
-- ---- designation（3 行）----
INSERT INTO designation (id, value, item_id, language_id, source_id) SELECT x'16784da557374eaaaaaf22f67eb09a15', '注册表上下文', x'00000000000000000000000000000002', x'228df1d4d1fb4ec3a4ccdd58acd5f531', NULL WHERE NOT EXISTS (SELECT 1 FROM designation WHERE id = x'16784da557374eaaaaaf22f67eb09a15');
INSERT INTO designation (id, value, item_id, language_id, source_id) SELECT x'32d4ee918a5a4e1baa474564b854ad71', 'Registry Context', x'00000000000000000000000000000002', x'985703769fc74508952adb0106d3ea16', NULL WHERE NOT EXISTS (SELECT 1 FROM designation WHERE id = x'32d4ee918a5a4e1baa474564b854ad71');
INSERT INTO designation (id, value, item_id, language_id, source_id) SELECT x'd4233af483af46f0a62762029e9e5c63', '默认命名空间', x'ed6732aa8cfe419b967b887b850286dd', NULL, NULL WHERE NOT EXISTS (SELECT 1 FROM designation WHERE id = x'd4233af483af46f0a62762029e9e5c63');
-- ---- definition（2 行）----
INSERT INTO definition (id, value, item_id, language_id, source_id) SELECT x'54947a78d4474e709dc3df7bf1c8c6f1', 'The default context for this registry: the setting in which designations are used to designate and definitions are used to define registry items.', x'00000000000000000000000000000002', x'985703769fc74508952adb0106d3ea16', NULL WHERE NOT EXISTS (SELECT 1 FROM definition WHERE id = x'54947a78d4474e709dc3df7bf1c8c6f1');
INSERT INTO definition (id, value, item_id, language_id, source_id) SELECT x'c55b69d76a6c45a79088e016efcab4cd', '本注册表的上下文：指称用于指称注册项、定义用于定义注册项的语境。', x'00000000000000000000000000000002', x'228df1d4d1fb4ec3a4ccdd58acd5f531', NULL WHERE NOT EXISTS (SELECT 1 FROM definition WHERE id = x'c55b69d76a6c45a79088e016efcab4cd');
-- ---- designation_context（2 行）----
INSERT INTO designation_context (id, acceptability, context_id, designation_id) SELECT x'181067ff8df445cdb869344e37bbe149', 'preferred', x'00000000000000000000000000000002', x'32d4ee918a5a4e1baa474564b854ad71' WHERE NOT EXISTS (SELECT 1 FROM designation_context WHERE id = x'181067ff8df445cdb869344e37bbe149');
INSERT INTO designation_context (id, acceptability, context_id, designation_id) SELECT x'6797a1ab29024cefbfc4af256a385017', 'preferred', x'00000000000000000000000000000002', x'16784da557374eaaaaaf22f67eb09a15' WHERE NOT EXISTS (SELECT 1 FROM designation_context WHERE id = x'6797a1ab29024cefbfc4af256a385017');
-- ---- definition_context（2 行）----
INSERT INTO definition_context (id, acceptability, context_id, definition_id) SELECT x'07b58b04e0104af6a8335026db7b89bb', 'preferred', x'00000000000000000000000000000002', x'54947a78d4474e709dc3df7bf1c8c6f1' WHERE NOT EXISTS (SELECT 1 FROM definition_context WHERE id = x'07b58b04e0104af6a8335026db7b89bb');
INSERT INTO definition_context (id, acceptability, context_id, definition_id) SELECT x'91f55f659e61493ebd26b27254565286', 'preferred', x'00000000000000000000000000000002', x'c55b69d76a6c45a79088e016efcab4cd' WHERE NOT EXISTS (SELECT 1 FROM definition_context WHERE id = x'91f55f659e61493ebd26b27254565286');
