package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.AdministeredItemResponse;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
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
import java.util.List;
import java.util.UUID;

/**
 * Service providing unified search across all metadata entity types.
 * <p>
 * Uses native SQL via JdbcTemplate to bypass Hibernate's polymorphic JOIN
 * which causes SQL Server's 4096-column limit to be exceeded when
 * querying AdministeredItem → IdentifiedItem → Item (JOINED inheritance
 * with 11+ subclasses).
 * <p>
 * Note: Not marked @Transactional because JdbcTemplate manages its own
 * connections and the native SQL queries do not need JPA transaction
 * participation. Adding @Transactional can cause rollback failures on
 * SQL Server when the connection pool has auto-commit enabled.
 */
@Service
@RequiredArgsConstructor
public class MetadataSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseEnvironment env;

    /** 表名按当前方言/schema 限定。 */
    private String t(String table) {
        return env.qualify(table);
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
     * Unified search across all metadata entities.
     *
     * @param keyword  search keyword (matched against name, identifier, definition text)
     * @param type     optional entity type to filter (e.g., "DataElement", "ObjectClass")
     * @param status   optional registration status to filter
     * @param pageable pagination parameters
     * @return paginated search results as AdministeredItemResponse
     */
    public Page<AdministeredItemResponse> search(String keyword, String type,
                                                  RegistrationStatus status, Pageable pageable) {
        String keywordPattern = (keyword != null && !keyword.isBlank())
                ? "%" + keyword.trim().toLowerCase() + "%"
                : null;
        String typeFilter = (type != null && !type.isBlank()) ? type : null;
        String statusFilter = (status != null) ? status.name() : null;

        // Build WHERE clause dynamically
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> countParams = new ArrayList<>();
        List<Object> selectParams = new ArrayList<>();

        if (keywordPattern != null) {
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

        // In JOINED inheritance strategy there is no discriminator column (dtype).
        // The type parameter is currently ignored for filtering.
        // To properly filter by type would require checking each subclass table.
        // if (typeFilter != null) {
        //     where.append(" AND i.dtype = ?");
        //     countParams.add(typeFilter);
        //     selectParams.add(typeFilter);
        // }

        if (statusFilter != null) {
            where.append(" AND rs.registration_status = ?");
            countParams.add(statusFilter);
            selectParams.add(statusFilter);
        }

        // Count query
        String countSql = "SELECT COUNT(DISTINCT ai.id) FROM " + t("administered_item") + " ai " +
                "JOIN " + t("identified_item") + " ii ON ai.id = ii.id " +
                "JOIN " + t("item") + " i ON ii.item_id = i.id " +
                "LEFT JOIN " + t("registration_state") + " rs ON ai.registration_state_id = rs.id" +
                where;

        Long total = jdbcTemplate.queryForObject(countSql, Long.class, countParams.toArray());
        if (total == null) total = 0L;

        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // Main query with pagination
        String selectSql = "SELECT DISTINCT " +
                "ai.id, " +
                "i.id AS item_id, " +
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
                "ai.creation_date_time, " +
                "ai.last_change_date_time, " +
                "ai.change_description " +
                "FROM " + t("administered_item") + " ai " +
                "JOIN " + t("identified_item") + " ii ON ai.id = ii.id " +
                "JOIN " + t("item") + " i ON ii.item_id = i.id " +
                "LEFT JOIN " + t("scoped_identifier") + " si ON si.identified_item_id = ai.id " +
                "LEFT JOIN " + t("namespace") + " ns ON si.namespace_id = ns.id " +
                "LEFT JOIN " + t("registration_authority") + " ra ON ai.registration_authority_id = ra.id " +
                "LEFT JOIN " + t("registration_state") + " rs ON ai.registration_state_id = rs.id" +
                where +
                " ORDER BY ai.creation_date_time DESC" +
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

    private AdministeredItemResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AdministeredItemResponse(
                uuid(rs, "id"),
                uuid(rs, "item_id"),
                null, // item_type — JOINED inheritance has no dtype column
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
                null,
                null,
                List.of(),
                ldt(rs, "creation_date_time"),
                ldt(rs, "last_change_date_time"),
                rs.getString("change_description")
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
