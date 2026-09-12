# 任务：强制值域/概念域有效日期（valid_period）

## 1. 后端

- [x] 1.1 值域校验：`ValueDomainAssociationValidator.validateCompleteness` 增加 validPeriod.start 必填（dataType 校验在先）；单测（缺 start 拒、仅 start 过、start+end 过）+ 既有受影响测试补 validPeriod
- [x] 1.2 概念域校验：定位概念域保存/校验入口，施加同等校验与单测
- [x] 1.3 导入兜底：数据迁移导入路径的值域/概念域创建点，缺有效日期时以执行时间补 start
- [x] 1.4 数据回填：`data/_backfill_valid_period.sql`（16 值域 + 16 概念域，lifecycle 创建时间优先、2026-01-01 兜底，头注占位说明）执行并 SELECT 核查
- [x] 1.5 全量回归：受影响包全绿（关联校验、值域/概念域服务、迁移导入）

## 2. 前端

- [x] 2.1 值域/概念域表单：有效期起始日期必填标记 + 新建预填当日；vitest 用例与全量绿 + build

## 3. 收尾

- [x] 3.1 变更记录：未归档相关变更与 system.md 登记约束已强制；本变更 validate --strict 通过
- [x] 3.2 GitNexus：编辑前 impact、收尾 detect_changes（无 partial/truncated）
- [x] 3.3 部署：重打包 jar + dist 同步 static + 重启后端验证
