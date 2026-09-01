-- 阶段 3 补遗:1) 删除阶段 2 已删功能的无实体孤儿表(均核实行数与实体引用);
-- 2) interop_data_set/interop_data_element 补迁入 ext(阶段 3 盘点遗漏,实体在 data-set-registration 模块)。
-- 幂等:逐表守卫。

-- 一、孤儿表清理(17 张功能残留 + 3 张测试垃圾表)
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='approval_step_record') DROP TABLE mdr.approval_step_record;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='approval_record') DROP TABLE mdr.approval_record;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='approval_step') DROP TABLE mdr.approval_step;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='approval_process_instance') DROP TABLE mdr.approval_process_instance;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='approval_template') DROP TABLE mdr.approval_template;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='notification_preference') DROP TABLE mdr.notification_preference;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='notification_record') DROP TABLE mdr.notification_record;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='data_quality_task') DROP TABLE mdr.data_quality_task;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='export_task') DROP TABLE mdr.export_task;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='definition_audit_log') DROP TABLE mdr.definition_audit_log;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='fhir_code_system') DROP TABLE mdr.fhir_code_system;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='fhir_element') DROP TABLE mdr.fhir_element;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='fhir_resource') DROP TABLE mdr.fhir_resource;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='fhir_value_set') DROP TABLE mdr.fhir_value_set;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='designation_context') DROP TABLE mdr.designation_context;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='definition_context') DROP TABLE mdr.definition_context;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='definition_namespace') DROP TABLE mdr.definition_namespace;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='definition_service_test$test_item') DROP TABLE mdr.[definition_service_test$test_item];
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='designation_service_test$test_item') DROP TABLE mdr.[designation_service_test$test_item];
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='item_administration_service_test$test_item') DROP TABLE mdr.[item_administration_service_test$test_item];

-- 二、interop 两表迁入 ext(约束/索引随 TRANSFER 保留)
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='interop_data_set')
    ALTER SCHEMA ext TRANSFER mdr.interop_data_set;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='interop_data_element')
    ALTER SCHEMA ext TRANSFER mdr.interop_data_element;
