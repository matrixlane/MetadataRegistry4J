## ADDED Requirements

### Requirement: 数据元支持精度属性

数据元 SHALL 具有可选的精度（precision）属性，表示其关联数据元值允许的小数位数（ISO/IEC 11179-31:2023 §7.6.2.1.4 Table 23，基数 0..1，类型 Integer，类型定义见 ISO/IEC 11179-3:2023, 6.2.5）。该属性 SHALL 在实体、创建/更新请求与响应 DTO 之间双向贯通。

#### Scenario: 创建数据元时设置精度
- **WHEN** 创建数据元的请求携带 `precision=2`
- **THEN** 持久化后的数据元 `precision` 为 2
- **AND** 响应体的 `precision` 字段为 2

#### Scenario: 精度为可空属性
- **WHEN** 创建数据元的请求不携带 `precision`
- **THEN** 数据元创建成功且 `precision` 为空

#### Scenario: 更新为 null 清除精度
- **WHEN** 已有 `precision=4` 的数据元收到 `precision=null` 的更新请求
- **THEN** 该数据元的 `precision` 被清除为空
- **AND** 响应体的 `precision` 字段为空

### Requirement: 精度属性的界面编辑

数据元编辑对话框的属性区 SHALL 提供「精度」数字输入项，最小值为 0。

#### Scenario: 编辑精度
- **WHEN** 打开数据元的新建/编辑对话框
- **THEN** 属性区显示「精度」数字输入框，占位提示「关联数据元值允许的小数位数」
- **AND** 输入值不允许小于 0
