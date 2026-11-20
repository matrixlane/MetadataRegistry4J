package io.github.matrixlane.metadataregistry.web.service.database;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Item JOINED 继承树的 itemType 推导/筛选原生 SQL 片段。
 *
 * <p>Item 元模型为 JOINED 继承且无 discriminator 列，JPA 多态加载会拼出远超
 * SQLite 64 表 JOIN 上限的 SQL；因此所有需要"条目具体类型"的场景（检索、导出）
 * 统一改用 EXISTS 子查询探测各子类表。本类集中类型名白名单与 SQL 片段生成，
 * 避免各服务各自维护一份白名单而漂移。
 */
public final class ItemTypeSql {

    private ItemTypeSql() {
    }

    /**
     * 类型名 → 实体表名映射（白名单，单一事实源）；未知类型返回 null，调用方不得把 null 拼进 SQL。
     */
    private static final Map<String, String> TYPE_TO_TABLE;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("DataElement", "data_element");
        m.put("DataElementConcept", "data_element_concept");
        m.put("ConceptualDomain", "conceptual_domain");
        m.put("ValueDomain", "value_domain");
        m.put("ObjectClass", "object_class");
        m.put("Property", "property");
        m.put("DataType", "data_type");
        m.put("DataTypeScheme", "data_type_scheme");
        m.put("UnitOfMeasure", "unit_of_measure");
        m.put("MeasureClass", "measure_class");
        m.put("Dimensionality", "dimensionality");
        m.put("Namespace", "namespace");
        m.put("ConstraintSet", "constraint_set");
        m.put("ReferenceDocument", "reference_document");
        m.put("ClassificationScheme", "classification_scheme");
        m.put("Context", "context");
        m.put("Organization", "organization");
        m.put("Individual", "individual");
        m.put("Contact", "contact");
        m.put("DocumentType", "document_type");
        TYPE_TO_TABLE = Collections.unmodifiableMap(m);
    }

    public static String typeToTable(String type) {
        if (type == null) return null;
        return TYPE_TO_TABLE.get(type);
    }

    /**
     * 大小写不敏感地把外部输入归一化为白名单内的规范类型名；未知返回 null。
     */
    public static String normalizeType(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        for (String known : TYPE_TO_TABLE.keySet()) {
            if (known.equalsIgnoreCase(trimmed)) {
                return known;
            }
        }
        return null;
    }

    /**
     * SELECT 列表的 item_type 表达式。
     *
     * <p>带类型筛选时结果集已被 WHERE 的 EXISTS 限定为这些类型：单值直接返回常量，
     * 多值只探测传入的类型子集；不带筛选时退回全量 CASE 探测（按业务类型分组、
     * 同一组内顺序无关——各子类表互斥）。
     */
    public static String itemTypeExprFor(DatabaseEnvironment env, List<String> canonicalTypes) {
        if (canonicalTypes == null || canonicalTypes.isEmpty()) {
            return itemTypeCaseExpr(env);
        }
        if (canonicalTypes.size() == 1) {
            return "'" + canonicalTypes.get(0) + "'";
        }
        StringBuilder sb = new StringBuilder("CASE ");
        int alias = 0;
        for (String type : canonicalTypes) {
            sb.append("WHEN EXISTS (SELECT 1 FROM ").append(env.qualifyAuto(typeToTable(type)))
                    .append(" sub").append(alias)
                    .append(" WHERE sub").append(alias).append(".id = i.id) THEN '")
                    .append(type).append("' ");
            alias++;
        }
        sb.append("ELSE NULL END");
        return sb.toString();
    }

    /**
     * 全量 item_type 推导 CASE 表达式（21 个子类表逐一 EXISTS 探测）。
     */
    public static String itemTypeCaseExpr(DatabaseEnvironment env) {
        StringBuilder sb = new StringBuilder("CASE ");
        // 注册类（Owner 类型）
        sb.append(when(env, "namespace", "n", "Namespace"));
        sb.append(when(env, "constraint_set", "cs", "ConstraintSet"));
        sb.append(when(env, "reference_document", "rd", "ReferenceDocument"));
        // 数据规范类
        sb.append(when(env, "data_element", "de", "DataElement"));
        sb.append(when(env, "data_element_concept", "dec", "DataElementConcept"));
        sb.append(when(env, "conceptual_domain", "cd", "ConceptualDomain"));
        sb.append(when(env, "value_domain", "vd", "ValueDomain"));
        sb.append(when(env, "object_class", "oc", "ObjectClass"));
        sb.append(when(env, "property", "p", "Property"));
        sb.append(when(env, "data_type", "dt", "DataType"));
        sb.append(when(env, "data_type_scheme", "dts", "DataTypeScheme"));
        sb.append(when(env, "unit_of_measure", "uom", "UnitOfMeasure"));
        sb.append(when(env, "measure_class", "mc", "MeasureClass"));
        sb.append(when(env, "dimensionality", "dim", "Dimensionality"));
        sb.append(when(env, "classification_scheme", "cs2", "ClassificationScheme"));
        sb.append(when(env, "context", "ctx", "Context"));
        sb.append(when(env, "organization", "o", "Organization"));
        sb.append(when(env, "individual", "ind", "Individual"));
        sb.append(when(env, "contact", "c", "Contact"));
        sb.append(when(env, "document_type", "doc", "DocumentType"));
        sb.append("ELSE NULL END");
        return sb.toString();
    }

    /**
     * 单个 itemType 是否存在的 WHERE 片段（EXISTS 子查询）。
     * 仅支持已通过 {@link #typeToTable} 白名单校验的规范类型名。
     */
    public static String itemTypeExistsClause(DatabaseEnvironment env, String canonicalType) {
        String tableName = typeToTable(canonicalType);
        if (tableName == null) {
            // 未知类型：不命中；返回 1=0 谓词
            return "1=0";
        }
        return "EXISTS (SELECT 1 FROM " + env.qualifyAuto(tableName) + " sub WHERE sub.id = i.id)";
    }

    private static String when(DatabaseEnvironment env, String table, String alias, String type) {
        return "WHEN EXISTS (SELECT 1 FROM " + env.qualifyAuto(table) + " " + alias
                + " WHERE " + alias + ".id = i.id) THEN '" + type + "' ";
    }
}
