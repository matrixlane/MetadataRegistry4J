# MetadataRegistry4j

基于 ISO/IEC 11179-3:2023 的元数据注册中心（Metadata Registry）。

---

## 项目结构

```
MetadataRegistry4j/
├── metadata-registry-common-facilities/      # ISO 11179-3 领域模型（Common Facilities）
├── metadata-registry-data-specification/     # ISO 11179-3 领域模型（Data Specification）
├── metadata-registry-data-set-registration/  # 数据集注册（占位）
├── metadata-registry-item-mapping/           # 项目映射
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

- **概览** (Dashboard) — 统计卡片 + 近期活动
- **注册管理** — 注册机构 / 注册员 / 命名空间 / 注册项生命周期
- **主体** — 组织 / 个人 / 联系人 / 参考文献
- **概念** — 对象类 / 特性 / 数据元素概念
- **域** — 概念域 / 值域 / 数据类型 / 数据类型方案 / 计量单位 / 计量类
- **数据元素** — 核心主线入口
- **系统** — 用户管理（仅 ADMIN）

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
