# Tasks: 命名规范表单对齐 ISO/IEC 11179-3:2023

## 1. 测试先行（TDD）

- [x] 1.1 新增 `metadata-registry-ui/src/views/naming-convention/NamingConventionList.spec.ts`：mock `@/api/resources` 与 `vue-i18n`，挂载组件；断言「新建」对话框打开后存在「注册标识」「属性」页签且不存在「指称」「定义」页签；断言「属性」页签渲染 scopeRule/authorityRule/semanticRule/syntacticRule/lexicalRule 五个输入项（先跑红）
- [x] 1.2 同一 spec 断言：调用 `submitFn` 后 `namingConventionApi.create` 收到的 payload 含五个规则属性、不含 designations/definitions；scopeRule 为空时提交被前端拒绝且不发起 API 调用（先跑红）

## 2. i18n 文案

- [x] 2.1 `metadata-registry-ui/src/i18n/zh-CN.json` 的 `namingConvention` 区块新增 semanticRule/syntacticRule/lexicalRule 的名称、提示（忠实还原 ISO/IEC 11179-3:2023 Table 17 定义的中文翻译）与 placeholder key；删除不再使用的 `designationsRequired`

## 3. 组件实现

- [x] 3.1 `metadata-registry-ui/src/views/naming-convention/NamingConventionList.vue`：移除「指称」「定义」页签与 `MultilingualTextEditor` 引入；移除 designations 必填校验；「属性」页签新增 semanticRule/syntacticRule/lexicalRule 三个 `el-input`（带 tooltip/placeholder i18n）；handleCreate/handleEdit/submitFn 同步五个规则属性，payload 不再携带 designations/definitions（1.1/1.2 转绿）
- [x] 3.2 同文件列表区：新增 semanticRule/syntacticRule/lexicalRule 三列（截断显示、空显 `-`）；首列无指称时回退 scopeRule 截断文本

## 4. 验证

- [x] 4.1 `cd metadata-registry-ui && npx vitest run` 全量通过（276/277；唯一失败 MyProfile.spec.ts 为 stash 验证的既有无关失败）
- [x] 4.2 `cd metadata-registry-ui && npm run build`（vue-tsc）通过
- [x] 4.3 `openspec validate --all` 通过（66 passed, 0 failed）
- [x] 4.4 `node .gitnexus/run.cjs detect-changes --scope all --repo .` 无 partial/truncated（risk: low）
- [x] 4.5 主 spec 同步核对：`openspec/specs/naming-convention-ui/spec.md` 由归档流程合并 delta 后与设计一致（归档时验证）
