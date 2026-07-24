# Tech Stack & 编码规范 / 局限

## 一、核心技术栈

### 后端
| 组件 | 版本 / 选型 | 说明 |
|------|-------------|------|
| JDK | 17 | `maven.compiler.source/target` 锁定 |
| Spring Boot | 3.3.4 | 主框架，依赖管理通过 `spring-boot-dependencies` BOM |
| 数据库 | SQL Server | 驱动 `mssql-jdbc` 12.6.3，HikariCP 5.1.0 连接池 |
| ORM | JPA / Hibernate 6 | `SQLServerDialect`，`ddl-auto: ${DDL_AUTO:update}` |
| 迁移 | Flyway | `baseline-on-migrate: true` |
| 安全 | Spring Security + JWT | `jjwt` 0.12.6，无状态认证 |
| API 文档 | SpringDoc OpenAPI | 2.6.0，Swagger UI 路径 `/swagger-ui.html` |
| 工具库 | Lombok、MapStruct | Lombok 1.18.34（编译期注解处理器） |
| 测试 | JUnit 5、Mockito、Testcontainers | `mssqlserver` + `jdbc` Testcontainers 1.20.1 |
| 覆盖率 | JaCoCo | 0.8.12，绑定到 `test` phase 生成报告 |

### 前端
| 组件 | 版本 / 选型 | 说明 |
|------|-------------|------|
| 框架 | Vue 3.5 | Composition API + `<script setup lang="ts">` |
| 构建 | Vite 5.4 | 开发端口 5173，代理 `/api` → `localhost:8081` |
| 类型 | TypeScript 5.6 | `strict: true`，`verbatimModuleSyntax: true` |
| UI 库 | Element Plus 2.8 | Sass 变量覆盖，`@use` modern-compiler API |
| 状态 | Pinia 2.2 | Store 以 `defineStore('id', () => { ... })` Setup 风格编写 |
| 路由 | Vue Router 4.4 | `createWebHistory`，路由表集中配置 |
| 请求 | Axios 1.7 | 统一封装在 `utils/request.ts`，拦截器内直接返回 `response.data` |
| 图表 | ECharts 6 + vue-echarts | — |
| 国际化 | vue-i18n 9.14 | — |
| 图标 | @element-plus/icons-vue | 全局循环注册所有图标组件 |
| 自动导入 | unplugin-auto-import / unplugin-vue-components | 预配置 Vue、Vue Router、Pinia、Element Plus 的自动导入 |
| E2E | Playwright 1.60 | — |

---

## 二、编码规范（从代码库中提炼）

### 2.1 Java 后端

#### 包命名与模块边界
- 根包：`io.github.matrixlane.metadataregistry`
- 各子模块在根包下按功能划分二级包（如 `commonfacilities`、`itemmapping`、`schemaextraction`）
- `web` 模块通过 `@SpringBootApplication(scanBasePackages = "io.github.matrixlane.metadataregistry")` 扫描全部子模块

#### Lombok 使用惯例
- **实体类**：`@Getter @Setter @NoArgsConstructor`，需要 Builder 时加 `@Builder @AllArgsConstructor`
- **DTO/Response**：`@Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(NON_NULL)`
- **日志**：优先使用 `@Slf4j`（Lombok），但存在少量手动 `LoggerFactory.getLogger(...)` 的旧代码
- **Controller/Service**：`@RequiredArgsConstructor` 进行字段注入（构造器注入）

#### 实体与持久化
- 主键统一使用 `UUID`，生成策略为 `GenerationType.UUID`
- 表名显式标注 `@Table(name = "...")`，列名使用下划线命名
- 审计字段使用 `@CreatedDate` + `AuditingEntityListener`
- `equals/hashCode` 基于 `UUID id` 实现，null-id 时返回 false / 0

#### API 设计规范
- 统一返回包装：`ApiResponse<T>`（code / message / data / timestamp）
- 分页统一返回：`PageResponse<T>`，通过 `PageResponse.of(Page<T>)` 或 `PageResponse.of(Page<T>, Function<T, R>)` 转换
- Controller 路径前缀：`/api/v1/...`
- Swagger 分组：`@Tag(name = "序号. 中文名称", description = "...")`
- 入参校验：DTO 使用 `jakarta.validation` 注解（`@NotBlank`、`@Email`、`@Size` 等），Controller 参数前加 `@Valid`
- 请求 DTO 使用 Java `record`，响应 DTO 使用 `class`（因需 `@Builder`）

#### 异常处理
- 全局处理器：`@RestControllerAdvice` + `GlobalExceptionHandler`
- 业务异常：`BusinessException extends RuntimeException`，携带 `int code`
- HTTP 状态码映射：
  - `400` → `BusinessException`、`IllegalArgumentException`、`MethodArgumentTypeMismatchException`
  - `401` → `BadCredentialsException`、`AuthenticationException`
  - `403` → `AccessDeniedException`
  - `404` → `ResourceNotFoundException`、`EntityNotFoundException`
  - `409` → `DataIntegrityViolationException`
  - `422` → `MethodArgumentNotValidException`
  - `500` → 兜底 `Exception`，日志记录完整堆栈

#### 测试规范
- 单元测试：JUnit 5 + Mockito，`@ExtendWith(MockitoExtension.class)`
- Controller 层测试：`@WebMvcTest` + `@AutoConfigureMockMvc(addFilters = false)` + `@MockBean`
- 集成测试：`@SpringBootTest(webEnvironment = RANDOM_PORT / MOCK)`
- 无 `@DataJpaTest`（项目中未使用），持久层逻辑通过集成测试或纯 Mockito 测试覆盖
- 常量命名：测试类中固定 UUID 使用 `UPPER_SNAKE_CASE`（如 `ITEM_ID`、`RECORD_ID`）

### 2.2 前端

#### Vue / TypeScript
- 统一使用 `<script setup lang="ts">` + Composition API
- 自动导入：Vue、Vue Router、Pinia 的 API 无需显式 import（由 `unplugin-auto-import` 处理）
- Element Plus 组件和图标同样自动导入（由 `unplugin-vue-components` 处理）
- 路径别名：`@/` 指向 `src/`

#### 状态管理（Pinia）
- 全部采用 Setup Store 风格：`defineStore('id', () => { ... })`
- 使用 `ref` / `computed` 暴露状态，`function` 暴露方法
- 认证 Token 持久化到 `localStorage`

#### HTTP 请求
- 统一入口：`utils/request.ts` 基于 Axios 创建实例
- 拦截器行为：
  - Request：自动注入 `Authorization: Bearer <token>`
  - Response：判断 `body.code !== 0` 时弹出 `ElMessage.error` 并 `Promise.reject`
  - 401 时自动清空登录态并重定向到 `/login`
- 封装 `http.get/post/put/delete<T>()` 泛型方法，直接返回 `Promise<T>`（已剥掉外层 `ApiResponse`）

#### 样式
- SCSS 预处理器，使用 `modern-compiler` API
- 全局 Element Plus 变量覆盖通过 `additionalData` 注入

---

## 三、已知局限与约束

### 3.1 数据库与运行环境
- **强绑定 SQL Server**：方言、Flyway 脚本、连接串中的 `integratedSecurity=true` 和 `encrypt=true;trustServerCertificate=true` 均为 SQL Server 特有配置
- **Windows 本地开发依赖**：根目录存在 `mssql-jdbc_auth-12.6.3.x64.dll`，本地集成安全认证需要此 DLL
- **无多数据库适配层**：`schema-extraction` 模块虽支持 MySQL/Oracle/PostgreSQL/SQL Server 的反向提取，但应用自身运行只针对 SQL Server
- **ddl-auto 风险**：开发环境默认 `update`，生产环境需显式覆盖为 `none` 或 `validate`

### 3.2 安全
- **JWT 默认密钥硬编码**：`application.yml` 中默认 `JWT_SECRET` 为占位字符串，生产环境必须通过环境变量注入
- **CORS 允许本地开发前端**：`http://localhost:5173` 为默认允许源
- **SEED 默认开启**：`SEED_ENABLED=true` 可能导致演示数据在生产环境意外写入

### 3.3 代码质量工具缺失
- 项目中**未配置** Checkstyle、Spotless、PMD、SpotBugs 等静态代码检查/格式化工具
- 无 `.editorconfig`，代码风格完全依赖 IDE 和开发者自律
- 无代码格式化钩子（Git Hooks 未配置）

### 3.4 架构/设计约束
- **无 MapStruct**：项目中已引入 MapStruct 依赖管理，但实际代码中 grep 无 `@Mapper` 使用痕迹，DTO 转换可能依赖手动 Builder 或流式映射
- **日志混用**：大部分类使用 `@Slf4j`，但少数类（如 `DataQualityController`、`ComplianceReportService`）仍手动声明 `LoggerFactory.getLogger(...)`
- **前端 axios 非标准封装**：响应拦截器直接返回 `body.data`，丢失了原始 `response` 对象（如 headers、status），特殊场景下需绕过封装
- **Flyway 迁移文件命名**：采用 `VYYYYMMDDNNN__Description.sql` 格式，多人同日开发时序号冲突风险较高
