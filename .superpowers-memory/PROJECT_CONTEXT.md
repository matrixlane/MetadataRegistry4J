# Project Context

Use this file for stable project knowledge that should survive across sessions.

## Project Summary

- What this project does: 元数据注册系统（MDR），依据 ISO/IEC 11179 系列标准（2023 版，本机标准库 8 个部分）。
- Who uses it: 元数据管理/注册相关人员。
- Main goals: 注册项管理、数据规范/数据集注册、item mapping、schema 提取等。

## Architecture Notes

- Core modules:
- Important boundaries:
- Key data flows:

## Working Agreements

- Coding conventions:
- Testing expectations:
- Deployment notes:

## Known Constraints

- Technical constraints:
- Product constraints:
- Operational constraints:

## Durable Facts

Use this section for stable facts that need explicit metadata and later review.

```md
### Fact: <short-title>
- id: context-YYYY-MM-DD-<slug>
- type: durable_fact
- status: active
- confidence: verified
- last_updated: YYYY-MM-DD
- source:
- owner:
- review_after:

Statement:

Why it matters:
```

### Fact: 各 Agent 宿主的 MCP 配置互相独立
- id: context-2026-09-13-mcp-config-per-host
- type: durable_fact
- status: active
- confidence: verified
- last_updated: 2026-09-13
- source: 2026-09-13 会话实测（milvus 仅在 Claude Code 注册、Kimi Code 缺失）
- owner: matrixlane
- review_after: 2026-12-13

Statement:

Kimi Code 读 `~/.kimi-code/mcp.json`，Claude Code 读 `~/.claude.json`，二者互不同步。本项目依赖的 MCP server：gitnexus（代码图谱，两宿主均已配置）、milvus（ISO 11179 向量检索，2026-09-13 已补齐到 Kimi Code）。MCP server 仅在会话启动时加载，改配置后须开新会话。

Why it matters:

在某个宿主里"MCP 能用"不代表其他宿主已注册；排障时先查当前宿主的配置文件，避免误判服务故障。
