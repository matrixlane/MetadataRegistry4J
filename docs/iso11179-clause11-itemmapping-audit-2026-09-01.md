# ISO/IEC 11179-3:2023 第 11 章(Item_Mapping 包)审计报告

日期:2026-09-01
范围:`metadata-registry-item-mapping` 模块 `item_mapping` / `item_mapping_subject` / `item_mapping_object` 三表(含 `MappingType` 枚举)对照 ISO/IEC 11179-3:2023 第 11 章。
依据:标准原文(Milvus `anythingllm_metadata_registry` 集合,`ISO_IEC 11179-3-2023.pdf` 分块),条款 11.2–11.6、Table 32–35、Annex B 示例。
目的:供用户决策该模块的 MDR / EXT 归属。

## 1. 标准第 11 章元模型(原文核实)

| 元素 | 条款 | 内容 |
| --- | --- | --- |
| Item_Mapping class | 11.3.1 | **直接超类为 Item**(11.2.1)——其实例可被标识、注册、管理、命名、定义、分类。属性(Table 32):`degree` 1 Item_Mapping_Degree;`description` 0..1 Text(尤其在 degree='related to' 时使用);`transformation_rule` 0..1 Text;`transformation_rule_notation` 0..1 Text(条件:transformation_rule 存在时必填) |
| Subject_Mapping 关联类 | 11.4.1 | 绑定 0..* Item_Mapping 与 1..* Item(映射主体)。属性(Table 33):`mapping_variable_name` 0..1 String(transformation_rule 中的变量名,如 'day'/'month'/'year') |
| Object_Mapping 关联类 | 11.4.2 | 绑定 0..* Item_Mapping 与 1..* Item(映射客体)。属性(Table 34):`mapping_variable_name` 0..1 String |
| mapping_hierarchy 关联 | 11.5.1 | 自反关联:0..1 个 superset Item_Mapping 绑定 0..* 个 subset Item_Mapping(模型映射的层级) |
| Item_Mapping_Degree 枚举 | 11.6.1 | Table 35 共 14 值:`related to`、`same as`、`semantically equivalent`、`semantically similar`、`derived from`、`has derivation`、`broader than`、`narrower than`、`child of`、`parent of`、`contains`、`contained in`、`instance of`、`has instance`。标准明示"遵从实现可扩展该值列表" |

关键语义:Subject/Object 关联的双方都**只能是注册 Item**(经 Item.identifier 引用,见 Annex B 全部示例);关联类携带 `mapping_variable_name` 以支撑转换规则变量绑定。

## 2. 代码现状(item-mapping 模块)

| 类/表 | 属性 | 与标准的偏差 |
| --- | --- | --- |
| `ItemMapping`(item_mapping) | mappingType、description、transformationRule、mappingScheme、createdAt | **未继承 Item**(独立 @Entity,不可被标识/注册/管理);`mappingType` 为自创 4 值枚举;多出 `mapping_scheme_id`(MappingScheme 分组,扩展)、`createdAt`;缺 `transformation_rule_notation` |
| `ItemMappingSubject`(item_mapping_subject) | itemId XOR externalUri | 主体**允许外部 URI**(标准只关联注册 Item);无 `mapping_variable_name`;非关联类形态(独立实体) |
| `ItemMappingObject`(item_mapping_object) | itemId XOR externalUri | 同上(客体侧) |
| `MappingType` 枚举 | EQUIVALENT / DERIVED / AGGREGATED / SUBDIVIDED | 与标准 14 值不对应。近似映射:EQUIVALENT≈`semantically equivalent`;DERIVED≈`derived from`;AGGREGATED/SUBDIVIDED 近似 `contains`/`contained in` 但语义(聚合/细分 vs 包含)不完全重合。自创互斥规则(EQUIVALENT 与其他互斥、DERIVED 与 AGGREGATED 互斥等)标准无此约束 |
| (缺失) | — | 无 `mapping_hierarchy`(superset/subset 映射层级) |

## 3. 结论

现行实现是"简化映射模型 + 外部资源 URI 扩展 + 映射方案(MappingScheme)分组",
**不是** ISO/IEC 11179-3:2023 第 11 章元模型的实现——与 2.5 节 ClassificationScheme
的情况同型。主要结构性差距:

1. Item_Mapping 未作为 Item 子类建模(无法经统一条目机制标识/注册/命名);
2. Subject/Object 未按关联类建模,且缺 `mapping_variable_name`;
3. degree 枚举与标准 14 值不对齐;
4. 缺 `mapping_hierarchy` 关联;
5. 外部 URI 引用与 MappingScheme 分组为标准外扩展(后者有其自身价值)。

## 4. 归属建议(供用户决策)

**建议归 EXT**,与 2.5 同处理:

- 现行三表及 MappingType/MappingScheme 作为扩展功能整体归入 EXT schema(阶段 3);
- javadoc 显式声明妥协:未按第 11 章元模型重建,标准 Item_Mapping 模型重建
  (Item 子类化、Subject/Object 关联类、mapping_variable_name、标准 degree 枚举、
  mapping_hierarchy)**另立 change** 处理,与第 10 章 Classification 重建一并规划;
- 标准 4.4.2 的 profile 划分("Basic Registry" 可不含第 11 章)允许注册表在不含
  Item_Mapping 包时仍声称 Basic Registry 遵从,故归 EXT 不妨碍 MDR 侧的标准遵从声明。

替代方案(不推荐):按第 11 章立即重建——工作量大且与联邦合并、映射订阅等
既有扩展功能耦合深,宜与第 10 章重建统一立项。

**决策(用户,2026-09-01):归 MDR 并立项重建。**

用户裁定:item-mapping 是 ISO/IEC 11179-3:2023 标准内容,应作为标准模型
对待。(用户表述为"第 10 章";经 Milvus 标准原文核实,Item_Mapping 为
**第 11 章 Item Mapping package**,第 10 章为 Classification package——
用户意图明确为"标准里有,归标准侧",条款号以原文为准。)

落实口径:

- 阶段 3(`mdr-ext-schema-split`)中 item-mapping 三表**留在 MDR schema**,
  不随扩展剥离;
- 按第 11 章元模型的符合性重建(Item 子类化、Subject/Object 关联类、
  mapping_variable_name、标准 14 值 degree 枚举、mapping_hierarchy、
  transformation_rule_notation)**另立 change**,与第 10 章 Classification
  重建一并规划;
- 注册表遵从声明目标由 "Basic Registry" 调整为 "Basic Registry with mapping"
  (4.4.2:含第 11 章的 profile)。
