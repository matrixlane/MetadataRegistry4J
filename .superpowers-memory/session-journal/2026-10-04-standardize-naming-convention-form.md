# Session Journal — 2026-10-04 standardize-naming-convention-form

## 任务

命名规范（Naming_Convention）按 ISO/IEC 11179-3:2023 完善（用户指令：默认方案、免询问免确认、直接 OpenSpec 流程进实现）：
1. 命名规范创建按标准（此前强制 designations ≥1）；
2. 命名规范编辑界面移除「指称」「定义」设置；
3. 指称可选择命名规范（既有 conformance 链路保持），反向不需要；
4. 五个规则属性（Table 17：scope/authority/semantic/syntactic/lexical）此前 UI 仅两个。

## 标准依据（Milvus anythingllm_metadata_registry 检索 ISO_IEC 11179-3-2023.pdf 原文核实）

- §8.4.4 Naming_Convention：为构造注册项指称符号的规则集；Item 子类，经注册标识识别。
- Table 17：scope_rule 基数 1（必填，应说明描述性/规定性）；authority/semantic/syntactic/lexical_rule 各 0..1。
- §8.6.5 naming_convention_conformance：Designation → Naming_Convention（指称选命名规范的方向性依据）。

## 关键发现

- 后端零缺口：实体五属性 + DTO + Service + 三方言列（V20260731001）早已齐备；`NamingConventionRequest.designations` 无 @NotEmpty，API 本允许无指称创建。
- 缺口纯前端：`NamingConventionList.vue` 表单只有 scope/authority 两字段、含指称/定义页签并前端强制 designations ≥1；`types/common.ts` 的 NamingConvention DTO 缺三字段（vue-tsc 抓出）。
- 指称→命名规范全链路既有：Designation.namingConventions（designation_naming_convention 表）、ItemAdministrationService.applyDesignations、MultilingualTextEditor/DesignationEditor 行内多选、ItemFormDialog 按命名空间装载候选。

## 实施

- TDD：新增 NamingConventionList.spec.ts（4 用例，2 红→绿）：页签集合断言、五输入渲染、payload 无 designations/definitions、scopeRule 空拒提交。
- i18n zh-CN.json：新增 semantic/syntactic/lexicalRule 名称/提示/placeholder（tooltip 忠实还原 Table 17 定义）；scope/authorityRule 旧提示替换为标准译文；删 designationsRequired。en-US.json 走 fallbackLocale（既有模式，该区块本就只有 zh-CN）。
- 组件：移除指称/定义页签与校验；属性页签五输入；列表 +3 列（截断 50、空显 -）；首列无指称回退 scopeRule 截断；删除确认同款回退；types/common.ts DTO 补三字段 + registration.namespaceId/version + designations/definitions 改可选。

## 验证

- vitest 31 文件 277 用例：276 过；唯一失败 MyProfile.spec.ts 经 git stash 验证为既有无关失败。
- vue-tsc + vite build 通过。
- openspec validate --all 65/65（归档后顺手把主 spec 占位 Purpose 补为正式描述）。
- detect-changes：risk low、无 partial/truncated（注意：工作树混有并行会话的 init-wizard/datasource 等未提交改动，均非本次触碰）。

## 归档

- `standardize-naming-convention-form` → `2026-10-04-standardize-naming-convention-form`，主 spec `naming-convention-ui` 合并（+1 ~2 -1）。
- 活动变更仅剩并行会话的 remove-system-default-namespace（未触碰）。

## 遗留 / 备注

- 后端 API 保留 designations/definitions 字段兼容存量（设计决策，不改动）。
- 命名规范机器可执行规则（rulesJson 卫星表）的表单编辑仍为既有范围之外。
- 工作树存在大量并行会话未提交变更，提交时需按变更分组（本次组：NamingConventionList.vue/.spec.ts、types/common.ts、zh-CN.json、openspec 产物、docs/superpowers 两份）。
