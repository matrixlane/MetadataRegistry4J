# API 约定

本文档约定 MetadataRegistry4j REST API 的通用契约。逐端点的完整契约见本目录 [`openapi.json`](openapi.json)（构建时生成）与运行时的 Swagger UI（`/swagger-ui.html`）。

## 基础信息

| 项 | 值 |
| --- | --- |
| API 前缀 | `/api/v1/` |
| 认证方式 | JWT Bearer |
| OpenAPI 规范 | SpringDoc OpenAPI 2.6.0，`/v3/api-docs`（匿名可访问） |
| 契约文件 | [`openapi.json`](openapi.json) — 由 `OpenApiExportTest` 生成，签入版本库 |

重新生成契约文件（后端契约变更后执行）：

```bash
mvn -pl metadata-registry-web test -Dtest=OpenApiExportTest -Dopenapi.export=true
```

## 认证

1. `POST /api/v1/auth/login` 获取令牌：

```json
{
  "token": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresInSeconds": 86400,
  "user": { }
}
```

2. 后续请求携带请求头：`Authorization: Bearer <token>`

## 统一响应信封

所有端点返回 `ApiResponse` 信封：

```json
{
  "code": 0,
  "message": "OK",
  "data": { },
  "timestamp": "2026-09-13T10:00:00+08:00"
}
```

- `code = 0` 表示成功；非 0 即为错误（见错误码）
- `data` 为 null 时该字段不输出（`NON_NULL` 序列化）

## 错误码

| 码 | 含义 |
| --- | --- |
| `0` | 成功 |
| `400` / `401` / `403` / `404` / `409` | 与 HTTP 语义对齐的通用错误 |
| `422` | 参数校验失败（VALIDATION_FAILED） |
| `500` | 服务器内部错误 |
| `1000` | 通用业务错误（BUSINESS_ERROR） |
| `1001` | 功能未启用（FEATURE_NOT_ENABLED） |
| `1100–1199` | 注册项生命周期状态转移错误（详见 `ApiCodes`） |

## 分页

列表端点返回 `PageResponse`：

```json
{
  "content": [ ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7
}
```

请求侧使用 Spring Data 分页参数：`page`（从 0 起）、`size`、`sort`。

## 匿名可访问端点（无需令牌）

- `POST /api/v1/auth/login`、`/api/v1/auth/register`、`/api/v1/auth/allow-registration`
- `/api/v1/datasource/**`、`/api/v1/migration/**`
- `/api/v1/init-wizard/**`（初始化向导）
- `/api/v1/identifier-strategy/presets`、`/object-abbreviations`、`/preview`（标识模板匿名只读）
- `/api/v1/federation/inbound/**`（联邦入站）
- `/swagger-ui.html`、`/v3/api-docs`、`/actuator/health`、`/actuator/info`
- 静态资源：`/`、`/index.html`、`/favicon.svg`、`/assets/**`

## 角色约束

| 角色 | 范围 |
| --- | --- |
| `SUPER_ADMIN` | `/api/v1/system/**`、`/actuator/loggers/**` |
| `REGISTRAR` | `/api/v1/inference/**`、`/api/v1/assistant/**` 使用类端点；`GET /api/v1/assistant/settings` |
| `SUPER_ADMIN` | `PUT /api/v1/assistant/settings`（配置类） |
| 其余规则 | 默认：GET 登录即可，写操作需 `SUPER_ADMIN` 或 `REGISTRAR` |

角色定义见 `AppUserRole`；具体端点的角色要求以 `SecurityConfig` 与 openapi.json 的 security 声明为准。
