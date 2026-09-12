# 技术栈

## 技术栈总览

| 层级 | 技术 |
|---|---|
| 后端 | Java 17, Spring Boot 3.3.4, Spring Data JPA, QueryDSL, Spring Security, JWT (jjwt 0.12.6) |
| 数据库 | SQLite / SQL Server / 达梦（由 `DB_VENDOR` 环境变量选择，默认 `sqlserver`） |
| 数据库迁移 | Flyway（按方言分目录，规则见 [database-portability.md](database-portability.md)） |
| API 文档 | SpringDoc OpenAPI 2.6.0 (Swagger UI) |
| 前端 | Vue 3.5, TypeScript, Vite, Element Plus 2.8, Pinia, Vue Router 4, Axios |
| 元数据模型 | 基于 ISO/IEC 11179-3:2023 |
| 数据交换 | FHIR R4 映射支持 |

## 后端模块

```
metadata-registry-common-facilities/      # 核心实体与基础设施
metadata-registry-data-specification/     # 数据规范
metadata-registry-data-set-registration/  # 数据集注册
metadata-registry-item-mapping/           # 项目映射
metadata-registry-schema-extraction/      # 模式提取
metadata-registry-draft-generation/       # 草案生成
metadata-registry-ext/                    # 扩展
metadata-registry-web/                    # Spring Boot 后端 + REST API
metadata-registry-ui/                     # Vue 3 前端（独立构建）
```

## 相关文档

- [database-portability.md](database-portability.md) — 数据库可移植性规则
- [api/](api/) — API 契约（openapi.json）与约定（conventions.md）
- [docs/architecture/ARCHITECTURE.md](../architecture/ARCHITECTURE.md) — 架构文档（模块结构、功能区域、执行流程）
