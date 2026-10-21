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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdministeredItemRepository
        extends JpaRepository<AdministeredItem, UUID>, JpaSpecificationExecutor<AdministeredItem> {

    Optional<AdministeredItem> findByItem(Item item);

    Optional<AdministeredItem> findByItem_Id(UUID itemId);

    /**
     * 只取归属注册机构标识，不加载受管项实体图。
     *
     * <p>{@link #findByItem_Id(UUID)} 返回 {@code AdministeredItem} 实体，Hibernate 会把
     * Item 全部子类表与受管项的整个关联对象图（registration_state / stewardship_record /
     * constraint_set / 引用文档…）拼进同一条 SQL 的 join。SQLite 单条查询 FROM 项上限为
     * 64，带受管项的条目在此必抛 {@code at most 64 tables in a join}
     * （见 KNOWN_FAILURES 的 failure-2026-09-21-sqlite-64-table-join-limit）。
     * 权限校验只需要归属注册机构一个标量，用投影查询即可完全避开该 join。</p>
     *
     * <p>无受管项行时返回 {@link Optional#empty()}；RA 未设时投影结果为 {@code null}，
     * 此时投影查询返回带 {@code null} 值的 {@code Optional}——调用方需以
     * {@link Optional#isPresent()} 与 {@code orElse(null)} 分别处理"无行"与"RA 未设"，
     * 二者语义相同（放行）。</p>
     */
    @Query("SELECT ai.registrationAuthority.id FROM AdministeredItem ai WHERE ai.item.id = :itemId")
    Optional<UUID> findRegistrationAuthorityIdByItemId(@Param("itemId") UUID itemId);

    /**
     * 条目注册元数据的**标量投影**——{@code buildItemMeta} 的唯一数据来源。
     *
     * <p>一次性取齐该条目所需的全部受管属性：受管项 id、注册状态与 Registration_State
     * 表 22 的其余属性、注册机构 id 与其名称、以及 Stewardship_Record 的 id 及其
     * contact / organization。</p>
     *
     * <p><b>为什么必须投影而不是加载实体</b>（2026-10-09）：本方法在条目列表响应中被
     * <b>逐条</b>调用（{@code ItemAdministrationService.buildItemMeta}）。原实现另有一处
     * {@code findByItem_Id} 取完整 {@code AdministeredItem}，而该实体对
     * registration_authority / organization_name / registration_state / previous_state /
     * stewardship_record / contact / individual / organization 均为 EAGER，Hibernate 会把
     * Item 子类继承树一并拉进同一条 SQL 的 join——实测单次 43ms、731 逻辑读，列表每条约
     * 0.4s。关联为空时该开销不可见，关联填满后（WS 363/364 基准数据补齐受管链）即暴露为
     * 列表接口数十秒级超时。改投影后不再触碰继承展开。</p>
     *
     * <p>列序与调用方 {@code ItemAdministrationService#buildItemMeta} 强耦合，改动须同步。</p>
     */
    @Query("""
            SELECT ai.id,
                   rs.registrationStatus,
                   ai.registrationAuthority.id,
                   (SELECT n.value FROM ai.registrationAuthority.name n),
                   rs.effectiveDate,
                   rs.untilDate,
                   rs.administrativeStatus,
                   rs.administrativeNote,
                   rs.unresolvedIssue,
                   sr.id,
                   c.id,
                   o.id
            FROM AdministeredItem ai
            LEFT JOIN ai.registrationState rs
            LEFT JOIN ai.stewardshipRecord sr
            LEFT JOIN sr.contact c
            LEFT JOIN sr.organization o
            WHERE ai.item.id = :itemId
            """)
    List<Object[]> findMetaByItemId(@Param("itemId") UUID itemId);

    /**
     * {@link #findMetaByItemId(UUID)} 的批量版本，供列表页一次取回整页（避免 N+1）。
     * 返回行含 {@code itemId} 作为第 0 列（单条版由调用方的 itemId 隐含，批量版须自带）。
     */
    @Query("""
            SELECT ai.item.id, ai.id,
                   rs.registrationStatus,
                   ai.registrationAuthority.id,
                   (SELECT n.value FROM ai.registrationAuthority.name n),
                   rs.effectiveDate,
                   rs.untilDate,
                   rs.administrativeStatus,
                   rs.administrativeNote,
                   rs.unresolvedIssue,
                   sr.id,
                   c.id,
                   o.id
            FROM AdministeredItem ai
            LEFT JOIN ai.registrationState rs
            LEFT JOIN ai.stewardshipRecord sr
            LEFT JOIN sr.contact c
            LEFT JOIN sr.organization o
            WHERE ai.item.id IN :itemIds
            """)
    List<Object[]> findMetaByItemIds(@Param("itemIds") Collection<UUID> itemIds);

    /**
     * 一批条目的参考文档关联 id（引用关联 §9.5.2），返回 {@code itemId -> [docId]} 行对。
     *
     * <p>只读关联表列，不加载 {@code ReferenceDocument} 实体也不展开受管项继承树。</p>
     */
    @Query("""
            SELECT ai.item.id, rrd.id
            FROM AdministeredItem ai
            JOIN ai.referenceDocuments rrd
            WHERE ai.item.id IN :itemIds
            """)
    List<Object[]> findReferenceDocumentIdsByItemIds(@Param("itemIds") Collection<UUID> itemIds);

    @Query("""
            SELECT ai FROM AdministeredItem ai WHERE ai.lastChangeDateTime > :since
            """)
    List<AdministeredItem> findChangedAfter(@Param("since") LocalDateTime since);

    /** 概览页统计：时间窗口内创建的受管项总数。 */
    @Query("SELECT COUNT(ai) FROM AdministeredItem ai WHERE ai.creationDateTime >= :since")
    long countByCreationDateTimeAfter(@Param("since") LocalDateTime since);

    /**
     * 概览页统计：时间窗口内创建的指定条目类型的受管项数。
     *
     * <p>类型判定走 {@code ai.item.id IN (SELECT ...)} 子查询而非 {@code TYPE(ai.item)}，
     * 避免 Hibernate 为 joined 继承树生成展开全部 Item 子类表的 join
     * （SQLite 64 表 join 上限，见 {@link #findMetaByItemId} 注释）。</p>
     */
    @Query("SELECT COUNT(ai) FROM AdministeredItem ai WHERE ai.creationDateTime >= :since AND ai.item.id IN (SELECT de.id FROM DataElement de)")
    long countCreatedAfterDataElement(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(ai) FROM AdministeredItem ai WHERE ai.creationDateTime >= :since AND ai.item.id IN (SELECT oc.id FROM ObjectClass oc)")
    long countCreatedAfterObjectClass(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(ai) FROM AdministeredItem ai WHERE ai.creationDateTime >= :since AND ai.item.id IN (SELECT p.id FROM Property p)")
    long countCreatedAfterProperty(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(ai) FROM AdministeredItem ai WHERE ai.creationDateTime >= :since AND ai.item.id IN (SELECT vd.id FROM ValueDomain vd)")
    long countCreatedAfterValueDomain(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(ai) FROM AdministeredItem ai WHERE ai.creationDateTime >= :since AND ai.item.id IN (SELECT cd.id FROM ConceptualDomain cd)")
    long countCreatedAfterConceptualDomain(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(ai) FROM AdministeredItem ai WHERE ai.creationDateTime >= :since AND ai.item.id IN (SELECT dec.id FROM DataElementConcept dec)")
    long countCreatedAfterDataElementConcept(@Param("since") LocalDateTime since);

    /** 概览页状态分布：按注册状态分组计数（以受管项为基，状态缺失时 status 为 null）。 */
    @Query("""
            SELECT rs.registrationStatus, COUNT(ai)
            FROM AdministeredItem ai
            LEFT JOIN ai.registrationState rs
            GROUP BY rs.registrationStatus
            """)
    List<Object[]> countGroupedByRegistrationStatus();

    /**
     * 概览页"近期注册项变更"：按最近变更时间倒序的标量投影（避免加载受管项实体图，
     * 原因见 {@link #findMetaByItemId} 注释）。
     *
     * <p>返回行：{@code [0]=itemId, [1]=lastChangeDateTime, [2]=changeDescription}。</p>
     */
    @Query("""
            SELECT ai.item.id, ai.lastChangeDateTime, ai.changeDescription
            FROM AdministeredItem ai
            ORDER BY ai.lastChangeDateTime DESC
            """)
    List<Object[]> findRecentChanges(Pageable pageable);

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
