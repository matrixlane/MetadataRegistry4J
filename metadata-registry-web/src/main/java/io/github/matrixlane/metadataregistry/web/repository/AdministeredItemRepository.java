package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdministeredItemRepository
        extends JpaRepository<AdministeredItem, UUID>, JpaSpecificationExecutor<AdministeredItem> {

    Optional<AdministeredItem> findByItem(Item item);

    Optional<AdministeredItem> findByItem_Id(UUID itemId);

    @Query("""
            SELECT ai.id, rs.registrationStatus
            FROM AdministeredItem ai
            LEFT JOIN ai.registrationState rs
            WHERE ai.item.id = :itemId
            """)
    List<Object[]> findMetaByItemId(@Param("itemId") UUID itemId);

    @Query("""
            SELECT ai FROM AdministeredItem ai WHERE ai.lastChangeDateTime > :since
            """)
    List<AdministeredItem> findChangedAfter(@Param("since") LocalDateTime since);

    /**
     * 按管理状态与归属联系人查询（工作空间 DRAFTS / REJECTED 视图）。
     *
     * <p>归属判定：主管记录联系人（stewardshipRecord.contact）在 contactIds 中，
     * 或最近一条 SubmissionRecord 的联系人在 contactIds 中。</p>
     */
    @Query("""
            SELECT ai FROM AdministeredItem ai
            JOIN ai.registrationState rs
            LEFT JOIN ai.stewardshipRecord sr
            LEFT JOIN sr.contact sc
            WHERE rs.administrativeStatus = :status
            AND (
                sc.id IN :contactIds
                OR EXISTS (
                    SELECT 1 FROM SubmissionRecord sub
                    JOIN sub.registeredItems ri
                    WHERE ri.id = ai.id
                    AND sub.contact.id IN :contactIds
                    AND sub.id = (
                        SELECT MAX(sub2.id) FROM SubmissionRecord sub2
                        JOIN sub2.registeredItems ri2
                        WHERE ri2.id = ai.id
                    )
                )
            )
            """)
    Page<AdministeredItem> findByAdministrativeStatusAndOwnerContacts(
            @Param("status") AdministrativeStatus status,
            @Param("contactIds") List<UUID> contactIds,
            Pageable pageable);

    @Query("""
            SELECT COUNT(ai) FROM AdministeredItem ai
            JOIN ai.registrationState rs
            LEFT JOIN ai.stewardshipRecord sr
            LEFT JOIN sr.contact sc
            WHERE rs.administrativeStatus = :status
            AND (
                sc.id IN :contactIds
                OR EXISTS (
                    SELECT 1 FROM SubmissionRecord sub
                    JOIN sub.registeredItems ri
                    WHERE ri.id = ai.id
                    AND sub.contact.id IN :contactIds
                    AND sub.id = (
                        SELECT MAX(sub2.id) FROM SubmissionRecord sub2
                        JOIN sub2.registeredItems ri2
                        WHERE ri2.id = ai.id
                    )
                )
            )
            """)
    long countByAdministrativeStatusAndOwnerContacts(
            @Param("status") AdministrativeStatus status,
            @Param("contactIds") List<UUID> contactIds);

    /**
     * 当前注册机构下待审核项（工作空间 PENDING 视图）。
     *
     * <p>包含管理状态为 PROVISIONALLY_QUALIFIED 的项，以及存在未生效批准记录的项。</p>
     */
    @Query("""
            SELECT ai FROM AdministeredItem ai
            JOIN ai.registrationState rs
            WHERE ai.registrationAuthority.id = :authorityId
            AND (
                rs.administrativeStatus = :provisionallyQualified
                OR EXISTS (
                    SELECT 1 FROM RegistrationTransitionApproval rta
                    WHERE rta.administeredItem.id = ai.id
                )
            )
            """)
    Page<AdministeredItem> findPendingByRegistrationAuthority(
            @Param("authorityId") UUID authorityId,
            @Param("provisionallyQualified") AdministrativeStatus provisionallyQualified,
            Pageable pageable);

    @Query("""
            SELECT COUNT(ai) FROM AdministeredItem ai
            JOIN ai.registrationState rs
            WHERE ai.registrationAuthority.id = :authorityId
            AND (
                rs.administrativeStatus = :provisionallyQualified
                OR EXISTS (
                    SELECT 1 FROM RegistrationTransitionApproval rta
                    WHERE rta.administeredItem.id = ai.id
                )
            )
            """)
    long countPendingByRegistrationAuthority(
            @Param("authorityId") UUID authorityId,
            @Param("provisionallyQualified") AdministrativeStatus provisionallyQualified);
}
