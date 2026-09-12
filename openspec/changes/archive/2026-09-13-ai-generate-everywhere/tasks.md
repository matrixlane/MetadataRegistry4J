# 任务：字段级 AI 生成普及（含新建模式）

## 1. 后端

- [x] 1.1 `CompleteMetadataTask`：inputSpec itemIdRequired=false、params 增加 draft；校验（itemId/draft 至少其一、itemId 优先、draft 至少一行非空文本、单值截断）
- [x] 1.2 `InferencePromptBuilder.buildDraft(...)`：草稿「已知元数据」段组装（条目类型/指称/定义，空段省略），复用既有写作规则/任务段/输出契约
- [x] 1.3 后端单测：draft 校验矩阵、buildDraft 段落内容、itemId 优先、空草稿拒绝；inference/assistant 全包回归全绿

## 2. 前端

- [x] 2.1 `ItemFormDialog.vue`：canAiGenerate 放开为 isRegistrarUser；新建模式点击时组装 params.draft（条目类型 + 全部已填指称/定义行）；编辑模式维持 itemId；缓存 key 新建模式用 clientKey
- [x] 2.2 前端 vitest：新建模式按钮可见且 draft 载荷正确、编辑模式仍发 itemId、非 Registrar 无按钮、值域等共用对话框视图的覆盖确认；全量绿 + build 过

## 3. 文档与收尾

- [x] 3.1 `openspec/specs/system.md` 增设 AI 辅助小节，写入普及原则（见 design D4 条文）
- [x] 3.2 未归档 ai-assist-ux-revamp 变更加变更记录（门控放开与草稿模式）；本变更 validate --strict 通过
- [x] 3.3 GitNexus：编辑前 impact、收尾 detect_changes（无 partial/truncated）
- [x] 3.4 部署：重打包 jar + dist 同步 static + 重启后端验证端点在线
