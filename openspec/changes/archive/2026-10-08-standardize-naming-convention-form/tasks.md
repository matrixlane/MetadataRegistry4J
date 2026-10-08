# Tasks: 命名规范表单对齐 ISO/IEC 11179-3:2023

## 1. 实现（工作树清理后恢复终态，测试先行已在恢复过程中验证）

- [x] 1.1 `NamingConventionList.vue` 终态：五规则属性表单+列表、指称/定义页签保留、designations 非必填、首列回退
- [x] 1.2 新增 `NamingConventionList.spec.ts`（5 用例全绿）
- [x] 1.3 `types/common.ts` NamingConvention DTO 补 semanticRule/syntacticRule/lexicalRule（与并行暂存改动共存）

## 2. 验证

- [x] 2.1 `npx vitest run` 全量通过
- [x] 2.2 `npm run build` 通过
- [x] 2.3 `openspec validate --all` 通过
- [x] 2.4 `node .gitnexus/run.cjs detect-changes --scope all --repo .` 无 partial/truncated
- [x] 2.5 归档并核对主 spec 合并结果
