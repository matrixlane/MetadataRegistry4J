# MetadataRegistry4j

基于 ISO/IEC 11179-3:2023 的元数据注册中心（Metadata Registry）。

---

## 项目结构

```
MetadataRegistry4j/
├── metadata-registry-common-facilities/      # ISO 11179-3 领域模型（Common Facilities）
├── metadata-registry-data-specification/     # ISO 11179-3 领域模型（Data Specification）
├── metadata-registry-data-set-registration/  # 数据集注册（占位）
├── metadata-registry-item-mapping/           # 条目映射（ISO/IEC 11179-3 第 11 章 Item_Mapping）
├── metadata-registry-schema-extraction/      # 模式提取
├── metadata-registry-draft-generation/       # 草案生成
├── metadata-registry-ext/                    # 扩展
├── metadata-registry-web/                    # Spring Boot 后端 + REST API
└── metadata-registry-ui/                     # Vue 3 + Element Plus 前端
```

---

## 文档导航

| 目录 | 内容 |
|---|---|
| [docs/architecture/](docs/architecture/) | 架构文档（模块结构、功能区域、执行流程）、领域模型 |
| [openspec/changes/](openspec/changes/) | 进行中变更的设计文档（proposal / design / specs / tasks 四件套） |
| [docs/technical/](docs/technical/) | 技术栈、数据库可移植性规则 |
| [docs/superpowers/](docs/superpowers/) | superpowers 工作流产出（设计草稿 `specs/`、实施计划 `plans/`） |
| [docs/audits/](docs/audits/) | ISO/IEC 11179 一致性审计与对比报告 |

---

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Java 17, Spring Boot 3.5.0, Spring Data JPA, Spring Security, JWT (jjwt 0.12.6) |
| 数据库 | SQLite / SQL Server / 达梦（`DB_VENDOR` 选择，默认 `sqlserver`；详见 [docs/technical/](docs/technical/)） |
| API 文档 | SpringDoc OpenAPI 2.8.8 (Swagger UI) |
| 前端 | Vue 3.5, TypeScript, Vite, Element Plus 2.8, Pinia, Vue Router 4, Axios |

---

## 快速开始

### 1. 数据库准备

使用 SQL Server 创建数据库（以 Windows 身份验证执行）：

```sql
CREATE DATABASE metadata_registry;
GO
```

当前 Windows 用户会自动以 `dbo` 身份访问该数据库。若使用其他 Windows 账号运行后端，请确保该账号已加入数据库的 `db_owner` 角色。

### 2. 配置后端

`metadata-registry-web/src/main/resources/application.yml` 默认使用 **Windows 身份验证**（`integratedSecurity=true`），无需配置用户名密码。

只需设置 JWT 密钥（建议修改）：

```bash
set JWT_SECRET=your-32-byte-secret-key-here!!!
```

### 3. 启动后端

```bash
cd metadata-registry-web
mvn spring-boot:run
```

或从项目根目录：

```bash
mvn -pl metadata-registry-web -am spring-boot:run
```

首次启动时：
- Hibernate 自动创建 60+ 张表。
- `DataInitializer` 自动写入种子数据（演示账号、语言、注册机构、命名空间、数据类型、对象类、特性、数据元素等）。
- 初始化向导完成（setup 并 commit）后自动创建 **Registry Context**（注册系统默认上下文，归属向导创建的注册机构，命名空间为注册机构自有命名空间，标识/版本由命名空间生成策略产出；指称 `Registry Context`/`注册系统上下文` 及其定义的语境自指），并回填注册规范的默认上下文；已初始化系统升级后首次启动自动补建。

### 4. 启动前端

```bash
cd metadata-registry-ui
npm install
npm run dev
```

浏览器访问 http://localhost:5173 。

---

## 默认账号

| 账号 | 密码 | 角色 |
|---|---|---|
| admin | Admin@123 | ADMIN |
| registrar | Registrar@123 | REGISTRAR |
| viewer | Viewer@123 | VIEWER |

---

## API 文档

后端启动后访问：http://localhost:8080/swagger-ui.html

---

## 前端页面

侧边栏导航按 ISO/IEC 11179-3 元模型模块分组（详见 `openspec/specs/standardize-menu-layout/spec.md`）：

- **概览** (Dashboard) — 统计卡片 + 近期活动
- **数据字典** — 元数据浏览器
- **概念模块** — 对象类 / 特性 / 数据元概念 / 概念域 / 值含义 / 维度 / 计量类 / 计量单位
- **表示模块** — 数据元 / 值域 / 数据类型方案 / 数据类型
- **基础与核心模块** — 个人 / 组织 / 角色 / 联系人 / 文档类型 / 语言 / 参考文档 / 上下文
- **标识与命名模块** — 命名空间 / 命名规范
- **注册模块** — 注册机构 / 注册规范 / 注册人员 / 注册流程（标准流程只读展示）
- **工作空间** — 草稿 / 待审核 / 已拒绝三视图与行级操作（WorkspaceView）
- **分类模块** — 分类方案 / 分类项（规划中）
- **项目映射模块** — 条目映射（ISO/IEC 11179-3 第 11 章 Item_Mapping）
- **系统** — 用户管理 / 系统设置（仅 ADMIN）
- **我的** — 固定于侧边栏底部：个人资料 + 联系人身份管理（多身份/默认身份；Registrar 身份只读可见）

---

## 开发说明

### 后端关键包

- `auth` — 本地账号 + JWT 认证
- `controller` — REST 控制器，统一前缀 `/api/v1/`
- `service` — 业务服务
- `repository` — Spring Data JPA
- `dto` — 请求/响应 DTO（Java records）
- `seed` — 启动时种子数据（idempotent）

### 前端关键目录

- `src/api/resources.ts` — 各资源 REST API 封装
- `src/components/DataTablePage.vue` — 通用列表页（分页/搜索/CRUD）
- `src/components/ResourceFormDialog.vue` — 通用新建/编辑弹窗
- `src/layouts/AppLayout.vue` — 主布局（侧边栏 + 顶部栏）
- `src/stores/auth.ts` — 认证状态（Pinia）

### 主题

Element Plus 主色覆盖为 `#0c3c78`（深海军蓝），营造 ERP 风格的专业界面。变量见 `src/styles/element-variables.scss`。

---

## 生产部署

### 后端打包

```bash
mvn -pl metadata-registry-web -am -DskipTests package
```

### 前端打包

```bash
cd metadata-registry-ui
npm run build
```

产物位于 `dist/`，可由后端 `src/main/resources/static/` 托管，也可通过 Nginx 独立部署。

---

## 许可证

MIT
