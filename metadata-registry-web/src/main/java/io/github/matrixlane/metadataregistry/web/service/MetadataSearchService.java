package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.AdministeredItemResponse;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service providing unified search across all metadata entity types.
 *
 * <p>Uses native SQL via JdbcTemplate to bypass Hibernate's polymorphic JOIN
 * which causes SQL Server's 4096-column limit to be exceeded when
 * querying AdministeredItem → IdentifiedItem → Item (JOINED inheritance
 * with 11+ subclasses).
 *
 * <p>smart-item-picker 扩展（D5 决议）：支持 namespaceId 限定、精确
 * identifier 匹配（NS.ID / 当前 namespace identifier）、itemType 推导
 * （JOINED 继承无 dtype 列，用 EXISTS 子查询拼 CASE 表达）。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MetadataSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseEnvironment env;

    /** 表名按当前方言/schema 限定(EXT 表自动走 ext schema,如 classification_scheme)。 */
    private String t(String table) {
        return env.qualifyAuto(table);
    }

    /** "取第一行"标量子查询：SQL Server 用 TOP 1，其余方言用 LIMIT 1。 */
    private String firstOne(String column, String table, String condition) {
        if (env.vendor() == DatabaseVendor.SQLSERVER) {
            return "(SELECT TOP 1 " + column + " FROM " + table + " WHERE " + condition + ")";
        }
        return "(SELECT " + column + " FROM " + table + " WHERE " + condition + " LIMIT 1)";
    }

    /** 分页子句与参数顺序因方言而异。 */
    private String pagingClause() {
        return env.vendor() == DatabaseVendor.SQLSERVER
                ? " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"
                : " LIMIT ? OFFSET ?";
    }

    /**
     * itemType 推导 CASE 表达式：JOINED 继承无 discriminator 列；用 EXISTS 子查询
     * 探测具体子类表，按从派生最深到最浅的顺序短路（先 DataElement，再 DataElementConcept…）。
     */
    /**
     * SELECT 列表的 item_type 表达式。
     * <p>
     * 带类型筛选时结果集已被 WHERE 的 EXISTS 限定为这些类型，无需对每行全量探测 21 张表：
     * 单值直接返回常量，多值只探测传入的类型子集；不带筛选时才退回全量 CASE 探测。
     * 类型名经 {@link #typeToTable} 白名单校验，未知类型不会拼进 SQL。
     */
    private String itemTypeExprFor(String typeFilter) {
        if (typeFilter == null) {
            return itemTypeCaseExpr();
        }
        List<String> known = Arrays.stream(typeFilter.split(","))
                .map(String::trim)
                .filter(s -> typeToTable(s) != null)
                .toList();
        if (known.isEmpty()) {
            // 未知类型：WHERE 已用 1=0 保证无命中，表达式形态无关紧要
            return itemTypeCaseExpr();
        }
        if (known.size() == 1) {
            return "'" + known.get(0) + "'";
        }
        StringBuilder sb = new StringBuilder("CASE ");
        int alias = 0;
        for (String type : known) {
            sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t(typeToTable(type)))
                    .append(" sub").append(alias)
                    .append(" WHERE sub").append(alias).append(".id = i.id) THEN '")
                    .append(type).append("' ");
            alias++;
        }
        sb.append("ELSE NULL END");
        return sb.toString();
    }

    private String itemTypeCaseExpr() {
        StringBuilder sb = new StringBuilder("CASE ");
        // 注册类（Owner 类型）
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("namespace")).append(" n WHERE n.id = i.id) THEN 'Namespace' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("registry_specification")).append(" rs WHERE rs.id = i.id) THEN 'RegistrySpecification' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("constraint_set")).append(" cs WHERE cs.id = i.id) THEN 'ConstraintSet' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("reference_document")).append(" rd WHERE rd.id = i.id) THEN 'ReferenceDocument' ");
        // 数据规范类
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("data_element")).append(" de WHERE de.id = i.id) THEN 'DataElement' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("data_element_concept")).append(" dec WHERE dec.id = i.id) THEN 'DataElementConcept' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("conceptual_domain")).append(" cd WHERE cd.id = i.id) THEN 'ConceptualDomain' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("value_domain")).append(" vd WHERE vd.id = i.id) THEN 'ValueDomain' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("object_class")).append(" oc WHERE oc.id = i.id) THEN 'ObjectClass' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("property")).append(" p WHERE p.id = i.id) THEN 'Property' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("data_type")).append(" dt WHERE dt.id = i.id) THEN 'DataType' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("data_type_scheme")).append(" dts WHERE dts.id = i.id) THEN 'DataTypeScheme' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("unit_of_measure")).append(" uom WHERE uom.id = i.id) THEN 'UnitOfMeasure' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("measure_class")).append(" mc WHERE mc.id = i.id) THEN 'MeasureClass' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("dimensionality")).append(" dim WHERE dim.id = i.id) THEN 'Dimensionality' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("classification_scheme")).append(" cs2 WHERE cs2.id = i.id) THEN 'ClassificationScheme' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("context")).append(" ctx WHERE ctx.id = i.id) THEN 'Context' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("organization")).append(" o WHERE o.id = i.id) THEN 'Organization' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("individual")).append(" ind WHERE ind.id = i.id) THEN 'Individual' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("contact")).append(" c WHERE c.id = i.id) THEN 'Contact' ");
        sb.append("WHEN EXISTS (SELECT 1 FROM ").append(t("document_type")).append(" doc WHERE doc.id = i.id) THEN 'DocumentType' ");
        sb.append("ELSE NULL END");
        return sb.toString();
    }

    /**
     * Unified search across all metadata entities.
     *
     * @param keyword            模糊匹配 designation / definition / scoped_identifier
     * @param type               实体类型筛选（如 "DataElement"，多选由调用方以英文逗号分隔拼好后传入）
     * @param status             注册状态
     * @param contextId          上下文筛选
     * @param acceptabilities    可接受性筛选
     * @param namespaceId        限定命名空间（smart-item-picker 范围限定）
     * @param exactMatch         true 时切换主路径为精确匹配（D5）
     * @param identifier         精确匹配路径的标识符
     * @param namespaceShorthand 精确匹配路径的命名空间简写前缀
     * @param pageable           分页
     */
    public Page<AdministeredItemResponse> search(String keyword, String type,
                                                  RegistrationStatus status,
                                                  UUID contextId,
                                                  List<Acceptability> acceptabilities,
                                                  UUID namespaceId,
                                                  boolean exactMatch,
                                                  String identifier,
                                                  String namespaceShorthand,
                                                  Pageable pageable) {
        String keywordPattern = (keyword != null && !keyword.isBlank())
                ? "%" + keyword.trim().toLowerCase() + "%"
                : null;
        String typeFilter = (type != null && !type.isBlank()) ? type : null;
        String statusFilter = (status != null) ? status.name() : null;
        String identifierExact = (exactMatch && identifier != null && !identifier.isBlank())
                ? identifier.trim() : null;
        String namespaceShorthandExact = (exactMatch && namespaceShorthand != null && !namespaceShorthand.isBlank())
                ? namespaceShorthand.trim() : null;
        boolean prefixMatch = exactMatch && identifierExact != null && !identifierExact.contains(" ");

        // Build WHERE clause dynamically
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> countParams = new ArrayList<>();
        List<Object> selectParams = new ArrayList<>();

        // 精确匹配模式：D5 决议 - 主路径走 (identifier, namespace) 精确查 + 前缀回退
        if (identifierExact != null) {
            where.append(" AND LOWER(si.identifier) = LOWER(?)");
            countParams.add(identifierExact);
            selectParams.add(identifierExact);
            if (namespaceShorthandExact != null) {
                where.append(" AND LOWER(ns.shorthand_prefix) = LOWER(?)");
                countParams.add(namespaceShorthandExact);
                selectParams.add(namespaceShorthandExact);
            }
        }

        // 模糊匹配模式：仅在精确匹配未设置时启用，避免双轨混合
        if (keywordPattern != null && identifierExact == null) {
            where.append(" AND (EXISTS (SELECT 1 FROM " + t("designation") + " d WHERE d.item_id = i.id AND LOWER(d.value) LIKE ?)");
            where.append(" OR EXISTS (SELECT 1 FROM " + t("definition") + " df WHERE df.item_id = i.id AND LOWER(df.value) LIKE ?)");
            where.append(" OR EXISTS (SELECT 1 FROM " + t("scoped_identifier") + " si2 WHERE si2.identified_item_id = ai.id AND LOWER(si2.identifier) LIKE ?))");
            countParams.add(keywordPattern);
            countParams.add(keywordPattern);
            countParams.add(keywordPattern);
            selectParams.add(keywordPattern);
            selectParams.add(keywordPattern);
            selectParams.add(keywordPattern);
        }

        // 类型筛选（D5 决议）：itemType 由 CASE 表达式推导，故 SQL 用 EXISTS 多类型短路
        // （调用方传入的 type 支持英文逗号分隔多值；本期单值实现，扩展预留）
        // 注意：itemTypeExistsClause 生成的 EXISTS 子查询无 "?" 占位符，不得向参数列表加参数
        if (typeFilter != null) {
            String[] types = typeFilter.split(",");
            if (types.length == 1) {
                where.append(" AND " + itemTypeExistsClause(types[0].trim()));
            } else {
                StringBuilder or = new StringBuilder(" AND (");
                for (int i = 0; i < types.length; i++) {
                    if (i > 0) or.append(" OR ");
                    or.append(itemTypeExistsClause(types[i].trim()));
                }
                or.append(")");
                where.append(or);
            }
        }

        if (statusFilter != null) {
            where.append(" AND rs.registration_status = ?");
            countParams.add(statusFilter);
            selectParams.add(statusFilter);
        }

        if (contextId != null) {
            String contextFilter = " AND (EXISTS (SELECT 1 FROM " + t("designation") + " d_ctx " +
                    "JOIN " + t("designation_context_relation") + " dc ON dc.designation_id = d_ctx.id " +
                    "WHERE d_ctx.item_id = i.id AND dc.context_id = ?)" +
                    " OR EXISTS (SELECT 1 FROM " + t("definition") + " df_ctx " +
                    "JOIN " + t("definition_context_relation") + " dfc ON dfc.definition_id = df_ctx.id " +
                    "WHERE df_ctx.item_id = i.id AND dfc.context_id = ?))";
            where.append(contextFilter);
            countParams.add(contextId);
            countParams.add(contextId);
            selectParams.add(contextId);
            selectParams.add(contextId);
        }

        if (acceptabilities != null && !acceptabilities.isEmpty()) {
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < acceptabilities.size(); i++) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?");
            }
            String acceptabilityFilter = " AND (EXISTS (SELECT 1 FROM " + t("designation") + " d_acc " +
                    "WHERE d_acc.item_id = i.id AND d_acc.acceptability IN (" + placeholders + "))" +
                    " OR EXISTS (SELECT 1 FROM " + t("definition") + " df_acc " +
                    "WHERE df_acc.item_id = i.id AND df_acc.acceptability IN (" + placeholders + ")))";
            where.append(acceptabilityFilter);
            for (Acceptability acc : acceptabilities) {
                countParams.add(acc.name());
                selectParams.add(acc.name());
            }
            for (Acceptability acc : acceptabilities) {
                countParams.add(acc.name());
                selectParams.add(acc.name());
            }
        }

        // namespaceId 限定（D1 决议）：administered item 的命名空间由 scoped_identifier 承载，
        // administered_item/registered_item 表均无 namespace_id 列
        if (namespaceId != null) {
            where.append(" AND ns.id = ? ");
            countParams.add(namespaceId);
            selectParams.add(namespaceId);
        }

        // Count query
        String countSql = "SELECT COUNT(DISTINCT ai.id) FROM " + t("administered_item") + " ai " +
                "JOIN " + t("identified_item") + " ii ON ai.id = ii.id " +
                "JOIN " + t("item") + " i ON ii.item_id = i.id " +
                "LEFT JOIN " + t("registration_state") + " rs ON ai.registration_state_id = rs.id " +
                "LEFT JOIN " + t("scoped_identifier") + " si ON si.identified_item_id = ai.id " +
                "LEFT JOIN " + t("namespace") + " ns ON si.namespace_id = ns.id" +
                where;

        Long total = jdbcTemplate.queryForObject(countSql, Long.class, countParams.toArray());
        if (total == null) total = 0L;

        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        String hasPreferred = "EXISTS (SELECT 1 FROM " + t("designation") + " d_pref " +
                "WHERE d_pref.item_id = i.id AND d_pref.acceptability = 'PREFERRED')" +
                " OR EXISTS (SELECT 1 FROM " + t("definition") + " df_pref " +
                "WHERE df_pref.item_id = i.id AND df_pref.acceptability = 'PREFERRED')";

        String itemTypeExpr = itemTypeExprFor(typeFilter);
        String matchTypeLiteral = exactMatch ? "'EXACT'" : "'PARTIAL'";

        // 排序：精确匹配时按匹配度（精确 > 前缀 > 模糊）；其它按创建时间倒序
        String orderBy;
        if (prefixMatch && identifierExact != null) {
            String prefixPattern = identifierExact.toLowerCase() + "%";
            orderBy = " ORDER BY CASE WHEN LOWER(si.identifier) = LOWER(?) THEN 0" +
                    " WHEN LOWER(si.identifier) LIKE ? THEN 1 ELSE 2 END, ai.creation_date_time DESC";
            selectParams.add(identifierExact);
            selectParams.add(prefixPattern);
        } else {
            orderBy = " ORDER BY ai.creation_date_time DESC";
        }

        // Main query with pagination
        String selectSql = "SELECT DISTINCT " +
                "ai.id, " +
                "i.id AS item_id, " +
                itemTypeExpr + " AS item_type, " +
                firstOne("d.value", t("designation") + " d", "d.item_id = i.id") + " AS item_label, " +
                "si.identifier, " +
                "si.version, " +
                "ns.id AS ns_id, " +
                firstOne("d2.value", t("designation") + " d2", "d2.item_id = ns.id") + " AS ns_label, " +
                "ra.id AS ra_id, " +
                firstOne("oname.value", t("organization_name") + " oname", "oname.organization_id = ra.id") + " AS ra_label, " +
                "rs.registration_status, " +
                "rs.effective_date, " +
                "rs.administrative_note, " +
                "ri.constraint_set_id AS cs_id, " +
                "(" + hasPreferred + ") AS has_preferred, " +
                "ai.creation_date_time, " +
                "ai.last_change_date_time, " +
                "ai.change_description, " +
                matchTypeLiteral + " AS match_type " +
                "FROM " + t("administered_item") + " ai " +
                "JOIN " + t("identified_item") + " ii ON ai.id = ii.id " +
                "JOIN " + t("item") + " i ON ii.item_id = i.id " +
                "LEFT JOIN " + t("scoped_identifier") + " si ON si.identified_item_id = ai.id " +
                "LEFT JOIN " + t("namespace") + " ns ON si.namespace_id = ns.id " +
                "LEFT JOIN " + t("registration_authority") + " ra ON ai.registration_authority_id = ra.id " +
                "LEFT JOIN " + t("registration_state") + " rs ON ai.registration_state_id = rs.id " +
                // constraint_set_id 位于继承链中间表 registered_item（JOINED 继承），不在 administered_item
                "LEFT JOIN " + t("registered_item") + " ri ON ri.id = ai.id " +
                where +
                orderBy +
                pagingClause();

        if (env.vendor() == DatabaseVendor.SQLSERVER) {
            selectParams.add(pageable.getOffset());
            selectParams.add(pageable.getPageSize());
        } else {
            selectParams.add(pageable.getPageSize());
            selectParams.add(pageable.getOffset());
        }

        List<AdministeredItemResponse> results = jdbcTemplate.query(selectSql, this::mapRow, selectParams.toArray());

        return new PageImpl<>(results, pageable, total);
    }

    /**
     * 单个 itemType 是否存在的 SQL 片段（EXISTS 子查询）。
     * 仅支持已通过 enum/白名单校验的 type；调用方在 controller 做白名单校验。
     */
    private String itemTypeExistsClause(String type) {
        String tableName = typeToTable(type);
        if (tableName == null) {
            // 未知类型：不命中；返回 1=0 谓词
            return "1=0";
        }
        return "EXISTS (SELECT 1 FROM " + t(tableName) + " sub WHERE sub.id = i.id)";
    }

    /**
     * 类型名 → 实体表名映射。
     */
    private String typeToTable(String type) {
        if (type == null) return null;
        return switch (type) {
            case "DataElement" -> "data_element";
            case "DataElementConcept" -> "data_element_concept";
            case "ConceptualDomain" -> "conceptual_domain";
            case "ValueDomain" -> "value_domain";
            case "ObjectClass" -> "object_class";
            case "Property" -> "property";
            case "DataType" -> "data_type";
            case "DataTypeScheme" -> "data_type_scheme";
            case "UnitOfMeasure" -> "unit_of_measure";
            case "MeasureClass" -> "measure_class";
            case "Dimensionality" -> "dimensionality";
            case "Namespace" -> "namespace";
            case "RegistrySpecification" -> "registry_specification";
            case "ConstraintSet" -> "constraint_set";
            case "ReferenceDocument" -> "reference_document";
            case "ClassificationScheme" -> "classification_scheme";
            case "Context" -> "context";
            case "Organization" -> "organization";
            case "Individual" -> "individual";
            case "Contact" -> "contact";
            case "DocumentType" -> "document_type";
            default -> null;
        };
    }

    private AdministeredItemResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AdministeredItemResponse(
                uuid(rs, "id"),
                uuid(rs, "item_id"),
                rs.getString("item_type"),
                rs.getString("item_label"),
                rs.getString("identifier"),
                rs.getString("version"),
                uuid(rs, "ns_id"),
                rs.getString("ns_label"),
                uuid(rs, "ra_id"),
                rs.getString("ra_label"),
                status(rs, "registration_status"),
                ldt(rs, "effective_date"),
                rs.getString("administrative_note"),
                uuid(rs, "cs_id"),
                null,
                List.of(),
                rs.getBoolean("has_preferred"),
                ldt(rs, "creation_date_time"),
                ldt(rs, "last_change_date_time"),
                rs.getString("change_description"),
                rs.getString("match_type")
        );
    }

    private UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return DatabaseEnvironment.toUuid(value);
    }

    private RegistrationStatus status(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : RegistrationStatus.valueOf(value);
    }

    private LocalDateTime ldt(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }
}