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

首次启动时（团队版 SQL Server / 达梦）：
- Hibernate 自动创建 60+ 张表（`mdr` 标准层 + `ext` 扩展卫星层）。
- `StaticDataPresetService` 自动写入静态预置数据（语言、全局默认标识策略、占位管理员、标准流转规则等；演示数据由 `app.seed.demo-data.enabled` 单独开关）。
- Flyway 按 `db/migration/<方言>/` 管理基线之后的迁移，三方言共用 SQL Server 版本号序列。
- 初始化向导完成（setup 并 commit）后自动创建 **Registry Context**（注册表上下文，归属向导创建的注册机构，命名空间为注册机构自有命名空间，标识/版本由命名空间生成策略产出；指称 `Registry Context`/`注册表上下文` 及其定义的语境自指），并回填注册规范的默认上下文；已初始化系统升级后首次启动自动补建。

### 个人版（SQLite）首次启动

个人版零外部依赖，默认使用本地 SQLite 文件库，无需上述建库步骤：

```bash
java -jar metadata-registry-web.jar --spring.profiles.active=personal
```

- 默认数据库文件为用户目录下 `~/.metadata-registry/data/metadata-registry.db`（可用 `DB_URL` 环境变量覆盖）。文件及其父目录不存在时会自动创建。程序目录 `data/metadata-registry.db` 为预置基准数据库（见 `data/README.md`），可拷贝到实际库文件位置直接使用。
- 全新空库首次启动时自动初始化：先由 ORM 创建当前版本全部表结构，Flyway 再以当前最大迁移版本基线化（SQLite/SQL Server/达梦一致），随后执行静态预置（含 `data/scripts/common/<方言>/00-common.sql` 通用基准自动导入），最后开放初始化向导——无需手工导入基准库。WS/T 363/364 卫生信息标准基准为可选内容：停止应用后双击 `data/init-baseline.bat` 按当前数据源配置一键导入（详见 `data/README.md` 与 `data/scripts/data-standards/ws/README.md`）。34 份 WS/T 363/364-2023 标准原文 PDF 随仓库分发于 `data/docs/data-standards/ws/WS363/` 与 `data/docs/data-standards/ws/WS364/`，基准数据的参考文档 URI 即指向这些仓库内相对路径（克隆仓库后可直接经 URI 打开标准原文）。
- 在初始化向导中保存新的数据库配置后：
  - **通过项目启动器运行的个人版会自动应用**：系统自动重启后端、准备数据库、核对实际数据源并回到向导，无需用户执行任何命令；页面显示“正在重启应用 / 正在准备数据库 / 正在核对设置”等真实进度与等待时间。
  - 其他启动方式（直接 `java -jar`、IDE、Maven、团队版）仍提示“保存后需手工重启”。
- 新配置无法启动时（连接失败、迁移失败、启动超时、配置与目标不一致），启动器会**自动恢复原数据库配置并重新启动**，向导提示“已恢复原设置”，用户可修改后重试；失败目标库文件不会被自动删除。
- 切换方言时 ORM 映射同步切换：SQLite 使用 `orm/orm-sqlite.xml`，SQL Server/达梦使用 `orm/orm-team.xml`（否则 SQLite 会因 `ext.` 模式无法建表而启动失败）。
- 既有数据库文件完全不受影响：Flyway 历史、数据与校验和原样保留。
- SQLite 不保存账号口令。

### 启动器与自动重启（Windows 单机个人版）

`start.bat` 现在通过常驻启动器 `scripts/backend-supervisor.ps1` 托管后端进程：

- 启动器持有自己创建的后端进程，只停止自有 PID，不按端口模糊终止其他进程；用户停止应用后不会被自动重新拉起。
- 重启事务的控制文件位于用户目录 `~/.metadata-registry/restart/`（重启请求、状态与配置备份），不进入仓库；其中可能包含数据库口令，请勿对外分享。
- `stop.bat` 优先写入停止信号再关闭受管服务；若仍有旧版启动的未受管实例，只在 `scripts/port-state.ps1` 判定端口占用者**是本应用**时才停止它，外来程序仅告警不误杀。
- 启动器默认以个人版 profile 启动（内置 SQLite 文件库）。团队版部署需把启动器改为 `--spring.profiles.active=team`（见 `application-team.yml`），团队版不加载 `orm-sqlite.xml`。
- 端口与控制目录可用环境变量覆盖：`MDR_BACKEND_PORT`（默认 8081，缺省取 `SERVER_PORT`）、`MDR_FRONTEND_PORT`（默认 5173）、`MDR_RESTART_CONTROL_DIR`（默认 `~/.metadata-registry/restart`）。
- 自动重启与失败恢复目前**仅支持 Windows 单机个人版 + 项目启动器**；其他部署方式保持原有手工重启行为。
- **环境变量会覆盖数据源设置**：数据源配置以 `${DB_URL:默认值}` 等形式落盘，若机器上已设置 `DB_URL`、`DB_VENDOR`、`HIBERNATE_DIALECT`、`DB_DEFAULT_SCHEMA`、`FLYWAY_SCHEMAS`、`FLYWAY_DEFAULT_SCHEMA` 且取值与默认值不同，系统会在保存前直接拒绝并指出冲突的环境变量（重启后仍会用旧目标）。
- **自动重启不可用时的提示**：若同一控制目录已有启动器实例在运行，新实例会写出 `~/.metadata-registry/restart/supervisor.error`，`start.bat` 会提示"自动重启不可用，请先运行 stop.bat"；启动器被强杀后重新运行会先清理上次遗留、仍占用端口的后端进程。
- **失败不中断系统**：重启目标无法启动或旧配置恢复失败时，系统先记录 `RECOVERY_FAILED` 并以磁盘上现有配置继续运行，不会停在"已停止"状态；含明文口令的配置备份在成功或恢复就绪后立即清理，失败时保留用于诊断，超过 7 天后自动删除。

### 初始化阶段的数据库依赖延后（init-phase-database-free）

数据库配置完成之前，应用的启动与运行**零数据库行为**——不建连接、不查表、不跑迁移，初始化向导始终可打开：

- **初始化状态不来自数据库**：以 `~/.metadata-registry/initialization-state.yml` 记录（数据源是否已确认、配置指纹、是否已初始化）辅以数据库配置是否存在为准；向导保存数据源时自动写入确认与指纹，配置被手工改动会识别为指纹失配并退回向导要求重新确认。既有部署仅在记录缺失时一次性对齐，不会被推回向导。
- **数据库初始化在配置完成后的那次自动重启中完成**：连接校验 → 建表 → 迁移 → 预置 → 治理回填，全部在该次重启内做完，向导随后自动续上。"保持当前数据源并继续"同样会确认并触发这一次重启。
- **初始化失败不黑屏**：数据库不可达或结构不兼容时，应用保持运行，界面显示"初始化系统失败"与可读原因，提供「重试初始化」与「退出应用」，等待用户处理。
- 该机制由个人版/团队版 profile 的 `app.bootstrap.enabled=true` 开启（默认关闭）；其他启动方式与集成测试环境不受影响。

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

> 以上账号仅适用于**团队版（TEAM）**——个人版（PERSONAL）无登录概念：
> 本地访问直接进入应用（以唯一管理员身份运行），不存在登录页/注册入口。

---

## API 文档

后端启动后访问：http://localhost:8181/swagger-ui.html

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
- **我的** — 固定于侧边栏底部：个人资料 + 联系人身份管理（多身份/默认身份；Registrar 身份可编辑、可删除，删除效果相当于初始化时未设为注册人员）

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
