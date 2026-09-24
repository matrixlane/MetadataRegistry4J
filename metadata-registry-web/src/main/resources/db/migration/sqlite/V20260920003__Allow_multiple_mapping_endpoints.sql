-- 忠实标准模型：Item 与 Item_Mapping 为多对多，
-- 一条映射可有 1..* 主体与 1..* 客体（评审裁定 2026-09-20，替代初版 MVP 1:1）。
-- 仅去除 V20260920002 引入的两个唯一约束，关联表形态不变。
drop index if exists uk_ims_mapping;
drop index if exists uk_imo_mapping;
