## 1. 准备与依赖确认

- [x] 1.1 确认 `metadata-registry-ui/package.json` 中 Element Plus 版本 ≥ 2.6.0（`el-segmented` 起始版本）；若不满足则回退到 `el-radio-button` 内联小尺寸版
- [x] 1.2 复核 `ItemRegistrationSection.vue` 当前模板，定位需移除的两个独立 `el-form-item` 行（标识符生成方式 / 版本生成方式）与对应的 `el-radio-button` 引用
- [x] 1.3 复核 `ItemRegistrationSection.spec.ts` 现有用例（标识符自动/手动切换、版本自动/手动切换、编辑态禁用），标记需要适配的断言

## 2. 前端模板改造：内嵌 inline 切换控件

- [x] 2.1 移除 `ItemRegistrationSection.vue` 中"标识符生成方式" `el-form-item` 整行；保留其下 `identifierChoice` ref 与 `showIdentifierChoice` 计算属性
- [x] 2.2 在"标识符" `el-input` 上新增 `append` 槽位承载 inline 切换控件（首选 `el-segmented`，备选 `el-radio-button` size="small"）；`v-if="showIdentifierChoice"` 包裹；外层加 `role="radiogroup"` 与 `aria-label="标识符生成方式"`
- [x] 2.3 移除"版本生成方式" `el-form-item` 整行；保留 `versionChoice` ref 与 `showVersionChoice` 计算属性
- [x] 2.4 在"版本" `el-input` 上以同样的 inline 控件模式承载版本切换；`v-if="showVersionChoice"` 包裹；外层加 `aria-label="版本生成方式"`
- [x] 2.5 校验 `identifierAutoInEffect` / `versionAutoInEffect` / `identifierChoice` / `versionChoice` / `templateVariables` / `variableValues` 等 script 逻辑零改动；仅模板两个 `el-form-item` 形态变更
- [x] 2.6 在 `<style scoped>` 中微调 `.el-input-group__append` padding（移除原 el-radio-button 整行的垂直 margin），确保 inline 控件与 input 视觉对齐

## 3. 测试补充与回归

- [x] 3.1 在 `ItemRegistrationSection.spec.ts` 中新增 inline 控件场景：AUTO_ONLY 命名空间下 append 槽位 v-if 隐藏
- [x] 3.2 新增 MANUAL_ONLY 命名空间下 append 槽位 v-if 隐藏场景
- [x] 3.3 新增 AUTO_WITH_OVERRIDE 下 append 槽位显示且可切换场景（点击 segmented 切到 manual 后输入框启用）
- [x] 3.4 新增编辑态 append 槽位 v-if 隐藏、标识符输入框禁用回显场景
- [x] 3.5 新增版本字段的等价场景（AUTO_ONLY / MANUAL_ONLY / AUTO_WITH_OVERRIDE / 编辑态）
- [x] 3.6 回归 `ItemFormDialog.spec.ts`：确认数据元 / 值域 / 概念域 / 语境四个既有界面注册区段行为不变
- [x] 3.7 跨 Item 列表页视觉冒烟（手动 spot-check）：DataElement、DataElementConcept、ObjectClass、ValueMeaning、DataType、ConceptualDomain、Context、NamingConvention、RegistrySpecification 等至少 5 个页面，确认生成方式已下放到输入框右侧

## 4. 构建与回归验证

- [x] 4.1 前端：`npx vitest run` 全部通过（包含 `ItemRegistrationSection.spec.ts` 与 `ItemFormDialog.spec.ts`）
- [x] 4.2 前端：`npx vue-tsc --noEmit` 通过（类型校验）
- [x] 4.3 前端：`npx vite build` 通过（产物可正常打包）
- [x] 4.4 确认后端无需变更；`metadata-registry-web` 不在本次 diff 内
- [x] 4.5 用 `git diff --stat` 与 `git status` 核对变更范围仅限 `ItemRegistrationSection.vue` / `ItemRegistrationSection.spec.ts`（必要时含测试夹具），无意外扩散

## 5. 文档与归档

- [x] 5.1 在 commit message 中引用 OpenSpec 变更名 `inline-identifier-version-generation-mode` 与本 tasks 列表
- [x] 5.2 在合并前以 `openspec validate inline-identifier-version-generation-mode --strict` 复核产出物一致性
- [x] 5.3 用户确认实现完成后，按 OpenSpec 流程归档该 change 到 `openspec/changes/archive/`（不晚于本会话）
