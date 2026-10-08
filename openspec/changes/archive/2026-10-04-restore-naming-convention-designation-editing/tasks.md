# Tasks: 恢复命名规范编辑界面的指称与定义

## 1. 测试先行（TDD）

- [x] 1.1 更新 `NamingConventionList.spec.ts`：断言「新建」对话框页签含「注册标识」「指称」「定义」「属性」；提交 payload 携带 designations/definitions（可为空数组）；指称数据原样透传（先跑红）

## 2. 组件实现

- [x] 2.1 `NamingConventionList.vue`：恢复「指称」「定义」页签（MultilingualTextEditor + 默认语言预选）、handleCreate/handleEdit 回填、submitFn payload 携带 designations/definitions；不恢复 designations 必填校验（1.1 转绿）

## 3. 验证

- [x] 3.1 `npx vitest run` 全量通过
- [x] 3.2 `npm run build` 通过
- [x] 3.3 `openspec validate --all` 通过
- [x] 3.4 `node .gitnexus/run.cjs detect-changes --scope all --repo .` 无 partial/truncated
- [x] 3.5 归档并核对主 spec 合并结果
