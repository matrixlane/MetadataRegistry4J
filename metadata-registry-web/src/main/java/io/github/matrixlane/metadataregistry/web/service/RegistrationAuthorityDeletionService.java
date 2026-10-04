package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityDeletionPreview;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
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
 * <p>按外键依赖顺序逐项清理注册机构名下的全部数据：管理项 Item 图、提交记录、
 * 主管记录、注册人员（含账号绑定与激活指向）、自有命名空间（sys: 系统命名空间
 * 保留，仅解除 NamingAuthority 挂接）、注册流程迁移规则与文档语言关联，
 * 最后删除注册机构行与 Organization JOINED 行。</p>
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
     */
    @Transactional(readOnly = true)
    public RegistrationAuthorityDeletionPreview preview(UUID registrationAuthorityId) {
        requireExists(registrationAuthorityId);
        List<UUID> aiIds = administeredItemIds(registrationAuthorityId);
        List<UUID> allRegIds = allRegisteredItemIds(aiIds);
        Set<UUID> submissionRecordIds = submissionRecordIdsToDelete(allRegIds);
        return new RegistrationAuthorityDeletionPreview(
                registrationAuthorityId,
                aiIds.size(),
                submissionRecordIds.size(),
                countWhere("registrar", "registration_authority_id", registrationAuthorityId),
                countWhere("namespace", "registration_authority_id", registrationAuthorityId),
                countWhere("stewardship_record", "organization_id", registrationAuthorityId),
                countWhere("registration_transition_rule", "registration_authority_id", registrationAuthorityId));
    }

    /**
     * 级联删除注册机构及其名下全部数据。注册机构不存在时抛 404。
     * 调用方负责版本/治理策略前置校验（同一事务内执行）。
     */
    @Transactional
    public void delete(UUID registrationAuthorityId) {
        requireExists(registrationAuthorityId);

        // ===== 1. 该 RA 下全部 AdministeredItem 的 Item 图 =====
        // identified_item.id(=registered_item.id=administered_item.id) 与 item.id 是分离的，需先查后删。
        List<UUID> aiIds = administeredItemIds(registrationAuthorityId);
        List<UUID> attachedIds = queryUuidsByIds(
                "SELECT id FROM " + t("attached_item") + " WHERE administered_item_id IN (", aiIds);
        List<UUID> allRegIds = concat(aiIds, attachedIds);
        List<UUID> itemIds = queryUuidsByIds(
                "SELECT item_id FROM " + t("identified_item") + " WHERE id IN (", allRegIds);
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
        deleteByIds(t("designation_context_relation"), "designation_id", designationIds);
        deleteByIds(t("definition_context_relation"), "definition_id", definitionIds);
        deleteByIds(t("designation_namespace"), "designation_id", designationIds);
        deleteByIds(t("designation_naming_convention"), "designation_id", designationIds);
        deleteByIds(t("slot"), "item_id", itemIds);
        deleteByIds(t("designation"), "item_id", itemIds);
        deleteByIds(t("definition"), "item_id", itemIds);

        // 1d. 提交关联 + 注册流程审批记录
        Set<UUID> submissionRecordIds = submissionRecordIdsToDelete(allRegIds);
        deleteByIds(t("submission_record_registered_item"), "registered_item_id", allRegIds);
        deleteByIds(t("registration_transition_approval"), "administered_item_id", aiIds);

        // 1e. 仅关联被删 item 的 SubmissionRecord（以及无剩余关联的 SR）
        deleteByIds(t("submission_record"), "id", List.copyOf(submissionRecordIds));

        // 1f. Item 图主干（子表 -> 父表）
        List<UUID> stateIds = queryUuidsByIds(
                "SELECT registration_state_id FROM " + t("administered_item") + " WHERE id IN (", aiIds);
        deleteByIds(t("registration_state"), "id", stateIds);
        deleteByIds(t("attached_item"), "id", attachedIds);
        deleteByIds(t("administered_item"), "id", aiIds);
        deleteByIds(t("registered_item_reference_document"), "registered_item_id", allRegIds);
        deleteByIds(t("registered_item"), "id", allRegIds);
        deleteByIds(t("identified_item"), "id", allRegIds);
        deleteByIds(t("item"), "id", itemIds);

        // ===== 2. StewardshipRecord（organization_id = RA） =====
        deleteWhereEq(t("stewardship_record"), "organization_id", registrationAuthorityId);

        // ===== 3. Registrar（registration_authority_id = RA） =====
        List<UUID> registrarIds = queryUuids(
                "SELECT id FROM " + t("registrar") + " WHERE registration_authority_id = ?",
                uuidArg(registrationAuthorityId));
        deleteByIds(t("registration_transition_rule_approver"), "registrar_id", registrarIds);
        deleteByIds(t("registration_transition_approval"), "registrar_id", registrarIds);
        // 解绑 ContactAccountBinding、清理所有 account.active_registrar_id 指向（EXT）
        deleteByIds(t("contact_account_binding"), "contact_id", registrarIds);
        for (List<UUID> chunk : chunks(registrarIds)) {
            jdbcTemplate.update("UPDATE " + t("account") + " SET active_registrar_id = NULL"
                    + " WHERE active_registrar_id IN (" + inClause(chunk.size()) + ")", uuidParams(chunk));
        }
        // 删 Registrar / Contact（JOINED 两行）；Registrar 的组织即 RA 本体，无需置空
        deleteByIds(t("registrar"), "id", registrarIds);
        deleteByIds(t("contact"), "id", registrarIds);
        // 直属于该 RA 组织的其余 Contact（普通联系人身份）及其绑定
        List<UUID> orgContactIds = queryUuids(
                "SELECT id FROM " + t("contact") + " WHERE organization_id = ?",
                uuidArg(registrationAuthorityId));
        deleteByIds(t("contact_account_binding"), "contact_id", orgContactIds);
        deleteByIds(t("contact"), "id", orgContactIds);
        deleteWhereEq(t("namespace_permission"), "organization_id", registrationAuthorityId);

        // ===== 4. RA 自有 Namespace 及其从属（sys: 系统命名空间保留） =====
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

        // sys: 等其余命名空间的 NamingAuthority 指向该 RA 时置空（系统命名空间保留）
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
        deleteWhereEq(t("registration_authority"), "id", registrationAuthorityId);
        deleteWhereEq(t("organization_name"), "organization_id", registrationAuthorityId);
        deleteWhereEq(t("organization_email"), "organization_id", registrationAuthorityId);
        deleteWhereEq(t("organization_phone"), "organization_id", registrationAuthorityId);
        deleteWhereEq(t("organization"), "id", registrationAuthorityId);

        log.info("注册机构级联删除完成 [raId={}]", registrationAuthorityId);
    }

    // ---------- 内部步骤 ----------

    /** 删除命名空间图：命名空间自身是 Item，含其 identified_item / 指称 / 定义 / 标识及其卫星。 */
    private void deleteNamespaceGraph(List<UUID> namespaceIds) {
        if (namespaceIds.isEmpty()) {
            return;
        }
        // 命名空间自身的 identified_item 行（注册过的命名空间才有）
        List<UUID> nsIdentifiedIds = queryUuidsByIds(
                "SELECT id FROM " + t("identified_item") + " WHERE item_id IN (", namespaceIds);
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
        deleteByIds(t("designation_context_relation"), "designation_id", nsDesignationIds);
        deleteByIds(t("definition_context_relation"), "definition_id", nsDefinitionIds);
        deleteByIds(t("designation_namespace"), "designation_id", nsDesignationIds);
        deleteByIds(t("designation_naming_convention"), "designation_id", nsDesignationIds);
        deleteByIds(t("slot"), "item_id", namespaceIds);
        deleteByIds(t("designation"), "item_id", namespaceIds);
        deleteByIds(t("definition"), "item_id", namespaceIds);

        // 任意 Item 的指称引用该命名空间（其余 RA 的项也可能引用）
        deleteByIds(t("designation_namespace"), "namespace_id", namespaceIds);
        deleteByIds(t("namespace_naming_convention"), "namespace_id", namespaceIds);
        // 命名空间标识策略卫星配置（EXT）
        deleteByIds(t("namespace_identifier_settings"), "namespace_id", namespaceIds);
        deleteByIds(t("identifier_strategy_config"), "namespace_id", namespaceIds);
        deleteByIds(t("identifier_sequence_registry"), "namespace_id", namespaceIds);
        deleteByIds(t("namespace_permission"), "namespace_id", namespaceIds);
        deleteByIds(t("scoped_identifier"), "namespace_id", namespaceIds);
        deleteByIds(t("scoped_identifier"), "identified_item_id", nsIdentifiedIds);

        // 命名空间 Item 行与子类行
        deleteByIds(t("namespace"), "id", namespaceIds);
        deleteByIds(t("identified_item"), "id", nsIdentifiedIds);
        deleteByIds(t("item"), "id", namespaceIds);
    }

    /**
     * 将被删除的 SubmissionRecord 集合：仅关联被删 item 的（全部关联都在被删集合内，
     * 与幸存 item 共享提交的 SR 保留）以及无剩余关联的 SR。join 行未删时即可算得，
     * 与删除执行时的语义一致（预览复用）。
     */
    private Set<UUID> submissionRecordIdsToDelete(List<UUID> allRegIds) {
        List<UUID> withSurvivingLinks = allRegIds.isEmpty()
                ? queryUuids("SELECT DISTINCT submission_record_id FROM " + t("submission_record_registered_item"))
                : queryUuidsByIds("SELECT DISTINCT submission_record_id FROM "
                        + t("submission_record_registered_item") + " WHERE registered_item_id NOT IN (", allRegIds);
        Set<UUID> surviving = new LinkedHashSet<>(withSurvivingLinks);
        Set<UUID> toDelete = new LinkedHashSet<>();
        for (UUID id : queryUuids("SELECT id FROM " + t("submission_record"))) {
            if (!surviving.contains(id)) {
                toDelete.add(id);
            }
        }
        return toDelete;
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
        deleteByIds(t("item_mapping_subject"), "item_id", itemIds);
        deleteByIds(t("item_mapping_object"), "item_id", itemIds);
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
     */
    private boolean columnExists(String table, String column) {
        return switch (databaseEnvironment.vendor()) {
            case SQLITE -> {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM pragma_table_info(?) WHERE name = ?", Integer.class, table, column);
                yield count != null && count > 0;
            }
            case SQLSERVER, DM -> {
                String schema = databaseEnvironment.schemaOf(table);
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?"
                                + (schema == null ? "" : " AND TABLE_SCHEMA = ?"),
                        Integer.class,
                        schema == null ? new Object[]{table, column} : new Object[]{table, column, schema});
                yield count != null && count > 0;
            }
        };
    }

    // ---------- JDBC 工具（与 ContextTestDataInitializer 同构） ----------

    /** 表名按当前方言/schema 限定（EXT 表路由到 ext schema；SQLite 为裸名）。 */
    private String t(String table) {
        return databaseEnvironment.qualifyAuto(table);
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
