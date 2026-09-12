# 提案：AI 元数据推断（对接本地 Coding Agent）

## Why

注册人员在登记元数据项目（如数据元）时，常遇到「已知部分元数据、缺失另一部分」的情况：例如已掌握来源文档、上下文与指称，却缺少符合 ISO/IEC 11179-1:2023 定义构成规则（§6.2–6.3）的定义。目前只能靠人工撰写，效率低且质量不一。本变更让应用能够调用**本机已安装的 Coding Agent CLI**（Claude Code / Codex / Kimi Code CLI 等，自带用户本人的订阅认证），基于项目已有元数据推断缺失内容并给出多个候选，由人选择采纳，从而避免在应用内直接保管各模型厂商的 API Key。

## What Changes

- 新增 `/api/v1/inference` 推断接口族：提供方可用性查询、创建推断作业、轮询作业状态/候选结果、取消作业（异步作业 + 轮询，作业状态存内存，不新增数据库表）。
- 新增推断提供方 SPI：首选 **本地 CLI 适配器**（`ProcessBuilder` 调本机 Coding Agent，提示词走 stdin，内置 claude/codex/kimi 预设且命令模板可配置）；兜底 **OpenAI 兼容直连适配器**（Key 仅存服务端环境变量/本地配置文件）。
- 新增提示词组装器：把项目的指称、定义、上下文、来源文档、数据元概念、值域、属性槽等已知元数据组装为结构化提示词，内嵌 ISO/IEC 11179-1:2023 §6.2–6.3（定义构成）与 §6.4（命名原则，命名细则见 11179-31:2023 §5.5）写作规则，要求 Agent 以严格 JSON 返回每字段多个候选。
- 数据元编辑对话框（ItemFormDialog）新增「AI 推断」入口与候选选择对话框：候选仅**填入表单**，用户确认后走既有保存路径（版本历史/快照机制不变）。
- 新增 `mdr.inference.*` 配置块（提供方选择、CLI 预设/命令/超时/工作目录、直连 base-url/model，Key 仅取 `MDR_LLM_API_KEY` 环境变量），默认启用、可整体关闭。
- 本期范围：推断字段为**定义**与**指称**（可指定语种/上下文）。分类建议、DEC/VD 推荐不在本期。

## Capabilities

### New Capabilities

- `ai-metadata-inference`: 基于项目已有元数据、通过可插拔推断提供方（本地 Coding Agent CLI 或 OpenAI 兼容直连）异步推断缺失元数据（定义/指称），返回多候选供人工选择并填入编辑表单的能力。

### Modified Capabilities

（无。数据元编辑的既有保存路径与需求不变，仅新增入口。）

## Impact

- **后端**：`metadata-registry-web` 新增 `web/inference/` 包（配置属性、提供方 SPI、CLI/直连适配器、提示词组装器、内存作业服务、REST 控制器）；`application.yml` 追加 `mdr.inference.*` 配置；新增 `@EnableAsync` 配置类与推断线程池（复用 `MdcTaskDecorator`）。不新增 Maven 模块、不新增第三方依赖（直连用 JDK HttpClient）、不新增数据库表与 Flyway 迁移。
- **安全**：新端点位于 `/api/v1/**` 之下，自动落入既有规则（GET 需登录，POST/DELETE 需 REGISTRAR/SUPER_ADMIN）；CLI 进程以应用启动用户的操作系统身份运行，使用该用户在本机 CLI 的既有登录态。
- **前端**：`metadata-registry-ui` 新增 `api/inference.ts` 与候选选择对话框组件，`ItemFormDialog.vue` 增加入口按钮与 i18n 文案。
- **标准依据**：提示词写作规则依据 ISO/IEC 11179-1:2023 §6.2–6.4 与 ISO/IEC 11179-31:2023 §5.5（条款经 Milvus 检索核对。本变更早期稿误引 11179-4/11179-5，已按 2023 版原文更正；更正记录见 `design.md` Goals）。
