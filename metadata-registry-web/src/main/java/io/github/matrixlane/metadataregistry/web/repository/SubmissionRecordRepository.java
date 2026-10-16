package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.SubmissionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionRecordRepository
        extends JpaRepository<SubmissionRecord, UUID>, SubmissionRecordRepositoryCustom {

    boolean existsByContactId(UUID contactId);

    /**
     * 取指定 RegisteredItem 关联的全部 SubmissionRecord（按 id 降序），调用方自行取第一条。
     *
     * <p>历史上同一管理项可被多次提交（提交→回退→再提交），因此 {@code registered_item_submission_record}
     * 对同一 {@code registered_item_id} 通常含多条不同的 {@code submission_record_id}，不能简单用
     * {@code Optional} 承载——必须返回 {@code List} 由调用方取最新一条。</p>
     *
     * <p><b>实现方式已变更（2026-10-09）</b>：原为 JPQL {@code JOIN sr.registeredItems}，
     * 需实例化 {@code RegisteredItem} —— Item 子类的 JOINED 继承根，Hibernate 会把 30+
     * 张子类表拉进同一条 SQL 的 join；该方法又在条目列表的逐条路径上被调用
     * （{@code buildItemMeta}），N+1 乘以重 join 使关联规模上万后列表接口数十秒级超时。
     * 现改为：先按关联表列取 {@code submission_record_id}（见
     * {@link SubmissionRecordRepositoryCustom}），再 {@link JpaRepository#findAllById} 取实体，
     * 不触碰继承映射。返回顺序仍为 id 降序，与原 JPQL 一致。</p>
     */
    default List<SubmissionRecord> findAllByRegisteredItemIdOrderByIdDesc(UUID itemId) {
        List<UUID> ids = findSubmissionRecordIdsByRegisteredItemId(itemId);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<SubmissionRecord> records = new ArrayList<>(findAllById(ids));
        records.sort(Comparator.comparing(SubmissionRecord::getId).reversed());
        return records;
    }

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
     *
     * <p>与 {@link #findAllByRegisteredItemIdOrderByIdDesc} 共用同一 id 取数路径——
     * 同样不得经 {@code JOIN sr.registeredItems} 展开受管项侧实体。</p>
     */
    default List<SubmissionRecord> findAllByRegisteredItemId(UUID itemId) {
        return findAllByRegisteredItemIdOrderByIdDesc(itemId);
    }
}
