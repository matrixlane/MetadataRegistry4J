-- 清除 mdr.registrar 的非标准列 identifier，回归 ISO/IEC 11179-3:2023 §6.3.5：
-- Contact 的标准属性仅有 organization / individual / role 三个关联（+ id），
-- Registrar 子类只额外挂 registration_authority_id；identifier 是应用曾自行添加的属性，
-- 标准无此语义，本次删除。
--
-- 存量数据（形如 {username}-registrar 的自动生成值）随列删除一并丢失：
-- 该列无标准语义、无检索/展示依赖，可接受。
-- SQLite 无 schema 概念，account/contact 同库同名，语句不加限定；
-- DROP COLUMN 不支持 IF EXISTS（Flyway 一次语义，无需幂等守卫）。
alter table registrar drop column identifier;
