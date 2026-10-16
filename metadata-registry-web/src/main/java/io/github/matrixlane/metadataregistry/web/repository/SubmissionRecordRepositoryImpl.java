package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SubmissionRecordRepositoryImpl implements SubmissionRecordRepositoryCustom {

    private final JdbcTemplate jdbc;
    private final DatabaseEnvironment databaseEnvironment;

    @Override
    public List<UUID> findSubmissionRecordIdsByRegisteredItemId(UUID registeredItemId) {
        String sql = """
                SELECT submission_record_id
                  FROM %s
                 WHERE registered_item_id = ?
                 ORDER BY submission_record_id DESC
                """.formatted(databaseEnvironment.qualify("registered_item_submission_record"));
        return jdbc.query(sql,
                (rs, n) -> DatabaseEnvironment.toUuid(rs.getObject(1)),
                databaseEnvironment.toJdbcUuid(registeredItemId));
    }

    @Override
    public Map<UUID, Long> countRegisteredItemsBySubmissionRecordIds(Collection<UUID> submissionRecordIds) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        if (submissionRecordIds == null || submissionRecordIds.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", Collections.nCopies(submissionRecordIds.size(), "?"));
        String sql = "SELECT submission_record_id, COUNT(*) FROM "
                + databaseEnvironment.qualify("registered_item_submission_record")
                + " WHERE submission_record_id IN (" + placeholders + ")"
                + " GROUP BY submission_record_id";
        Object[] params = submissionRecordIds.stream()
                .map(databaseEnvironment::toJdbcUuid)
                .toArray();
        jdbc.query(sql, rs -> {
            result.put(DatabaseEnvironment.toUuid(rs.getObject(1)), rs.getLong(2));
        }, params);
        return result;
    }
}