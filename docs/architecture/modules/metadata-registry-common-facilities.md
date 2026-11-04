# metadata-registry-common-facilities

# metadata-registry-common-facilities

`metadata-registry-common-facilities` 是元数据注册工程的公共领域契约层，提供核心实体、预定义类型、注册项、标识、命名与定义等可复用 Java 模型。模块还集中声明 Jakarta Persistence、Jakarta Annotation、Jackson、Lombok 与 Hibernate 注解相关的编译依赖，使业务模块能够直接复用统一的领域类型和映射能力。

## 子模块职责

| 子模块 | 作用 |
| --- | --- |
| [`metadata-registry-common-facilities`](metadata-registry-common-facilities.md) | 定义 Maven 模块坐标、父工程继承关系及公共依赖边界。依赖版本和通用构建配置由 `metadata-registry4j-parent` 统一管理。 |
| [`src`](src.md) | 承载公共领域模型及其类型关系，包括基础实体、预定义值类型、注册与标识模型、名称和定义模型。 |

## 模型协作方式

- **基础模型层**以 `Entity`、`Account`、`Individual`、`Organization`、`Role` 和 `Contact` 为核心，并使用 `PostalAddress`、`Sign`、`Date`、`Datetime`、`Text`、`StringType`、`IntegerType`、`BooleanType` 等值类型描述实体属性。`PostalAddress`、`Sign`、`Text` 等类型提供基于值的 `equals` 与 `hashCode` 语义。
- **标识与注册层**通过 `Namespace` 和 `IdentifiedItem` 建立标识基础；`RegisteredItem` 依赖 `IdentifiedItem`，`AdministeredItem` 与 `AttachedItem` 又以 `RegisteredItem` 为基础。`Registrar` 使用 `Contact` 描述登记主体。
- **时间范围模型**由 `Datetime_Period` 和 `contains` 方法表达区间关系，支持有界区间以及开放起点或开放终点。
- **名称与定义层**包含 `Definition`、`Designation` 和 `AcceptabilityConverter`。`Definition` 与 `Designation` 提供语言属性，`AcceptabilityConverter` 负责可接受性表示之间的转换。

## 跨模块多语言处理流程

`ConceptualDomainController.get` 和 `ConstraintSetController.get` 最终都会读取公共定义或名称中的语言信息：

```text
Controller.get
  → Service.get / toResponse
  → ItemAdministrationService.mapDefinitions / mapDefinitionContexts
  → ItemNamingHelper.preferredDesignation / pickByLanguage
  → Definition.getLanguage 或 Designation.getLanguage
```

该流程将 Web 查询、响应组装、定义上下文映射和首选名称选择连接起来；公共模块负责提供 `Definition`、`Designation` 等稳定的数据契约，而控制器、服务和辅助工具负责编排具体业务逻辑。模块自身不提供可执行入口或后台任务。