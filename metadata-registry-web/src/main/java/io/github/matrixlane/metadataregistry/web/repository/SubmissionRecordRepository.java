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
     * 取指定 RegisteredItem 关联的全部 SubmissionRecord（按 id 降序），调用方自行取第一条。
     *
     * <p>历史上同一管理项可被多次提交（提交→回退→再提交），因此 {@code submission_record_registered_item}
     * 对同一 {@code registered_item_id} 通常含多条不同的 {@code submission_record_id}，不能简单用
     * {@code Optional} 承载——必须返回 {@code List} 由调用方取最新一条（{@code ORDER BY sr.id DESC}）。
     * 同时 {@code sr.registeredItems} 为 {@code @ManyToMany}，{@code DISTINCT} 是必需的，
     * 否则 JOIN 会因 {@code submission_record_registered_item} 无 UNIQUE 约束出现重复 sr 行。</p>
     */
    @Query("""
            SELECT DISTINCT sr FROM SubmissionRecord sr
            JOIN sr.registeredItems ri
            WHERE ri.id = :itemId
            ORDER BY sr.id DESC
            """)
    List<SubmissionRecord> findAllByRegisteredItemIdOrderByIdDesc(@Param("itemId") UUID itemId);

    /**
     * 取指定 RegisteredItem 关联的最近一条 SubmissionRecord（按 id 降序近似时间顺序）。
     *
     * <p>默认实现依赖 Spring Data 派生方法命名。历史上同一管理项可被多次提交，因此最新一条即
     * {@code ORDER BY id DESC LIMIT 1}；语义对应 {@link #findAllByRegisteredItemIdOrderByIdDesc}
     * 后取第一条。当不存在任何提交记录时返回 {@link Optional#empty()}。</p>
     */
    default Optional<SubmissionRecord> findLatestByRegisteredItemId(UUID itemId) {
        List<SubmissionRecord> all = findAllByRegisteredItemIdOrderByIdDesc(itemId);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

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
