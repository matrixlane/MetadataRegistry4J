package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.SubmissionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionRecordRepository extends JpaRepository<SubmissionRecord, UUID> {

    boolean existsByContactId(UUID contactId);

    /**
     * 取指定 RegisteredItem 关联的最近一条 SubmissionRecord（按 id 降序近似时间顺序）。
     */
    @Query("""
            SELECT sr FROM SubmissionRecord sr
            JOIN sr.registeredItems ri
            WHERE ri.id = :itemId
            ORDER BY sr.id DESC
            """)
    Optional<SubmissionRecord> findLatestByRegisteredItemId(@Param("itemId") UUID itemId);

    /**
     * 取指定 RegisteredItem 关联的全部 SubmissionRecord（用于删除管理项前清理 join 表关联）。
     */
    @Query("""
            SELECT sr FROM SubmissionRecord sr
            JOIN sr.registeredItems ri
            WHERE ri.id = :itemId
            """)
    List<SubmissionRecord> findAllByRegisteredItemId(@Param("itemId") UUID itemId);
}
