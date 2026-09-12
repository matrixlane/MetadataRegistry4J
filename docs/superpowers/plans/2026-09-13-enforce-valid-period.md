# 实施计划：强制值域/概念域有效日期（enforce-valid-period）

- 日期：2026-09-13
- OpenSpec 变更：`openspec/changes/enforce-valid-period/`（strict 已过）
- 设计要点：D1 应用层校验（不动列可空性）、D2 回填（lifecycle 优先/2026-01-01 兜底）、D3 表单必填+预填当日、D4 导入兜底

## 阶段一：后端（agent-1，TDD）
1. 先红：`ValueDomainAssociationValidator` 缺 start 拒/仅 start 过/start+end 过；概念域同等；受影响旧测试补 validPeriod
2. 实现：值域 validateCompleteness 增 start 必填；概念域同等（先定位入口）；导入路径缺省补 start=执行时间
3. 产出 `data/_backfill_valid_period.sql`（不执行）
4. 受影响包回归全绿

## 阶段二：前端（agent-2，TDD）
1. 先红：值域/概念域表单起始日期必填断言 + 新建预填当日断言
2. 实现 + vitest 全量 + build

## 阶段三：数据与部署（主 agent）
1. 执行回填 SQL + SELECT 核查（16+16 行）
2. validate/变更记录/detect_changes
3. 重打包 + 重启 + 冒烟（新建值域缺 start 被拒、预填当日可存）
4. tasks 全勾、汇报
