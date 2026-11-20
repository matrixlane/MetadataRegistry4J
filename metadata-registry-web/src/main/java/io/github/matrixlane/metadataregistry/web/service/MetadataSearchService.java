package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.SubmissionRecord;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.AdministeredItemResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.SubmissionRecordSummary;
import io.github.matrixlane.metadataregistry.web.repository.SubmissionRecordRepository;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import io.github.matrixlane.metadataregistry.web.service.database.ItemTypeSql;
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
import java.util.Map;
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
    private final SubmissionRecordRepository submissionRecordRepository;

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
     * 类型名经 {@link ItemTypeSql#typeToTable} 白名单校验，未知类型不会拼进 SQL。
     */
    private String itemTypeExprFor(String typeFilter) {
        if (typeFilter == null) {
            return ItemTypeSql.itemTypeCaseExpr(env);
        }
        List<String> known = Arrays.stream(typeFilter.split(","))
                .map(String::trim)
                .filter(s -> ItemTypeSql.typeToTable(s) != null)
                .toList();
        // 未知类型：WHERE 已用 1=0 保证无命中，表达式形态无关紧要
        return ItemTypeSql.itemTypeExprFor(env, known);
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
            where.append(" OR EXISTS (SELECT 1 FROM " + t("scoped_identifier") + " si2 WHERE si2.identified_item_id = ai.id AND LOWER(si2.identifier) LIKE ?)");
            countParams.add(keywordPattern);
            countParams.add(keywordPattern);
            countParams.add(keywordPattern);
            selectParams.add(keywordPattern);
            selectParams.add(keywordPattern);
            selectParams.add(keywordPattern);
            // smart-item-picker 回填（fetchById）以 item UUID 为关键字：上述三类文本字段
            // 均不含 item 主键值，须追加 i.id 等值匹配，否则已选关联项反查不到、无法回显
            UUID keywordUuid = parseUuidOrNull(keyword);
            if (keywordUuid != null) {
                where.append(" OR i.id = ?");
                countParams.add(env.toJdbcUuid(keywordUuid));
                selectParams.add(env.toJdbcUuid(keywordUuid));
            }
            where.append(")");
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
                    "JOIN " + t("designation_context") + " dc ON dc.designation_id = d_ctx.id " +
                    "WHERE d_ctx.item_id = i.id AND dc.context_id = ?)" +
                    " OR EXISTS (SELECT 1 FROM " + t("definition") + " df_ctx " +
                    "JOIN " + t("definition_context") + " dfc ON dfc.definition_id = df_ctx.id " +
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
            // 可接受性仅存在于上下文关系（ISO/IEC 11179-3:2023 8.5），经 Designation_Context/
            // Definition_Context 过滤（mdr-standard-compliance-cleanup 后主表无 acceptability 列）。
            // AcceptabilityConverter 落库为小写（ISO 表 20），此处统一 lower 比对——
            // SQLite/达梦默认大小写敏感，大写字面量会恒不匹配。
            String acceptabilityFilter = " AND (EXISTS (SELECT 1 FROM " + t("designation") + " d_acc " +
                    "JOIN " + t("designation_context") + " dc_acc ON dc_acc.designation_id = d_acc.id " +
                    "WHERE d_acc.item_id = i.id AND LOWER(dc_acc.acceptability) IN (" + placeholders + "))" +
                    " OR EXISTS (SELECT 1 FROM " + t("definition") + " df_acc " +
                    "JOIN " + t("definition_context") + " dfc_acc ON dfc_acc.definition_id = df_acc.id " +
                    "WHERE df_acc.item_id = i.id AND LOWER(dfc_acc.acceptability) IN (" + placeholders + ")))";
            where.append(acceptabilityFilter);
            for (Acceptability acc : acceptabilities) {
                countParams.add(acc.name().toLowerCase(java.util.Locale.ROOT));
                selectParams.add(acc.name().toLowerCase(java.util.Locale.ROOT));
            }
            for (Acceptability acc : acceptabilities) {
                countParams.add(acc.name().toLowerCase(java.util.Locale.ROOT));
                selectParams.add(acc.name().toLowerCase(java.util.Locale.ROOT));
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
                "JOIN " + t("registered_item") + " ii ON ai.id = ii.id " +
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

        // EXISTS 不能直接作 SELECT 列表标量表达式(SQL Server 报 156),包一层 CASE WHEN
        String hasPreferred = hasPreferredExpr();
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
                "JOIN " + t("registered_item") + " ii ON ai.id = ii.id " +
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

    /** EXISTS 不能直接作 SELECT 列表标量表达式(SQL Server 报 156),包一层 CASE WHEN */
    private String hasPreferredExpr() {
        return "CASE WHEN EXISTS (SELECT 1 FROM " + t("designation") + " d_pref " +
                "JOIN " + t("designation_context") + " dc_pref ON dc_pref.designation_id = d_pref.id " +
                "WHERE d_pref.item_id = i.id AND LOWER(dc_pref.acceptability) = 'preferred')" +
                " OR EXISTS (SELECT 1 FROM " + t("definition") + " df_pref " +
                "JOIN " + t("definition_context") + " dfc_pref ON dfc_pref.definition_id = df_pref.id " +
                "WHERE df_pref.item_id = i.id AND LOWER(dfc_pref.acceptability) = 'preferred') THEN 1 ELSE 0 END";
    }

    /**
     * 单条条目详情（get-item-detail MCP）：与列表同一标量 SQL 路径，额外填充
     * constraintSetLabel / referenceDocumentIds / submissionRecordSummaries。
     *
     * <p><b>为什么不能用 {@code AdministeredItemService#get} 的实体路径</b>：
     * toResponse 初始化 Item 继承树代理会把 30+ 张子表连同 EAGER 关联拉进同一条 SQL，
     * SQLite 触发 "at most 64 tables in a join"（KNOWN_FAILURES
     * failure-2026-09-21-sqlite-64-table-join-limit），列表路径 2026-10 已改标量，
     * 详情路径当时漏网。条目带引用文档/命名空间等关联后实体路径必炸。</p>
     *
     * @param id 受管项 id（browse/search 结果的 {@code id} 字段）或内容链 itemId
     *           （{@code itemId} 字段）均可；均无命中时抛 {@link ResourceNotFoundException}
     */
    @Transactional(readOnly = true)
    public AdministeredItemResponse findDetailByAnyId(UUID id) {
        String itemTypeExpr = ItemTypeSql.itemTypeCaseExpr(env);
        String sql = "SELECT DISTINCT " +
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
                firstOne("dcs.value", t("designation") + " dcs JOIN " + t("constraint_set") + " cs ON dcs.item_id = cs.id",
                        "cs.id = ri.constraint_set_id") + " AS cs_label, " +
                "(" + hasPreferredExpr() + ") AS has_preferred, " +
                "ai.creation_date_time, " +
                "ai.last_change_date_time, " +
                "ai.change_description " +
                "FROM " + t("administered_item") + " ai " +
                "JOIN " + t("registered_item") + " ii ON ai.id = ii.id " +
                "JOIN " + t("item") + " i ON ii.item_id = i.id " +
                "LEFT JOIN " + t("scoped_identifier") + " si ON si.identified_item_id = ai.id " +
                "LEFT JOIN " + t("namespace") + " ns ON si.namespace_id = ns.id " +
                "LEFT JOIN " + t("registration_authority") + " ra ON ai.registration_authority_id = ra.id " +
                "LEFT JOIN " + t("registration_state") + " rs ON ai.registration_state_id = rs.id " +
                // constraint_set_id 位于继承链中间表 registered_item（JOINED 继承），不在 administered_item
                "LEFT JOIN " + t("registered_item") + " ri ON ri.id = ai.id " +
                "WHERE ai.id = ? OR i.id = ?";
        Object uuidParam = env.toJdbcUuid(id);
        List<AdministeredItemResponse> rows = jdbcTemplate.query(sql, (rs, rowNum) -> mapDetailRow(rs), uuidParam, uuidParam);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("AdministeredItem", id);
        }
        AdministeredItemResponse first = rows.get(0);
        List<UUID> referenceDocumentIds = jdbcTemplate.query(
                "SELECT reference_document_id FROM " + t("registered_item_reference_document")
                        + " WHERE registered_item_id = ?",
                (rs, rowNum) -> DatabaseEnvironment.toUuid(rs.getObject("reference_document_id")),
                env.toJdbcUuid(first.id()));
        List<SubmissionRecord> srs =
                submissionRecordRepository.findAllByRegisteredItemIdOrderByIdDesc(first.id());
        Map<UUID, Long> itemCountBySubmission = srs.isEmpty()
                ? Map.of()
                : submissionRecordRepository.countRegisteredItemsBySubmissionRecordIds(
                        srs.stream().map(SubmissionRecord::getId).toList());
        List<SubmissionRecordSummary> submissionRecordSummaries = srs.stream()
                .map(sr -> SubmissionRecordMapper.toSummary(sr, itemCountBySubmission.getOrDefault(sr.getId(), 0L)))
                .toList();
        return new AdministeredItemResponse(
                first.id(),
                first.itemId(),
                first.itemType(),
                first.itemLabel(),
                first.identifier(),
                first.version(),
                first.namespaceId(),
                first.namespaceLabel(),
                first.registrationAuthorityId(),
                first.registrationAuthorityLabel(),
                first.registrationStatus(),
                first.stateEffectiveDate(),
                first.administrativeNote(),
                first.constraintSetId(),
                first.constraintSetLabel(),
                referenceDocumentIds,
                first.hasPreferred(),
                first.creationDateTime(),
                first.lastChangeDateTime(),
                first.changeDescription(),
                null,
                submissionRecordSummaries);
    }

    /** 关键字可解析为 UUID 时返回之（首尾空白容忍），否则返回 null。 */
    private static UUID parseUuidOrNull(String keyword) {
        if (keyword == null) {
            return null;
        }
        try {
            return UUID.fromString(keyword.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 单个 itemType 是否存在的 SQL 片段（EXISTS 子查询）。
     * 仅支持已通过 enum/白名单校验的 type；调用方在 controller 做白名单校验。
     */
    private String itemTypeExistsClause(String type) {
        return ItemTypeSql.itemTypeExistsClause(env, type);
    }

    /**
     * 类型名 → 实体表名映射。
     */
    private String typeToTable(String type) {
        return ItemTypeSql.typeToTable(type);
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
                rs.getString("match_type"),
                null
        );
    }

    /** 详情行映射：字段集与 mapRow 相同，但读 cs_label，且不读列表专用的 match_type 列。 */
    private AdministeredItemResponse mapDetailRow(ResultSet rs) throws SQLException {
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
                rs.getString("cs_label"),
                List.of(),
                rs.getBoolean("has_preferred"),
                ldt(rs, "creation_date_time"),
                ldt(rs, "last_change_date_time"),
                rs.getString("change_description"),
                null,
                null
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