package io.github.matrixlane.metadataregistry.web.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.StewardshipRecord;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.SubmissionRecord;
import io.github.matrixlane.metadataregistry.web.dto.export.AdministeredItemExport;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportRequest;
import io.github.matrixlane.metadataregistry.web.dto.export.MetadataExportResult;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import io.github.matrixlane.metadataregistry.web.service.database.ItemTypeSql;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import org.hibernate.Hibernate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 注册元数据导出服务
 *
 * <p>支持按注册状态、组织、时间范围、Item 类型过滤，以及字段白名单筛选。
 * 支持 JSON 和 CSV 两种格式。
 *
 * <p>批量导出走原生 SQL 标量投影（{@link #queryExports}），不经 JPA 实体图：
 * Item 是 JOINED 继承根（30 子类表），加载它会拼出超 SQLite 64 表 JOIN 上限的
 * 多态 SQL（2026-11-20 生产事故：export-data-dictionary 在 SQLite 下 500）。
 * itemType 由 EXISTS 子查询推导，与 MetadataSearchService 共用 {@link ItemTypeSql}。
 */
@Service
@Transactional(readOnly = true)
public class MetadataExportService {

    private final AdministeredItemRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseEnvironment env;
    private final ObjectMapper objectMapper;

    public MetadataExportService(AdministeredItemRepository repository,
                                  JdbcTemplate jdbcTemplate,
                                  DatabaseEnvironment env,
                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.env = env;
        this.objectMapper = objectMapper;
    }

    private static final String SCHEMA_VERSION = "1.0.0";
    private static final String EXPORT_FILTER_NAME = "exportFilter";
    private static final int AUTO_ASYNC_THRESHOLD = 1_000;
    private static final int MAX_SYNC_EXPORT_SIZE = 10_000;

    /**
     * 导出注册元数据为 JSON 字符串（同步，小数据量）
     *
     * @param request 导出请求参数
     * @return JSON 字符串
     */
    public String exportToJson(MetadataExportRequest request) throws IOException {
        MetadataExportResult result = export(request);

        ObjectMapper mapper = createFilteredMapper(request.fields());
        return mapper.writeValueAsString(result);
    }

    /**
     * 导出注册元数据（同步）
     *
     * @param request 导出请求参数
     * @return 导出结果
     */
    public MetadataExportResult export(MetadataExportRequest request) {
        List<AdministeredItemExport> items = queryExports(request);

        if (items.size() > MAX_SYNC_EXPORT_SIZE) {
            throw new IllegalArgumentException(
                    "同步导出结果超过最大限制 " + MAX_SYNC_EXPORT_SIZE + " 条，请缩小过滤条件或使用异步导出。" +
                    "当前匹配: " + items.size());
        }

        return new MetadataExportResult(
                buildMetadata(request, items.size()),
                items
        );
    }

    /**
     * 判断是否应自动切换为异步导出
     */
    public boolean shouldUseAsyncExport(MetadataExportRequest request) {
        long estimated = estimateCount(request);
        return estimated > MAX_SYNC_EXPORT_SIZE;
    }

    /**
     * 估算匹配记录数（不加载全部数据）
     */
    public long estimateCount(MetadataExportRequest request) {
        Specification<AdministeredItem> spec = buildSpecification(request);
        return repository.count(spec);
    }



    /**
     * 流式导出 JSON 到输出流（用于同步大文件场景）
     *
     * @param request   导出请求参数
     * @param output    输出流
     */
    public void exportJsonToStream(MetadataExportRequest request, OutputStream output) throws IOException {
        List<AdministeredItemExport> items = queryExports(request);
        // 分页时 totalCount 报告全量命中数，客户端据其判定翻页终止；未分页维持现语义
        int totalCount = request.paged() ? countExports(request) : items.size();

        try (JsonGenerator generator = new JsonFactory().createGenerator(output, com.fasterxml.jackson.core.JsonEncoding.UTF8)) {
            ObjectMapper filteredMapper = createFilteredMapper(request.fields());

            generator.writeStartObject();

            MetadataExportResult.ExportMetadata metadata = buildMetadata(request, totalCount);
            generator.writeFieldName("exportMetadata");
            filteredMapper.writeValue(generator, metadata);

            generator.writeArrayFieldStart("items");
            for (AdministeredItemExport item : items) {
                filteredMapper.writeValue(generator, item);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        }
    }

    /**
     * 流式导出 CSV 到输出流
     *
     * @param request   导出请求参数
     * @param output    输出流
     */
    public void exportCsvToStream(MetadataExportRequest request, OutputStream output) throws IOException {
        List<AdministeredItemExport> items = queryExports(request);

        try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            // BOM for Excel compatibility
            writer.write('\ufeff');

            // Header
            writer.write("ID,ItemType,ItemLabel,ItemDefinition,Identifier,Version,RegistrationStatus,");
            writer.write("CreatedDate,LastModifiedDate,ChangeDescription,ExplanatoryComment\n");

            // Data rows
            for (AdministeredItemExport export : items) {
                writer.write(escapeCsv(export.id().toString()));
                writer.write(",");
                writer.write(escapeCsv(export.itemType()));
                writer.write(",");
                writer.write(escapeCsv(export.itemLabel()));
                writer.write(",");
                writer.write(escapeCsv(export.itemDefinition()));
                writer.write(",");
                String idStr = (export.identifiers() != null && !export.identifiers().isEmpty())
                        ? export.identifiers().get(0).identifier() : "";
                writer.write(escapeCsv(idStr));
                writer.write(",");
                String verStr = (export.identifiers() != null && !export.identifiers().isEmpty())
                        ? export.identifiers().get(0).version() : "";
                writer.write(escapeCsv(verStr));
                writer.write(",");
                String statusStr = export.registrationState() != null
                        ? export.registrationState().status().name() : "";
                writer.write(escapeCsv(statusStr));
                writer.write(",");
                writer.write(export.creationDateTime() != null ? export.creationDateTime().toString() : "");
                writer.write(",");
                writer.write(export.lastChangeDateTime() != null ? export.lastChangeDateTime().toString() : "");
                writer.write(",");
                writer.write(escapeCsv(export.changeDescription()));
                writer.write(",");
                writer.write(escapeCsv(export.explanatoryComment()));
                writer.write("\n");
            }
        }
    }

    // ---------------------------------------------------------------------
    // 原生 SQL 批量导出（不经 JPA Item 实体图，规避 JOINED 根多态 JOIN 爆炸）
    // ---------------------------------------------------------------------

    /**
     * 查询符合条件的导出行（含 Item 类型过滤，类型名下推为 EXISTS 谓词）。
     */
    List<AdministeredItemExport> queryExports(MetadataExportRequest request) {
        ExportQuery query = buildExportQuery(request);
        List<ExportRow> rows = jdbcTemplate.query(query.sql(), this::mapRow, query.params().toArray());
        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = rows.stream().map(ExportRow::id).toList();
        Map<UUID, List<AdministeredItemExport.ScopedIdentifierExport>> identifiers = queryIdentifiers(ids);
        Map<UUID, List<String>> submissionOrgs = querySubmissionOrganizations(ids);

        return rows.stream()
                .map(r -> r.toExport(
                        identifiers.getOrDefault(r.id(), List.of()),
                        submissionOrgs.getOrDefault(r.id(), List.of())))
                .toList();
    }

    /** 全量命中数（分页时填充 totalCount 用）。 */
    int countExports(MetadataExportRequest request) {
        ExportQuery query = buildCountQuery(request);
        Integer count = jdbcTemplate.queryForObject(query.sql(), Integer.class, query.params().toArray());
        return count == null ? 0 : count;
    }

    /**
     * 组装导出主查询。拆分为独立方法以便单测直接断言 SQL 与参数。
     */
    ExportQuery buildExportQuery(MetadataExportRequest request) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        appendWhereConditions(request, where, params);

        List<String> canonicalTypes = resolveCanonicalTypes(request);
        if (request.itemTypes() != null && !request.itemTypes().isEmpty()) {
            if (canonicalTypes.isEmpty()) {
                // 全部类型名均不在白名单：无命中（与内存过滤口径一致）
                where.append(" AND 1=0");
            } else {
                StringBuilder typeOr = new StringBuilder();
                for (String type : canonicalTypes) {
                    if (!typeOr.isEmpty()) {
                        typeOr.append(" OR ");
                    }
                    typeOr.append(ItemTypeSql.itemTypeExistsClause(env, type));
                }
                where.append(" AND (").append(typeOr).append(")");
            }
        }

        String sql = "SELECT " +
                "ai.id, " +
                ItemTypeSql.itemTypeExprFor(env, canonicalTypes) + " AS item_type, " +
                designationLabelExpr() + " AS item_label, " +
                firstOne("df.value", t("definition") + " df", "df.item_id = i.id AND df.value IS NOT NULL") + " AS item_definition, " +
                "rs.registration_status, " +
                "rs.effective_date, " +
                "rs.until_date, " +
                "rs.administrative_note, " +
                "rs.unresolved_issue, " +
                "rs.administrative_status, " +
                "ra.id AS ra_id, " +
                firstOne("on1.value", t("organization_name") + " on1", "on1.organization_id = ra.id") + " AS ra_name, " +
                "sr.organization_id AS st_org_id, " +
                firstOne("on2.value", t("organization_name") + " on2", "on2.organization_id = sr.organization_id") + " AS st_org_name, " +
                "sr.contact_id AS st_contact_id, " +
                firstOne("ind.name", t("individual") + " ind", "ind.id = sr.contact_id") + " AS st_contact_ind_name, " +
                firstOne("on3.value", t("organization_name") + " on3", "on3.organization_id = " +
                        firstOne("c.organization_id", t("contact") + " c", "c.id = sr.contact_id")) + " AS st_contact_org_name, " +
                "ai.creation_date_time, " +
                "ai.last_change_date_time, " +
                "ai.change_description, " +
                "ai.explanatory_comment, " +
                "ai.origin " +
                "FROM " + t("administered_item") + " ai " +
                "JOIN " + t("registered_item") + " ri ON ri.id = ai.id " +
                "JOIN " + t("item") + " i ON i.id = ri.item_id " +
                "LEFT JOIN " + t("registration_state") + " rs ON rs.id = ai.registration_state_id " +
                "LEFT JOIN " + t("registration_authority") + " ra ON ra.id = ai.registration_authority_id " +
                "LEFT JOIN " + t("stewardship_record") + " sr ON sr.id = ai.stewardship_record_id " +
                where +
                // 分页必须稳定序：creation_date_time 不唯一，追加 ai.id 作 tiebreaker
                " ORDER BY ai.creation_date_time, ai.id" +
                pagingClause(request, params);

        return new ExportQuery(sql, params);
    }

    /**
     * 全量命中数 COUNT（与主查询共用 WHERE 组装），供分页时填充 totalCount。
     */
    ExportQuery buildCountQuery(MetadataExportRequest request) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendWhereConditions(request, where, params);

        List<String> canonicalTypes = resolveCanonicalTypes(request);
        if (request.itemTypes() != null && !request.itemTypes().isEmpty()) {
            if (canonicalTypes.isEmpty()) {
                where.append(" AND 1=0");
            } else {
                StringBuilder typeOr = new StringBuilder();
                for (String type : canonicalTypes) {
                    if (!typeOr.isEmpty()) {
                        typeOr.append(" OR ");
                    }
                    typeOr.append(ItemTypeSql.itemTypeExistsClause(env, type));
                }
                where.append(" AND (").append(typeOr).append(")");
            }
        }

        String sql = "SELECT COUNT(*) " +
                "FROM " + t("administered_item") + " ai " +
                "JOIN " + t("registered_item") + " ri ON ri.id = ai.id " +
                "JOIN " + t("item") + " i ON i.id = ri.item_id " +
                "LEFT JOIN " + t("registration_state") + " rs ON rs.id = ai.registration_state_id " +
                "LEFT JOIN " + t("registration_authority") + " ra ON ra.id = ai.registration_authority_id " +
                "LEFT JOIN " + t("stewardship_record") + " sr ON sr.id = ai.stewardship_record_id " +
                where;
        return new ExportQuery(sql, params);
    }

    /** 通用过滤条件（状态/机构/时间窗），主查询与 COUNT 共用。 */
    private void appendWhereConditions(MetadataExportRequest request, StringBuilder where, List<Object> params) {
        if (request.registrationStatus() != null) {
            where.append(" AND rs.registration_status = ?");
            params.add(request.registrationStatus().name());
        }
        if (request.registrationAuthorityId() != null) {
            where.append(" AND ai.registration_authority_id = ?");
            params.add(env.toJdbcUuid(request.registrationAuthorityId()));
        }
        if (request.stewardshipOrganizationId() != null) {
            where.append(" AND sr.organization_id = ?");
            params.add(env.toJdbcUuid(request.stewardshipOrganizationId()));
        }
        if (request.createdAfter() != null) {
            where.append(" AND ai.creation_date_time >= ?");
            params.add(Timestamp.valueOf(request.createdAfter()));
        }
        if (request.createdBefore() != null) {
            where.append(" AND ai.creation_date_time <= ?");
            params.add(Timestamp.valueOf(request.createdBefore()));
        }
        if (request.modifiedAfter() != null) {
            where.append(" AND ai.last_change_date_time >= ?");
            params.add(Timestamp.valueOf(request.modifiedAfter()));
        }
        if (request.modifiedBefore() != null) {
            where.append(" AND ai.last_change_date_time <= ?");
            params.add(Timestamp.valueOf(request.modifiedBefore()));
        }
    }

    private List<String> resolveCanonicalTypes(MetadataExportRequest request) {
        List<String> canonicalTypes = new ArrayList<>();
        if (!CollectionUtils.isEmpty(request.itemTypes())) {
            request.itemTypes().stream()
                    .map(ItemTypeSql::normalizeType)
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(canonicalTypes::add);
        }
        return canonicalTypes;
    }

    /** 分页子句：SQL Server OFFSET/FETCH，其余方言 LIMIT/OFFSET；参数追加到查询参数尾。 */
    private String pagingClause(MetadataExportRequest request, List<Object> params) {
        if (!request.paged()) {
            return "";
        }
        // page/size 已校验（page≤100000、size≤10000），offset 不会溢出 int
        int offset = Math.toIntExact((long) request.page() * request.size());
        if (env.vendor() == DatabaseVendor.SQLSERVER) {
            params.add(offset);
            params.add(request.size());
            return " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        }
        params.add(request.size());
        params.add(offset);
        return " LIMIT ? OFFSET ?";
    }

    private Map<UUID, List<AdministeredItemExport.ScopedIdentifierExport>> queryIdentifiers(List<UUID> administeredIds) {
        String sql = "SELECT si.identified_item_id, si.identifier, si.version, si.namespace_id, " +
                firstOne("d2.value", t("designation") + " d2", "d2.item_id = ns.id") + " AS ns_label " +
                "FROM " + t("scoped_identifier") + " si " +
                "LEFT JOIN " + t("namespace") + " ns ON ns.id = si.namespace_id " +
                "WHERE si.identified_item_id IN (" + placeholders(administeredIds.size()) + ")";
        List<Object> params = administeredIds.stream().map(env::toJdbcUuid).collect(Collectors.toList());

        Map<UUID, List<AdministeredItemExport.ScopedIdentifierExport>> byItem = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            UUID owner = DatabaseEnvironment.toUuid(rs.getObject("identified_item_id"));
            byItem.computeIfAbsent(owner, k -> new ArrayList<>())
                    .add(new AdministeredItemExport.ScopedIdentifierExport(
                            rs.getString("identifier"),
                            rs.getString("version"),
                            DatabaseEnvironment.toUuid(rs.getObject("namespace_id")),
                            rs.getString("ns_label")));
        }, params.toArray());
        return byItem;
    }

    private Map<UUID, List<String>> querySubmissionOrganizations(List<UUID> administeredIds) {
        String sql = "SELECT rsr.registered_item_id, " +
                firstOne("on4.value", t("organization_name") + " on4", "on4.organization_id = s.organization_id") + " AS org_name " +
                "FROM " + t("registered_item_submission_record") + " rsr " +
                "JOIN " + t("submission_record") + " s ON s.id = rsr.submission_record_id " +
                "WHERE rsr.registered_item_id IN (" + placeholders(administeredIds.size()) + ")";
        List<Object> params = administeredIds.stream().map(env::toJdbcUuid).collect(Collectors.toList());

        Map<UUID, List<String>> byItem = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            String orgName = rs.getString("org_name");
            if (orgName == null) {
                return;
            }
            UUID owner = DatabaseEnvironment.toUuid(rs.getObject("registered_item_id"));
            byItem.computeIfAbsent(owner, k -> new ArrayList<>()).add(orgName);
        }, params.toArray());
        return byItem;
    }

    private ExportRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        String contactIndName = rs.getString("st_contact_ind_name");
        String contactOrgName = rs.getString("st_contact_org_name");

        return new ExportRow(
                DatabaseEnvironment.toUuid(rs.getObject("id")),
                rs.getString("item_type"),
                rs.getString("item_label"),
                rs.getString("item_definition"),
                status(rs, "registration_status"),
                ldt(rs, "effective_date"),
                ldt(rs, "until_date"),
                rs.getString("administrative_note"),
                rs.getString("unresolved_issue"),
                rs.getString("administrative_status"),
                DatabaseEnvironment.toUuid(rs.getObject("ra_id")),
                rs.getString("ra_name"),
                DatabaseEnvironment.toUuid(rs.getObject("st_org_id")),
                rs.getString("st_org_name"),
                DatabaseEnvironment.toUuid(rs.getObject("st_contact_id")),
                contactIndName != null ? contactIndName : contactOrgName,
                ldt(rs, "creation_date_time"),
                ldt(rs, "last_change_date_time"),
                rs.getString("change_description"),
                rs.getString("explanatory_comment"),
                rs.getString("origin")
        );
    }

    /**
     * designation 取值：与 {@link ItemNamingHelper#preferredDesignation(Item)} 同口径，
     * 语言优先级 zh-CN &gt; en-US &gt; 其它 &gt; 无语言。
     */
    private String designationLabelExpr() {
        String from = t("designation") + " d LEFT JOIN " + env.qualify("language_identification")
                + " li ON li.id = d.language_id";
        String orderBy = "CASE WHEN li.language_identifier = '" + ItemNamingHelper.DEFAULT_LANGUAGE + "' THEN 0"
                + " WHEN li.language_identifier = '" + ItemNamingHelper.FALLBACK_LANGUAGE + "' THEN 1"
                + " WHEN li.language_identifier IS NULL THEN 9 ELSE 5 END";
        return firstOneOrdered("d.value", from, "d.item_id = i.id", orderBy);
    }

    /**
     * administrative_status 列由 {@code AdministrativeStatusConverter} 以标准值文本
     * 存储（如 "initial"），与导出 DTO 字段口径一致，直接透传。
     */
    private static RegistrationStatus status(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : RegistrationStatus.valueOf(value);
    }

    private static LocalDateTime ldt(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    /** 表名按当前方言/schema 限定（EXT 表自动走 ext schema）。 */
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

    /** 带排序的"取第一行"标量子查询（方言分支同 {@link #firstOne}）。 */
    private String firstOneOrdered(String column, String from, String condition, String orderBy) {
        if (env.vendor() == DatabaseVendor.SQLSERVER) {
            return "(SELECT TOP 1 " + column + " FROM " + from + " WHERE " + condition
                    + " ORDER BY " + orderBy + ")";
        }
        return "(SELECT " + column + " FROM " + from + " WHERE " + condition
                + " ORDER BY " + orderBy + " LIMIT 1)";
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    record ExportQuery(String sql, List<Object> params) {
    }

    /**
     * 导出行中间记录：主查询一行对应一个 AdministeredItem，
     * 标识符与提交组织由批量副查询回填后组装为 {@link AdministeredItemExport}。
     * 包级可见以便单测直接构造。
     */
    record ExportRow(
            UUID id,
            String itemType,
            String itemLabel,
            String itemDefinition,
            RegistrationStatus status,
            LocalDateTime effectiveDate,
            LocalDateTime untilDate,
            String administrativeNote,
            String unresolvedIssue,
            String administrativeStatus,
            UUID raId,
            String raName,
            UUID stOrgId,
            String stOrgName,
            UUID stContactId,
            String stContactName,
            LocalDateTime creationDateTime,
            LocalDateTime lastChangeDateTime,
            String changeDescription,
            String explanatoryComment,
            String origin
    ) {
        AdministeredItemExport toExport(
                List<AdministeredItemExport.ScopedIdentifierExport> identifiers,
                List<String> submissionOrganizations) {
            AdministeredItemExport.RegistrationStateExport stateExport = null;
            if (status != null) {
                stateExport = new AdministeredItemExport.RegistrationStateExport(
                        status, effectiveDate, untilDate, administrativeNote,
                        unresolvedIssue, administrativeStatus);
            }
            AdministeredItemExport.RegistrationAuthorityExport raExport =
                    raId == null ? null : new AdministeredItemExport.RegistrationAuthorityExport(raId, raName);
            AdministeredItemExport.StewardshipExport stewardshipExport = null;
            if (stOrgId != null || stContactId != null) {
                stewardshipExport = new AdministeredItemExport.StewardshipExport(
                        stOrgId, stOrgName, stContactId, stContactName);
            }
            return new AdministeredItemExport(
                    id, itemType, itemLabel, itemDefinition, identifiers, stateExport,
                    raExport, stewardshipExport, submissionOrganizations,
                    creationDateTime, lastChangeDateTime, changeDescription,
                    explanatoryComment, origin);
        }
    }

    /**
     * 构建查询条件（仅用于 count 估算；批量查询走 {@link #buildExportQuery} 原生 SQL）
     */
    Specification<AdministeredItem> buildSpecification(MetadataExportRequest request) {
        Specification<AdministeredItem> spec = Specification.where(null);

        if (request.registrationStatus() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("registrationState").get("registrationStatus"), request.registrationStatus()));
        }

        if (request.registrationAuthorityId() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("registrationAuthority").get("id"), request.registrationAuthorityId()));
        }

        if (request.stewardshipOrganizationId() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("stewardshipRecord").get("organization").get("id"), request.stewardshipOrganizationId()));
        }

        if (request.createdAfter() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("creationDateTime"), request.createdAfter()));
        }

        if (request.createdBefore() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("creationDateTime"), request.createdBefore()));
        }

        if (request.modifiedAfter() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("lastChangeDateTime"), request.modifiedAfter()));
        }

        if (request.modifiedBefore() != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("lastChangeDateTime"), request.modifiedBefore()));
        }

        return spec;
    }

    /**
     * 将 AdministeredItem 转换为导出 DTO
     */
    /**
     * 公开的条目快照导出（供联邦合并包使用）。
     */
    public AdministeredItemExport exportItem(AdministeredItem ai) {
        return toExport(ai);
    }

    AdministeredItemExport toExport(AdministeredItem ai) {
        Item item = ai.getItem();
        Item realItem = item == null ? null : (Item) Hibernate.unproxy(item);

        String itemType = realItem == null ? null : realItem.getClass().getSimpleName();
        String itemLabel = realItem == null ? null : ItemNamingHelper.preferredDesignation(realItem);
        String itemDefinition = realItem == null ? null : preferredDefinition(realItem);

        List<AdministeredItemExport.ScopedIdentifierExport> identifiers = new ArrayList<>();
        if (ai.getScopedIdentifiers() != null) {
            for (ScopedIdentifier si : ai.getScopedIdentifiers()) {
                String nsLabel = null;
                UUID nsId = null;
                if (si.getNamespace() != null) {
                    nsId = si.getNamespace().getId();
                    nsLabel = ItemNamingHelper.preferredDesignation(si.getNamespace());
                }
                identifiers.add(new AdministeredItemExport.ScopedIdentifierExport(
                        si.getIdentifier(), si.getVersion(), nsId, nsLabel));
            }
        }

        AdministeredItemExport.RegistrationStateExport stateExport = null;
        RegistrationState state = ai.getRegistrationState();
        if (state != null) {
            String administrativeStatus = state.getAdministrativeStatus() == null
                    ? null
                    : state.getAdministrativeStatus().standardValue();
            stateExport = new AdministeredItemExport.RegistrationStateExport(
                    state.getRegistrationStatus(),
                    state.getEffectiveDate(),
                    state.getUntilDate(),
                    state.getAdministrativeNote(),
                    state.getUnresolvedIssue(),
                    administrativeStatus
            );
        }

        AdministeredItemExport.RegistrationAuthorityExport raExport = null;
        RegistrationAuthority ra = ai.getRegistrationAuthority();
        if (ra != null) {
            raExport = new AdministeredItemExport.RegistrationAuthorityExport(
                    ra.getId(), organizationName(ra));
        }

        AdministeredItemExport.StewardshipExport stewardshipExport = null;
        StewardshipRecord sr = ai.getStewardshipRecord();
        if (sr != null) {
            Organization org = sr.getOrganization();
            UUID orgId = org == null ? null : org.getId();
            String orgName = org == null ? null : organizationName(org);

            var contact = sr.getContact();
            UUID contactId = contact == null ? null : contact.getId();
            String contactName = contact == null ? null : contactName(contact);

            stewardshipExport = new AdministeredItemExport.StewardshipExport(
                    orgId, orgName, contactId, contactName);
        }

        List<String> submissionOrgs = new ArrayList<>();
        if (ai.getSubmissionRecords() != null) {
            for (SubmissionRecord rec : ai.getSubmissionRecords()) {
                if (rec.getOrganization() != null) {
                    String name = organizationName(rec.getOrganization());
                    if (name != null) {
                        submissionOrgs.add(name);
                    }
                }
            }
        }

        return new AdministeredItemExport(
                ai.getId(),
                itemType,
                itemLabel,
                itemDefinition,
                identifiers,
                stateExport,
                raExport,
                stewardshipExport,
                submissionOrgs,
                ai.getCreationDateTime(),
                ai.getLastChangeDateTime(),
                ai.getChangeDescription(),
                ai.getExplanatoryComment(),
                ai.getOrigin()
        );
    }

    private MetadataExportResult.ExportMetadata buildMetadata(MetadataExportRequest request, int totalCount) {
        Map<String, Object> filters = new HashMap<>();
        if (request.registrationStatus() != null) {
            filters.put("registrationStatus", request.registrationStatus());
        }
        if (request.registrationAuthorityId() != null) {
            filters.put("registrationAuthorityId", request.registrationAuthorityId());
        }
        if (request.stewardshipOrganizationId() != null) {
            filters.put("stewardshipOrganizationId", request.stewardshipOrganizationId());
        }
        if (request.createdAfter() != null) {
            filters.put("createdAfter", request.createdAfter());
        }
        if (request.createdBefore() != null) {
            filters.put("createdBefore", request.createdBefore());
        }
        if (request.modifiedAfter() != null) {
            filters.put("modifiedAfter", request.modifiedAfter());
        }
        if (request.modifiedBefore() != null) {
            filters.put("modifiedBefore", request.modifiedBefore());
        }
        if (!CollectionUtils.isEmpty(request.itemTypes())) {
            filters.put("itemTypes", request.itemTypes());
        }
        if (!CollectionUtils.isEmpty(request.fields())) {
            filters.put("fields", request.fields());
        }

        return new MetadataExportResult.ExportMetadata(
                SCHEMA_VERSION, LocalDateTime.now(), totalCount, filters);
    }

    private ObjectMapper createFilteredMapper(List<String> fields) {
        ObjectMapper mapper = objectMapper.copy();

        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        if (!CollectionUtils.isEmpty(fields)) {
            Set<String> fieldSet = fields.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            // Always include id if fields are specified to ensure identity
            fieldSet.add("id");
            filterProvider.addFilter(EXPORT_FILTER_NAME,
                    SimpleBeanPropertyFilter.filterOutAllExcept(fieldSet));
        } else {
            filterProvider.addFilter(EXPORT_FILTER_NAME, SimpleBeanPropertyFilter.serializeAll());
        }
        filterProvider.setFailOnUnknownId(false);
        mapper.setFilterProvider(filterProvider);

        return mapper;
    }

    private static String preferredDefinition(Item item) {
        if (item == null || item.getDefinitions() == null || item.getDefinitions().isEmpty()) {
            return null;
        }
        for (Definition def : item.getDefinitions()) {
            if (def.getText() != null && def.getText().getValue() != null) {
                return def.getText().getValue();
            }
        }
        return null;
    }

    private static String contactName(Contact contact) {
        if (contact == null) {
            return null;
        }
        if (contact.getIndividual() != null
                && contact.getIndividual().getName() != null
                && contact.getIndividual().getName().getValue() != null) {
            return contact.getIndividual().getName().getValue();
        }
        return organizationName(contact.getOrganization());
    }

    private static String organizationName(Organization org) {
        if (org == null) {
            return null;
        }
        if (org.getName() != null && !org.getName().isEmpty()) {
            Sign s = org.getName().get(0);
            if (s != null && s.getValue() != null) {
                return s.getValue();
            }
        }
        return null;
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

}
