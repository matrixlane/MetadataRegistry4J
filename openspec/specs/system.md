# MetadataRegistry4j 全局高级架构地图

## 项目定位

**MetadataRegistry4j** 是 ISO/IEC 11179 元数据注册表（Metadata Registry）标准的 Java 实现，用于元数据项（Data Element、Value Domain、Concept 等）的注册、管理、映射与合规性检查。

---

## 技术栈总览

| 层级 | 技术 |
|------|------|
| **后端** | Spring Boot 3.3.4 + Java 17 + Maven 多模块 |
| **数据库** | SQL Server + HikariCP + JPA/Hibernate + Flyway 迁移 |
| **安全** | Spring Security + JWT (jjwt) |
| **API 文档** | SpringDoc OpenAPI (Swagger UI) |
| **前端** | Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vue Router |
| **图表** | ECharts 6 |
| **测试** | JUnit + Testcontainers (MSSQL) + Playwright (E2E) |
| **工具库** | Lombok、MapStruct、Jacoco |

---

## 多模块架构

```
metadata-registry4j-parent (pom)
│
├─ metadata-registry-common-facilities      ← ISO 11179 核心领域模型
│     ├─ basicandcore          基础类与核心概念
│     ├─ identification        标识与命名空间
│     ├─ designationanddefinition  定义与描述
│     └─ registration          注册管理
│
├─ metadata-registry-data-specification     ← 数据规范定义
│
├─ metadata-registry-data-set-registration  ← 数据集注册管理
│     └─ entity / dto / repository / service
│
├─ metadata-registry-item-mapping           ← 元数据项映射与匹配
│     ├─ matching              匹配算法与配置
│     ├─ quality               映射质量评估
│     ├─ impact                变更影响分析
│     └─ notification          映射通知机制
│
├─ metadata-registry-schema-extraction      ← 数据库/源码模式提取
│     ├─ mysql / oracle / postgresql / sqlserver  多数据库支持
│     ├─ javasource            Java 源码解析
│     ├─ openapi               OpenAPI 规范解析
│     └─ mapping / strategy    映射策略
│
├─ metadata-registry-draft-generation       ← 草案/代码生成
│     ├─ generator             生成器引擎
│     ├─ naming                命名规范
│     └─ persistence           持久化映射
│
├─ metadata-registry-web                    ← 应用入口与 Web API
│     ├─ auth                  JWT 认证与权限
│     ├─ controller / dto      REST API 层
│     ├─ service               业务逻辑层
│     ├─ repository / entity   数据访问层
│     ├─ notification          通知系统（邮件/站内信）
│     ├─ seed                  数据初始化
│     ├─ fhir                  FHIR 合规性支持
│     └─ logging               审计日志
│
└─ metadata-registry-ui                     ← 前端 SPA
      ├─ views                 业务页面（命名空间、数据元素、值域等）
      ├─ api                   后端接口封装
      ├─ stores (Pinia)        状态管理
      ├─ router                路由配置
      └─ i18n                  国际化
```

---

## 核心入口

| 入口 | 路径 |
|------|------|
| **Spring Boot 启动类** | `metadata-registry-web/src/main/java/.../web/MetadataRegistryApplication.java` |
| **前端 SPA 入口** | `metadata-registry-ui/src/main.ts` |
| **根 POM** | `pom.xml` |
| **配置文件** | `application.yml` + `application-local.yml` |
| **数据库迁移** | `metadata-registry-web/src/main/resources/db/migration/` |

---

## 关键特征

- **标准驱动**：严格遵循 ISO/IEC 11179-3:2023 元数据注册模型
- **分层清晰**：领域模型（common-facilities）与 Web 应用层彻底分离
- **多源模式提取**：支持从主流数据库、Java 源码、OpenAPI 反向提取元数据
- **FHIR 支持**：内置 FHIR 资源合规性检查与报告
- **审批工作流**：元数据项注册需经审批流程（状态机驱动）
- **通知系统**：完整的事件驱动通知机制（邮件 + 站内消息）
- **安全**：JWT 无状态认证 + 角色权限控制
