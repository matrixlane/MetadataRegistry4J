package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityDeletionPreview;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 注册机构级联删除服务（acg-ra-governance）。
 *
 * <p>按外键依赖顺序逐项清理注册机构名下的全部数据：管理项 Item 图（含 Context
 * JOINED 子表与指向被删 Context 的指称/定义/配对关联，registry_specification.context_id
 * 置空——该表已是独立根实体、非 Item 子表，只是以 context_id 引用 Context）、提交记录
 * （候选集 = join 行触及的 SR，SQL 侧 NOT EXISTS 分批）、主管记录、
 * 注册人员（registration_authority_id = RA 或 organization_id = RA 的 Registrar 行，
 * 含账号绑定与激活指向）、自有命名空间（仅解除 NamingAuthority
 * 挂接）、注册流程迁移规则与文档语言关联，最后删除 Primary 标志卫星行
 * （ext.registration_authority，move-ra-primary-to-ext）、注册机构行与
 * Organization JOINED 行。</p>
 *
 * <p>实现走原生 JDBC（{@link DatabaseEnvironment#qualifyAuto} 方言/schema 感知 +
 * {@link JdbcTemplate}），与 DimensionalityService 清 join 表、
 * ContextTestDataInitializer 按 FK 顺序级联删除的既有先例同构：JPA 级联在
 * JOINED 继承 + 多对多 join 表场景下既慢又易触发方言差异，JDBC 删除顺序可控。
 * 只引用"当前 schema 中仍存在"的表（approval_* / fhir_element 等已被
 * V20260901015  DROP，不在清理范围）。</p>
 *
 * <p><b>事务约束：必须单事务（默认 REQUIRED），严禁 REQUIRES_NEW</b>——
 * SQLite 单连接池下嵌套新事务会造成死锁（项目先例）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationAuthorityDeletionService {

    /** IN 子句参数分块大小（避免超大 IN；SQLite 变量上限 999）。 */
    private static final int CHUNK = 200;

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseEnvironment databaseEnvironment;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;

    /**
     * 删除预览：统计级联删除将波及的数据量。注册机构不存在时抛 404。
     *
     * <p>角色门槛（M4）：仅 SUPER_ADMIN 或 REGISTRAR 业务角色可查看删除预览；
     * 角色取自 SecurityContext authorities，与 SecurityConfig 的 ROLE_* 口径一致，
     * 服务层校验——前端门控不作为唯一防线。其余角色（含 SUBMITTER）拒绝。
     */
    @Transactional(readOnly = true)
    public RegistrationAuthorityDeletionPreview preview(UUID registrationAuthorityId) {
        requirePreviewAuthorized();
        requireExists(registrationAuthorityId);
        List<UUID> aiIds = administeredItemIds(registrationAuthorityId);
        List<UUID> allRegIds = allRegisteredItemIds(aiIds);
        Set<UUID> submissionRecordIds = submissionRecordIdsToDelete(allRegIds);
        return new RegistrationAuthorityDeletionPreview(
                registrationAuthorityId,
                aiIds.size(),
                submissionRecordIds.size(),
                registrarCount(registrationAuthorityId),
                countWhere("namespace", "registration_authority_id", registrationAuthorityId),
                countWhere("stewardship_record", "organization_id", registrationAuthorityId),
                countWhere("registration_transition_rule", "registration_authority_id", registrationAuthorityId));
    }

    /**
     * 级联删除注册机构及其名下全部数据。注册机构不存在时抛 404。
     * 调用方负责版本判定前置校验（同一事务内执行）。
     */
    @Transactional
    public void delete(UUID registrationAuthorityId) {
        requireExists(registrationAuthorityId);

        // ===== 1. 该 RA 下全部 AdministeredItem 的 Item 图 =====
        // registered_item.id(=administered_item.id) 与 item.id 是分离的，需先查后删。
        List<UUID> aiIds = administeredItemIds(registrationAuthorityId);
        List<UUID> attachedIds = queryUuidsByIds(
                "SELECT id FROM " + t("attached_item") + " WHERE administered_item_id IN (", aiIds);
        List<UUID> allRegIds = concat(aiIds, attachedIds);
        List<UUID> itemIds = queryUuidsByIds(
                "SELECT item_id FROM " + t("registered_item") + " WHERE id IN (", allRegIds);
        // 松散 UUID 引用（ satellite 只存裸 UUID、无 JPA 关联）的并集：Item id ∪ RegisteredItem id
        List<UUID> itemRefIds = concat(itemIds, allRegIds);
        List<UUID> siIds = queryUuidsByIds(
                "SELECT id FROM " + t("scoped_identifier") + " WHERE identified_item_id IN (", allRegIds);

        // 1a. Scoped_Identifier 卫星（EXT）
        deleteByIds(t("scoped_identifier_lifecycle"), "scoped_identifier_id", siIds);
        deleteByIds(t("identifier_version_history"), "scoped_identifier_id", siIds);

        // 1b. 引用 AdministeredItem / Item 的卫星表（含 EXT 卫星）
        deleteByIds(t("ai_generation_record"), "administered_item_id", aiIds);
        deleteByIds(t("fhir_element_mapping"), "local_item_id", itemRefIds);
        deleteByIds(t("interop_data_element"), "mapped_item_id", itemRefIds);
        deleteByIds(t("item_link"), "local_item_id", itemRefIds);
        clearMergeRequestItemLinks(itemRefIds);
        deleteItemMappings(itemIds);
        deleteByIds(t("version_history"), "item_id", itemIds);

        // 1c. Item 从属：Slot / Designation / Definition 及其 join 子表
        List<UUID> designationIds = queryUuidsByIds(
                "SELECT id FROM " + t("designation") + " WHERE item_id IN (", itemIds);
        List<UUID> definitionIds = queryUuidsByIds(
                "SELECT id FROM " + t("definition") + " WHERE item_id IN (", itemIds);
        deleteDesignationDefinitionPairings(designationIds, definitionIds);
        deleteByIds(t("designation_context"), "designation_id", designationIds);
        deleteByIds(t("definition_context"), "definition_id", definitionIds);
        deleteByIds(t("designation_namespace"), "designation_id", designationIds);
        deleteByIds(t("naming_convention_conformance"), "designation_id", designationIds);
        deleteByIds(t("slot"), "item_id", itemIds);
        deleteByIds(t("designation"), "item_id", itemIds);
        deleteByIds(t("definition"), "item_id", itemIds);

        // 1c-ctx. Context 子表（JOINED）与指向被删 Context 的关联行（B1）：
        // Registry Context 是挂在 RA 下的 AdministeredItem（context 行 id = item id）；
        // 连同其余条目（命名空间等）若是 Context 一并清理。先删指向它们的关联行，
        // 再删 Context 子表行，并把 registry_specification.context_id 置空
        // （registry_specification 为独立根实体、不再是 Item 子表，不存在"子表行先于
        // 父 item 行"的顺序约束，只受指向 Context 的外键约束）
        // （全局默认上下文由启动调和 backfillIfInitialized 在下次启动补建并回填）。
        List<UUID> contextIds = queryUuidsByIds(
                "SELECT id FROM " + t("context") + " WHERE id IN (", itemIds);
        if (!contextIds.isEmpty()) {
            deleteByIds(t("designation_context"), "context_id", contextIds);
            deleteByIds(t("definition_context"), "context_id", contextIds);
            deleteByIds(t("designation_definition_pairing"), "context_id", contextIds);
            for (List<UUID> chunk : chunks(contextIds)) {
                jdbcTemplate.update("UPDATE " + t("registry_specification") + " SET context_id = NULL"
                        + " WHERE context_id IN (" + inClause(chunk.size()) + ")", uuidParams(chunk));
            }
            deleteByIds(t("context"), "id", contextIds);
        }

        // 1d. 提交关联 + 注册流程审批记录
        Set<UUID> submissionRecordIds = submissionRecordIdsToDelete(allRegIds);
        deleteByIds(t("registered_item_submission_record"), "registered_item_id", allRegIds);
        deleteByIds(t("registration_transition_approval"), "administered_item_id", aiIds);

        // 1e. 候选集中已无剩余关联的 SubmissionRecord（M3：候选集 = join 行中
        // item_id ∈ 被删集合触及的 SR；SQL 侧 NOT EXISTS 分批删除，其它机构的无关联 SR 不动）
        deleteOrphanedSubmissionRecords(submissionRecordIds);

        // 1f. Item 图主干（子表 -> 父表）
        List<UUID> stateIds = queryUuidsByIds(
                "SELECT registration_state_id FROM " + t("administered_item") + " WHERE id IN (", aiIds);
        deleteByIds(t("registration_state"), "id", stateIds);
        deleteByIds(t("attached_item"), "id", attachedIds);
        deleteByIds(t("administered_item"), "id", aiIds);
        deleteByIds(t("registered_item_reference_document"), "registered_item_id", allRegIds);
        // 2026-10-09 表合并：identified_item 已并入 registered_item，原先针对
        // identified_item 的删除不再单独执行——这些行现在就是 registered_item 行，
        // 由下面这一行统一删除（顺序在 attached_item / administered_item 之后）。
        deleteByIds(t("registered_item"), "id", allRegIds);
        deleteByIds(t("item"), "id", itemIds);

        // ===== 2. StewardshipRecord（organization_id = RA） =====
        deleteWhereEq(t("stewardship_record"), "organization_id", registrationAuthorityId);

        // ===== 3. Registrar（registration_authority_id = RA；organization_id = RA 的
        // Registrar 行一并匹配——兜住 createMyContact(REGISTRAR) 产生的 null-RA 历史/边缘行） =====
        List<UUID> registrarIds = registrarIdsOf(registrationAuthorityId);
        deleteByIds(t("registration_transition_rule_approver"), "registrar_id", registrarIds);
        deleteByIds(t("registration_transition_approval"), "registrar_id", registrarIds);
        // 解绑 ext.contact 账号绑定卫星、清理 ext.registrar 默认注册人员标志卫星
        // （personal-no-login-registrar-default；account.active_registrar_id 列已随
        // V20261009003 删除，不再有置空动作）
        deleteByIds(tContactExtension(), "id", registrarIds);
        deleteByIds(tRegistrarExtension(), "id", registrarIds);
        // 删 Registrar / Contact（JOINED 两行）；Registrar 的组织即 RA 本体，无需置空
        deleteByIds(t("registrar"), "id", registrarIds);
        deleteByIds(t("contact"), "id", registrarIds);
        // 直属于该 RA 组织的其余 Contact（普通联系人身份）及其绑定
        List<UUID> orgContactIds = queryUuids(
                "SELECT id FROM " + t("contact") + " WHERE organization_id = ?",
                uuidArg(registrationAuthorityId));
        deleteByIds(tContactExtension(), "id", orgContactIds);
        deleteByIds(t("contact"), "id", orgContactIds);
        deleteWhereEq(t("namespace_permission"), "organization_id", registrationAuthorityId);

        // ===== 4. RA 自有 Namespace 及其从属 =====
        List<UUID> namespaceIds = queryUuids(
                "SELECT id FROM " + t("namespace") + " WHERE registration_authority_id = ?",
                uuidArg(registrationAuthorityId));
        // registration_authority.namespace_id（遗留 1:1 反向指针列，仅存量库存在）先置空
        if (columnExists("registration_authority", "namespace_id")) {
            for (List<UUID> chunk : chunks(namespaceIds)) {
                jdbcTemplate.update("UPDATE " + t("registration_authority") + " SET namespace_id = NULL"
                        + " WHERE namespace_id IN (" + inClause(chunk.size()) + ")", uuidParams(chunk));
            }
        }
        deleteNamespaceGraph(namespaceIds);

        // 其余命名空间的 NamingAuthority 指向该 RA 时置空
        jdbcTemplate.update("UPDATE " + t("namespace") + " SET naming_authority_id = NULL"
                + " WHERE naming_authority_id = ?", uuidArg(registrationAuthorityId));

        // ===== 5. registration_transition_rule 及其 approver 子表 =====
        List<UUID> ruleIds = queryUuids(
                "SELECT id FROM " + t("registration_transition_rule") + " WHERE registration_authority_id = ?",
                uuidArg(registrationAuthorityId));
        deleteByIds(t("registration_transition_rule_approver"), "rule_id", ruleIds);
        deleteByIds(t("registration_transition_rule"), "id", ruleIds);

        // ===== 6. registration_authority_doc_language join 行 =====
        deleteWhereEq(t("registration_authority_doc_language"), "registration_authority_id", registrationAuthorityId);

        // ===== 7. registration_authority 行 + organization 行（JOINED） =====
        // Primary 标志卫星行（move-ra-primary-to-ext：ext.registration_authority 与
        // mdr 表同名，禁止走 qualifyAuto——同名标准表会被路由到 ext schema）
        deleteWhereEq(tRaExtension(), "id", registrationAuthorityId);
        deleteWhereEq(t("registration_authority"), "id", registrationAuthorityId);
        deleteWhereEq(t("organization_name"), "organization_id", registrationAuthorityId);
        deleteWhereEq(t("organization_email"), "organization_id", registrationAuthorityId);
        deleteWhereEq(t("organization_phone"), "organization_id", registrationAuthorityId);
        deleteWhereEq(t("organization"), "id", registrationAuthorityId);

        log.info("注册机构级联删除完成 [raId={}]", registrationAuthorityId);
    }

    // ---------- 内部步骤 ----------

    /** 删除命名空间图：命名空间自身是 Item，含其 registered_item / 指称 / 定义 / 标识及其卫星。 */
    private void deleteNamespaceGraph(List<UUID> namespaceIds) {
        if (namespaceIds.isEmpty()) {
            return;
        }
        // 命名空间自身的 identified_item 行（注册过的命名空间才有）
        List<UUID> nsIdentifiedIds = queryUuidsByIds(
                "SELECT id FROM " + t("registered_item") + " WHERE item_id IN (", namespaceIds);
        // 两类标识：指向该命名空间内对象的（namespace_id）、命名空间自身的（identified_item_id）
        List<UUID> siInNs = queryUuidsByIds(
                "SELECT id FROM " + t("scoped_identifier") + " WHERE namespace_id IN (", namespaceIds);
        List<UUID> siOfNs = queryUuidsByIds(
                "SELECT id FROM " + t("scoped_identifier") + " WHERE identified_item_id IN (", nsIdentifiedIds);
        List<UUID> nsSiIds = concat(siInNs, siOfNs);
        deleteByIds(t("scoped_identifier_lifecycle"), "scoped_identifier_id", nsSiIds);
        deleteByIds(t("identifier_version_history"), "scoped_identifier_id", nsSiIds);

        List<UUID> nsDesignationIds = queryUuidsByIds(
                "SELECT id FROM " + t("designation") + " WHERE item_id IN (", namespaceIds);
        List<UUID> nsDefinitionIds = queryUuidsByIds(
                "SELECT id FROM " + t("definition") + " WHERE item_id IN (", namespaceIds);
        deleteDesignationDefinitionPairings(nsDesignationIds, nsDefinitionIds);
        deleteByIds(t("designation_context"), "designation_id", nsDesignationIds);
        deleteByIds(t("definition_context"), "definition_id", nsDefinitionIds);
        deleteByIds(t("designation_namespace"), "designation_id", nsDesignationIds);
        deleteByIds(t("naming_convention_conformance"), "designation_id", nsDesignationIds);
        deleteByIds(t("slot"), "item_id", namespaceIds);
        deleteByIds(t("designation"), "item_id", namespaceIds);
        deleteByIds(t("definition"), "item_id", namespaceIds);

        // 任意 Item 的指称引用该命名空间（其余 RA 的项也可能引用）
        deleteByIds(t("designation_namespace"), "namespace_id", namespaceIds);
        deleteByIds(t("naming_convention_utilization"), "namespace_id", namespaceIds);
        // 命名空间标识策略卫星配置（EXT）
        deleteByIds(t("namespace_identifier_settings"), "namespace_id", namespaceIds);
        deleteByIds(t("identifier_strategy_config"), "namespace_id", namespaceIds);
        deleteByIds(t("identifier_sequence_registry"), "namespace_id", namespaceIds);
        deleteByIds(t("namespace_permission"), "namespace_id", namespaceIds);
        deleteByIds(t("scoped_identifier"), "namespace_id", namespaceIds);
        deleteByIds(t("scoped_identifier"), "identified_item_id", nsIdentifiedIds);

        // 命名空间 Item 行与子类行
        deleteByIds(t("namespace"), "id", namespaceIds);
        deleteByIds(t("registered_item"), "id", nsIdentifiedIds);
        deleteByIds(t("item"), "id", namespaceIds);
    }

    /**
     * 将被删除的 SubmissionRecord 集合（M3）：候选集仅限 join 行中
     * item_id ∈ 被删集合触及的 SR（DISTINCT）——不扫描 submission_record 全表，
     * 不波及其他机构的无关联 SR；与幸存 item 共享的 SR 保留。
     * join 行未删时即可算得，与删除执行时的语义一致（预览复用）。
     */
    private Set<UUID> submissionRecordIdsToDelete(List<UUID> allRegIds) {
        if (allRegIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> candidates = new LinkedHashSet<>(queryUuidsByIds(
                "SELECT DISTINCT submission_record_id FROM " + t("registered_item_submission_record")
                        + " WHERE registered_item_id IN (", allRegIds));
        if (candidates.isEmpty()) {
            return Set.of();
        }
        // 与幸存 item 仍有共享关联的候选 SR 保留
        Set<UUID> surviving = new LinkedHashSet<>(queryUuidsByIds(
                "SELECT DISTINCT submission_record_id FROM " + t("registered_item_submission_record")
                        + " WHERE registered_item_id NOT IN (", allRegIds));
        candidates.removeAll(surviving);
        return candidates;
    }

    /**
     * 候选集中已无剩余关联的 SubmissionRecord 删除（M3）：join 行删除后执行，
     * SQL 侧 NOT EXISTS 守卫 + IN 分块；不在候选集内的 SR（含其他机构的无关联 SR）不动。
     */
    private void deleteOrphanedSubmissionRecords(Set<UUID> candidates) {
        for (List<UUID> chunk : chunks(List.copyOf(candidates))) {
            jdbcTemplate.update("DELETE FROM " + t("submission_record") + " WHERE id IN ("
                    + inClause(chunk.size()) + ") AND NOT EXISTS (SELECT 1 FROM "
                    + t("registered_item_submission_record") + " WHERE submission_record_id = "
                    + t("submission_record") + ".id)", uuidParams(chunk));
        }
    }

    /**
     * 该 RA 名下的 Registrar 行 id（M2）：registration_authority_id = RA，
     * 或 organization_id = RA 的 Registrar 行（contact JOINED 子表口径，
     * 兜住 null-RA 的历史/边缘 Registrar 行）。级联删除与删除预览共用同一口径。
     */
    private List<UUID> registrarIdsOf(UUID registrationAuthorityId) {
        return queryUuids(
                "SELECT r.id FROM " + t("registrar") + " r LEFT JOIN " + t("contact") + " c ON c.id = r.id"
                        + " WHERE r.registration_authority_id = ? OR c.organization_id = ?",
                uuidArg(registrationAuthorityId), uuidArg(registrationAuthorityId));
    }

    /** Registrar 行计数（删除预览用，与 {@link #registrarIdsOf} 同口径）。 */
    private long registrarCount(UUID registrationAuthorityId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + t("registrar") + " r LEFT JOIN " + t("contact") + " c ON c.id = r.id"
                        + " WHERE r.registration_authority_id = ? OR c.organization_id = ?",
                Long.class, uuidArg(registrationAuthorityId), uuidArg(registrationAuthorityId));
        return count == null ? 0 : count;
    }

    /**
     * 删除预览授权（M4）：仅 SUPER_ADMIN 或 REGISTRAR 业务角色可查看删除预览；
     * 其余角色（含 SUBMITTER）拒绝。角色取自 SecurityContext authorities，
     * 与 SecurityConfig 的 ROLE_* 口径一致（服务层校验，前端门控不作唯一防线）。
     */
    private void requirePreviewAuthorized() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = auth != null && auth.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a) || "ROLE_REGISTRAR".equals(a));
        if (!allowed) {
            throw new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                    io.github.matrixlane.metadataregistry.web.common.ApiCodes.FORBIDDEN,
                    "仅超级管理员或注册人员可查看删除预览");
        }
    }

    private void deleteDesignationDefinitionPairings(List<UUID> designationIds, List<UUID> definitionIds) {
        if (designationIds.isEmpty() && definitionIds.isEmpty()) {
            return;
        }
        Set<UUID> pairingIds = new LinkedHashSet<>(
                queryUuidsByIds("SELECT id FROM " + t("designation_definition_pairing") + " WHERE designation_id IN (",
                        designationIds));
        pairingIds.addAll(queryUuidsByIds(
                "SELECT id FROM " + t("designation_definition_pairing") + " WHERE definition_id IN (", definitionIds));
        deleteByIds(t("designation_definition_pairing"), "id", List.copyOf(pairingIds));
    }

    private void clearMergeRequestItemLinks(List<UUID> itemRefIds) {
        for (List<UUID> chunk : chunks(itemRefIds)) {
            jdbcTemplate.update("UPDATE " + t("merge_request_item") + " SET linked_local_item_id = NULL"
                    + " WHERE linked_local_item_id IN (" + inClause(chunk.size()) + ")", uuidParams(chunk));
        }
    }

    private void deleteItemMappings(List<UUID> itemIds) {
        deleteByIds(t("subject_mapping"), "item_id", itemIds);
        deleteByIds(t("object_mapping"), "item_id", itemIds);
    }

    private List<UUID> administeredItemIds(UUID registrationAuthorityId) {
        return queryUuids("SELECT id FROM " + t("administered_item") + " WHERE registration_authority_id = ?",
                uuidArg(registrationAuthorityId));
    }

    private List<UUID> allRegisteredItemIds(List<UUID> aiIds) {
        List<UUID> attachedIds = queryUuidsByIds(
                "SELECT id FROM " + t("attached_item") + " WHERE administered_item_id IN (", aiIds);
        return concat(aiIds, attachedIds);
    }

    private void requireExists(UUID registrationAuthorityId) {
        if (!registrationAuthorityRepository.existsById(registrationAuthorityId)) {
            throw new ResourceNotFoundException("RegistrationAuthority", registrationAuthorityId);
        }
    }

    private long countWhere(String table, String column, UUID id) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + t(table) + " WHERE " + column + " = ?",
                Long.class, uuidArg(id));
        return count == null ? 0 : count;
    }

    /**
     * 列是否存在（当前库）。用于存量库遗留列（如 registration_authority.namespace_id）：
     * 新部署的实体驱动 schema（ddl-auto）可能已无该列。
     *
     * <p>DM 分支统一 {@code ALL_TAB_COLUMNS} 口径（与 dm 迁移脚本一致）：
     * DM 的 INFORMATION_SCHEMA.COLUMNS 大小写敏感、结果不可靠；
     * ALL_TAB_COLUMNS 中对象名以大写存储，故 owner/表名/列名一律转大写匹配。
     */
    private boolean columnExists(String table, String column) {
        return switch (databaseEnvironment.vendor()) {
            case SQLITE -> {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM pragma_table_info(?) WHERE name = ?", Integer.class, table, column);
                yield count != null && count > 0;
            }
            case SQLSERVER -> {
                String schema = databaseEnvironment.schemaOf(table);
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?"
                                + (schema == null ? "" : " AND TABLE_SCHEMA = ?"),
                        Integer.class,
                        schema == null ? new Object[]{table, column} : new Object[]{table, column, schema});
                yield count != null && count > 0;
            }
            case DM -> {
                String schema = databaseEnvironment.schemaOf(table);
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ALL_TAB_COLUMNS WHERE OWNER = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                        Integer.class,
                        (schema == null ? databaseEnvironment.schema() : schema).toUpperCase(java.util.Locale.ROOT),
                        table.toUpperCase(java.util.Locale.ROOT),
                        column.toUpperCase(java.util.Locale.ROOT));
                yield count != null && count > 0;
            }
        };
    }

    // ---------- JDBC 工具（与 ContextTestDataInitializer 同构） ----------

    /** 表名按当前方言/schema 限定（EXT 表路由到 ext schema；SQLite 为裸名）。 */
    private String t(String table) {
        return databaseEnvironment.qualifyAuto(table);
    }

    /**
     * RA Primary 标志卫星表限定名（V20261008001）：团队库与标准表同名
     * （ext.registration_authority，经 ext schema 区分），SQLite 物理名
     * ext_registration_authority。与 {@code language_identification} 同名表同理，
     * 不得走 {@link DatabaseEnvironment#qualifyAuto(String)}（会把同名标准表路由到
     * ext schema）。
     */
    private String tRaExtension() {
        return databaseEnvironment.vendor() == DatabaseVendor.SQLITE
                ? databaseEnvironment.qualify("ext_registration_authority")
                : databaseEnvironment.qualifyExt("registration_authority");
    }

    /**
     * 账号绑定卫星表限定名（V20261009001）：团队库与标准表同名（ext.contact），
     * SQLite 物理名 ext_contact。同 tRaExtension 的同名表路由约束。
     */
    private String tContactExtension() {
        return databaseEnvironment.vendor() == DatabaseVendor.SQLITE
                ? databaseEnvironment.qualify("ext_contact")
                : databaseEnvironment.qualifyExt("contact");
    }

    /**
     * 默认注册人员标志卫星表限定名（V20261009002）：团队库与标准表同名
     * （ext.registrar），SQLite 物理名 ext_registrar。
     */
    private String tRegistrarExtension() {
        return databaseEnvironment.vendor() == DatabaseVendor.SQLITE
                ? databaseEnvironment.qualify("ext_registrar")
                : databaseEnvironment.qualifyExt("registrar");
    }

    private Object uuidArg(UUID id) {
        return databaseEnvironment.toJdbcUuid(id);
    }

    private List<UUID> queryUuids(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> DatabaseEnvironment.toUuid(rs.getObject(1)), args);
    }

    /** 以 IN 子句（自动分块、结果合并）查询 UUID 列。 */
    private List<UUID> queryUuidsByIds(String sqlPrefix, List<UUID> ids) {
        List<UUID> result = new ArrayList<>();
        for (List<UUID> chunk : chunks(ids)) {
            result.addAll(queryUuids(sqlPrefix + inClause(chunk.size()) + ")", uuidParams(chunk)));
        }
        return result;
    }

    private void deleteByIds(String table, String column, List<UUID> ids) {
        for (List<UUID> chunk : chunks(ids)) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE " + column + " IN ("
                    + inClause(chunk.size()) + ")", uuidParams(chunk));
        }
    }

    private void deleteWhereEq(String table, String column, UUID id) {
        jdbcTemplate.update("DELETE FROM " + table + " WHERE " + column + " = ?", uuidArg(id));
    }

    private List<List<UUID>> chunks(List<UUID> ids) {
        List<List<UUID>> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += CHUNK) {
            result.add(ids.subList(i, Math.min(i + CHUNK, ids.size())));
        }
        return result;
    }

    private Object[] uuidParams(List<UUID> ids) {
        return ids.stream().map(databaseEnvironment::toJdbcUuid).toArray();
    }

    private String inClause(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private List<UUID> concat(List<UUID> first, List<UUID> second) {
        Set<UUID> merged = new LinkedHashSet<>(first);
        merged.addAll(second);
        return List.copyOf(merged);
    }
}
