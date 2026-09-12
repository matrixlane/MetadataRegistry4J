> 本文档由 GitNexus wiki 生成于 2026-09-13（kimi-for-coding，--lang chinese）。内容变化时请重新运行 `gitnexus wiki --lang chinese` 生成，勿手工改写。

# metadata-registry-ui

## 模块组概述

`metadata-registry-ui` 是**元数据注册中心（Metadata Registry）**的前端应用，基于 Vue 3 + TypeScript + Vite 构建，实现 ISO/IEC 11179 元数据注册库（MDR）的完整前端能力，包括注册实体的 CRUD 与生命周期管理、多语言指称/定义编辑、分类方案树浏览、段模板标识符策略配置、联邦节点合并评审以及映射方案版本管理等。

本模块组由三个子模块构成，覆盖「工程化配置 → 业务源码 → 端到端测试」的完整链路：

| 子模块 | 职责 |
|--------|------|
| [metadata-registry-ui](metadata-registry-ui.md) | 根配置层：构建工具链（Vite）、类型检查、ESLint、Vitest 单元测试与 Playwright E2E 测试的工程化配置 |
| [src](metadata-registry-ui-src.md) | 业务源码根目录：Vue 3 组合式 API + Element Plus + Pinia 实现的全部页面、组件与状态逻辑 |
| [e2e](metadata-registry-ui-e2e.md) | 端到端测试：基于 Playwright，通过 `page.route` 拦截 mock 全部 `/api/v1/*` 请求，以真实前端代码驱动浏览器验证页面行为 |

## 子模块协作关系

根配置层（[metadata-registry-ui](metadata-registry-ui.md)）本身不含运行时代码，但它定义的构建、自动导入、代码规范与测试配置，决定了 `src/` 下所有源码的编译方式、导入约定（如 unplugin-auto-import 使 `ref`、`computed` 等无需显式 import）与质量门槛。业务源码（[src](metadata-registry-ui-src.md)）中的组件依赖此基础设施运行，例如 `DesignationDefinitionSection` 组合 `DefinitionEditor` 与 `DesignationDefinitionPairingEditor` 提供「指称-定义配对」编辑能力，`ItemFormDialog` 则整合 `MultilingualTextEditor` 与注册状态区块，形成注册实体的编辑入口。

E2E 测试（[e2e](metadata-registry-ui-e2e.md)）位于链路末端：它不依赖 Spring 后端，而是通过 `page.route` 以统一响应格式 `{ code: 0, data, message: 'success' }` mock 全部 API 请求，并用 `page.addInitScript` 注入认证 token 模拟已登录状态，从而对 [src](metadata-registry-ui-src.md) 中的真实组件（路由守卫、表单交互、API 契约）进行浏览器级验证。

```mermaid
flowchart TD
    A[根配置层<br/>metadata-registry-ui] -->|构建 / 规范 / 工具链| B[业务源码 src<br/>组件 · 视图 · 状态]
    B -->|被驱动验证| C[E2E 测试 e2e<br/>Playwright + API mock]
    C -.->|"统一响应契约<br/>code: 0"| D[/api/v1/*]
    B -->|axios 请求| D
```

## 跨子模块的关键工作流

- **开发流程**：开发者在根配置层定义的工具链下编写 `src/` 源码（Vitest 承担组件单元测试，如 `ItemFormDialog.spec.ts`、`DefinitionEditor.spec.ts`），由 Vite dev server 提供本地运行环境。
- **验证流程**：Playwright 的 `webServer` 自动拉起前端 dev server，E2E 用例拦截 API 并注入认证态后驱动真实页面——从 `AppLayout` 导航到 `BrowserView`、`ClassificationTreeBrowser` 等业务视图，验证渲染、路由守卫与表单交互是否符合后端契约。

各子模块的详细配置项、组件清单与测试策略请分别查阅子模块页面。