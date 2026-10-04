package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.ApproverMode;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AttachedItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationTransitionApproval;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationTransitionRule;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.StewardshipRecord;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.SubmissionRecord;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityDeletionPreview;
import io.github.matrixlane.metadataregistry.web.dto.contact.MyContactRequest;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.IdentifiedItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationTransitionApprovalRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationTransitionRuleRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.repository.StewardshipRecordRepository;
import io.github.matrixlane.metadataregistry.web.repository.SubmissionRecordRepository;
import io.github.matrixlane.metadataregistry.web.seed.SystemDefaultNamespaceInitializer;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 注册机构级联删除集成测试（acg-ra-governance 验收）：
 * 造"满配"注册机构，级联删除后扫描 sqlite schema 中所有含 item 指向列或
 * RA 指向列的表，断言零残留行；sys: 系统命名空间保留且 NamingAuthority 置空。
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:ra-deletion-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main",
        "spring.jpa.mapping-resources=orm/orm-sqlite.xml"
})
@Transactional
@ActiveProfiles("test")
class RegistrationAuthorityDeletionServiceIntegrationTest {

    @Autowired
    private RegistrationAuthorityDeletionService deletionService;
    @Autowired
    private RegistrationAuthorityService registrationAuthorityService;
    @Autowired
    private RegistrationAuthorityRepository raRepository;
    @Autowired
    private NamespaceRepository namespaceRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private RegistrarService registrarService;
    @Autowired
    private ContactAccountBindingService bindingService;
    @Autowired
    private ContactRepository contactRepository;
    @Autowired
    private io.github.matrixlane.metadataregistry.web.repository.IndividualRepository individualRepository;
    @Autowired
    private ContextRepository contextRepository;
    @Autowired
    private AdministeredItemRepository administeredItemRepository;
    @Autowired
    private IdentifiedItemRepository identifiedItemRepository;
    @Autowired
    private ScopedIdentifierRepository scopedIdentifierRepository;
    @Autowired
    private StewardshipRecordRepository stewardshipRecordRepository;
    @Autowired
    private SubmissionRecordRepository submissionRecordRepository;
    @Autowired
    private LanguageIdentificationRepository languageRepository;
    @Autowired
    private RegistrationProcessService registrationProcessService;
    @Autowired
    private SystemSettingService systemSettingService;
    @Autowired
    private RegistrationTransitionRuleRepository ruleRepository;
    @Autowired
    private RegistrationTransitionApprovalRepository approvalRepository;
    @Autowired
    private RegistrySpecificationRepository registrySpecificationRepository;
    @Autowired
    private RegistrarRepository registrarRepository;
    @Autowired
    private ContactService contactService;
    @Autowired
    private RegistryContextBootstrapService registryContextBootstrapService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DatabaseEnvironment databaseEnvironment;

    @PersistenceContext
    private EntityManager entityManager;

    private LanguageIdentification zh;

    /** 满配 RA 的全部相关 id，删除后用于逐表零残留扫描。 */
    private Map<String, List<UUID>> deletedIds;

    private UUID raId;
    private UUID ra2Id;
    private UUID registrarId;
    private UUID registrar2Id;
    private UUID plainContactId;
    private UUID adminAccountId;
    private UUID sysNsId;
    private UUID raNsId;
    private UUID sharedSrId;
    private UUID orphanSrId;
    private UUID otherItemAiId;
    private UUID ai1Id;

    @BeforeEach
    void setUp() {
        deletedIds = new LinkedHashMap<>();
        zh = new LanguageIdentification();
        zh.setLanguageIdentifier("zh-CN");
        zh.setScriptIdentifier("Hans");
        zh.setGeopoliticalTerritoryIdentifier("CN");
        zh = languageRepository.save(zh);

        buildFullFeaturedAuthority();
        // JPA 写入刷新到库，供删除服务的原生 JDBC 读取（同事务内 JDBC 不触发 Hibernate flush）
        entityManager.flush();
        // M4：删除预览要求 SUPER_ADMIN 或 REGISTRAR 业务角色；默认以 SUPER_ADMIN 身份执行
        authenticateAs(SystemRole.SUPER_ADMIN);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 以指定系统角色（+ 可选业务角色）装配 SecurityContext，供删除预览的服务层角色校验使用。 */
    private void authenticateAs(SystemRole role, String... extraAuthorities) {
        java.util.List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        if (role != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        }
        for (String a : extraAuthorities) {
            authorities.add(new SimpleGrantedAuthority(a));
        }
        AuthenticatedUser principal = new AuthenticatedUser(
                adminAccountId, "del-admin", role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private void buildFullFeaturedAuthority() {
        // ---- 两个注册机构：ra 将被删除，ra2 及其数据必须原样存活 ----
        RegistrationAuthority ra = newAuthority("被删机构");
        ra.getEmailAddress().add("ra@example.org");
        ra.getPhoneNumber().add(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber.of("010-12345678"));
        ra = raRepository.save(ra);
        raId = ra.getId();

        RegistrationAuthority ra2 = newAuthority("幸存机构");
        ra2 = raRepository.save(ra2);
        ra2Id = ra2.getId();

        // ---- 账号 + Registrar（含绑定与激活指向） ----
        Account admin = newAccount("del-admin");
        adminAccountId = admin.getId();
        var registrar = registrarService.createRegistrarForUser(
                admin.getId(), admin.getUsername(), null, "注册管理员", ra.getId(), ra);
        registrarId = registrar.getId();
        admin.setActiveRegistrarId(registrar.getId());
        accountRepository.save(admin);

        Account admin2 = newAccount("del-admin-2");
        var registrar2 = registrarService.createRegistrarForUser(
                admin2.getId(), admin2.getUsername(), null, "注册管理员", ra2.getId(), ra2);
        registrar2Id = registrar2.getId();
        // ra2 的管理项（共享提交记录用），归属 ra2
        Context ctxB = newContext("幸存上下文", "幸存条目的定义。");
        AdministeredItem aiB = newAdministeredItem(ctxB, ra2);
        otherItemAiId = aiB.getId();

        // ---- sys: 系统命名空间（须保留，NamingAuthority 指向被删 RA） ----
        Namespace sysNs = namespaceRepository
                .findByShorthandPrefix(SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_PREFIX)
                .orElseGet(() -> {
                    Namespace ns = new Namespace();
                    ns.setShorthandPrefix(SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_PREFIX);
                    ns.setSchemeReference(Sign.of(SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_SCHEME));
                    return namespaceRepository.save(ns);
                });
        sysNs.setNamingAuthority(ra);
        sysNs = namespaceRepository.save(sysNs);
        sysNsId = sysNs.getId();

        // ---- RA 自有命名空间（含指称、identified_item、自身标识、卫星配置） ----
        Namespace raNs = new Namespace();
        raNs.setShorthandPrefix("ra-del");
        raNs.setSchemeReference(Sign.of("urn:test:ra-deletion"));
        raNs.setRegistrationAuthority(ra);
        Designation nsDesignation = newDesignation("被删命名空间", zh);
        nsDesignation.setItem(raNs);
        raNs.getDesignations().add(nsDesignation);
        raNs = namespaceRepository.save(raNs);
        raNsId = raNs.getId();
        IdentifiedItem nsIdentified = new IdentifiedItem(raNs);
        nsIdentified = identifiedItemRepository.save(nsIdentified);
        raNs.setIdentifiedItem(nsIdentified);
        namespaceRepository.save(raNs);
        ScopedIdentifier nsSi = new ScopedIdentifier("NS-1", raNs, nsIdentified);
        nsSi.setVersion("1.0");
        nsIdentified.getScopedIdentifiers().add(nsSi);
        scopedIdentifierRepository.save(nsSi);
        insertScopedIdentifierSatellites(nsSi.getId());

        // ---- 满配管理项 ×2（指称/定义/Slot/注册状态/标识/主管/提交/挂载项） ----
        Context ctx1 = newContext("被删上下文一", "第一个被级联删除的管理项。");
        Context ctx2 = newContext("被删上下文二", null);
        AdministeredItem ai1 = newAdministeredItem(ctx1, ra);
        AdministeredItem ai2 = newAdministeredItem(ctx2, ra);
        ai1Id = ai1.getId();

        ScopedIdentifier si1 = new ScopedIdentifier("CTX-1", raNs, ai1);
        si1.setVersion("1.0");
        ai1.getScopedIdentifiers().add(si1);
        ScopedIdentifier si2 = new ScopedIdentifier("CTX-2", raNs, ai2);
        si2.setVersion("1.0");
        ai2.getScopedIdentifiers().add(si2);
        scopedIdentifierRepository.saveAll(List.of(si1, si2));
        insertScopedIdentifierSatellites(si1.getId());
        insertScopedIdentifierSatellites(si2.getId());

        // 挂载项（Attached_Item，JOINED 另一支线）
        Context attachedCtx = newContext("挂载项载体", null);
        AttachedItem attached = new AttachedItem(attachedCtx);
        attached.setAdministeredItem(ai1);
        attached = identifiedItemRepository.save(attached);
        ai1.getAttachedItems().add(attached);
        administeredItemRepository.save(ai1);

        // 主管记录（organization = RA）
        StewardshipRecord stewardship = new StewardshipRecord();
        stewardship.setOrganization(ra);
        stewardship.setContact(registrar);
        stewardship = stewardshipRecordRepository.save(stewardship);
        ai1.setStewardshipRecord(stewardship);
        administeredItemRepository.save(ai1);

        // 直属于 RA 组织的普通联系人身份（非 Registrar）
        Individual individual = new Individual();
        individual.setName(Sign.of("普通联系人"));
        individual = individualRepository.save(individual);
        Contact plain = new Contact();
        plain.setOrganization(ra);
        plain.setIndividual(individual);
        plain = contactRepository.save(plain);
        plainContactId = plain.getId();
        bindingService.bind(plain.getId(), admin.getId());

        // ---- 提交记录：专属 SR（删）、共享 SR（留）、ra2 的无关联孤儿 SR（M3：不在候选集，必须原样保留） ----
        SubmissionRecord dedicatedSr = new SubmissionRecord();
        dedicatedSr.setContact(registrar);
        dedicatedSr.setOrganization(ra);
        dedicatedSr.setRegisteredItems(new ArrayList<>(List.of(ai1)));
        submissionRecordRepository.save(dedicatedSr);

        SubmissionRecord sharedSr = new SubmissionRecord();
        sharedSr.setContact(registrar2);
        sharedSr.setOrganization(ra2);
        sharedSr.setRegisteredItems(new ArrayList<>(List.of(ai1, aiB)));
        sharedSr = submissionRecordRepository.save(sharedSr);
        sharedSrId = sharedSr.getId();

        SubmissionRecord orphanSr = new SubmissionRecord();
        orphanSr.setContact(registrar2);
        orphanSr.setOrganization(ra2);
        orphanSr.setRegisteredItems(new ArrayList<>());
        orphanSr = submissionRecordRepository.save(orphanSr);
        orphanSrId = orphanSr.getId();

        // ---- 注册流程：种子规则 + 定制规则（指定审核人）+ 审批记录 ----
        registrationProcessService.ensureSeed(ra.getId());
        RegistrationTransitionRule customRule = new RegistrationTransitionRule();
        customRule.setRegistrationAuthority(ra);
        customRule.setFromStatus(RegistrationStatus.APPLICATION);
        customRule.setToStatus(RegistrationStatus.RETIRED);
        customRule.setDisplayName("定制迁移");
        customRule.setRequiresApproval(true);
        customRule.setApproverMode(ApproverMode.SELECTED_REGISTRARS);
        customRule.getApproverRegistrarIds().add(registrar.getId());
        ruleRepository.save(customRule);

        RegistrationTransitionApproval approval = new RegistrationTransitionApproval();
        approval.setAdministeredItem(ai1);
        approval.setFromStatus(RegistrationStatus.CANDIDATE);
        approval.setToStatus(RegistrationStatus.RECORDED);
        approval.setRegistrar(registrar);
        approval.setAccountId(admin.getId());
        approval.setApprovedAt(LocalDateTime.now());
        approvalRepository.save(approval);

        // ---- 文档语言关联 ----
        ra.getDocumentationLanguageIdentifier().add(zh);
        raRepository.save(ra);

        // 先落库，后续 JDBC 查询/插入才能看到 JPA 写入的数据
        entityManager.flush();

        // ---- 指称/定义 join 子表卫星行 ----
        List<UUID> designationIds = queryUuidList(
                "SELECT id FROM designation WHERE item_id IN (?, ?, ?, ?)",
                ctx1.getId(), ctx2.getId(), attachedCtx.getId(), raNs.getId());
        List<UUID> definitionIds = queryUuidList(
                "SELECT id FROM definition WHERE item_id IN (?, ?, ?, ?)",
                ctx1.getId(), ctx2.getId(), attachedCtx.getId(), raNs.getId());
        UUID contextRef = ctxB.getId();
        for (UUID designationId : designationIds) {
            insert("INSERT INTO designation_context_relation (id, acceptability, context_id, designation_id) VALUES (?, 'PREFERRED', ?, ?)",
                    UUID.randomUUID(), contextRef, designationId);
            insert("INSERT INTO designation_namespace (designation_id, namespace_id) VALUES (?, ?)",
                    designationId, raNsId);
            insert("INSERT INTO designation_naming_convention (designation_id, naming_convention_id) VALUES (?, ?)",
                    designationId, UUID.randomUUID());
        }
        for (UUID definitionId : definitionIds) {
            insert("INSERT INTO definition_context_relation (id, acceptability, context_id, definition_id) VALUES (?, 'PREFERRED', ?, ?)",
                    UUID.randomUUID(), contextRef, definitionId);
        }
        if (!designationIds.isEmpty() && !definitionIds.isEmpty()) {
            insert("INSERT INTO designation_definition_pairing (id, acceptability, context_id, definition_id, designation_id) VALUES (?, 'PREFERRED', ?, ?, ?)",
                    UUID.randomUUID(), contextRef, definitionIds.get(0), designationIds.get(0));
        }

        // ---- B1：指向"被删上下文"的关联行（幸存条目的指称/定义/配对关联到被删 Context；
        //      registry_specification.context 指向被删 Context）——级联删除须一并清理 ----
        List<UUID> survivingDesignationIds = queryUuidList(
                "SELECT id FROM designation WHERE item_id = ?", ctxB.getId());
        List<UUID> survivingDefinitionIds = queryUuidList(
                "SELECT id FROM definition WHERE item_id = ?", ctxB.getId());
        for (UUID designationId : survivingDesignationIds) {
            insert("INSERT INTO designation_context_relation (id, acceptability, context_id, designation_id) VALUES (?, 'PREFERRED', ?, ?)",
                    UUID.randomUUID(), ctx1.getId(), designationId);
        }
        for (UUID definitionId : survivingDefinitionIds) {
            insert("INSERT INTO definition_context_relation (id, acceptability, context_id, definition_id) VALUES (?, 'PREFERRED', ?, ?)",
                    UUID.randomUUID(), ctx1.getId(), definitionId);
        }
        if (!survivingDesignationIds.isEmpty() && !survivingDefinitionIds.isEmpty()) {
            insert("INSERT INTO designation_definition_pairing (id, acceptability, context_id, definition_id, designation_id) VALUES (?, 'PREFERRED', ?, ?, ?)",
                    UUID.randomUUID(), ctx1.getId(), survivingDefinitionIds.get(0), survivingDesignationIds.get(0));
        }
        RegistrySpecification danglingSpec = new RegistrySpecification();
        danglingSpec.setName(Sign.of("悬挂规范"));
        danglingSpec.setCharacterRepertoire("UTF-8");
        danglingSpec.setContext(ctx1);
        registrySpecificationRepository.save(danglingSpec);

        // ---- 命名空间卫星行（EXT） ----
        insert("INSERT INTO namespace_identifier_settings (id, namespace_id, versioning_policy) VALUES (?, ?, 'MAJOR_MINOR')",
                UUID.randomUUID(), raNsId);
        insert("INSERT INTO identifier_strategy_config (is_global_default, version, created_at, effective_from, status, strategy_type, config_json, namespace_id, id)"
                        + " VALUES (false, 0, ?, ?, 'PUBLISHED', 'SEQUENTIAL', '{}', ?, 990001)",
                LocalDateTime.now(), LocalDateTime.now(), raNsId);
        insert("INSERT INTO identifier_sequence_registry (increment_by, pad_length, created_at, current_value, last_allocated_at, start_value, scope_key, namespace_id, id)"
                        + " VALUES (1, 4, ?, 0, ?, 1, 'RA-DEL-TEST', ?, 990001)",
                LocalDateTime.now(), LocalDateTime.now(), raNsId);
        insert("INSERT INTO namespace_permission (permission_type, account_id, id, namespace_id, organization_id) VALUES ('READ_WRITE', ?, ?, ?, ?)",
                admin.getId(), UUID.randomUUID(), raNsId, raId);

        // ---- 其余 item/管理项卫星行（EXT / 联邦 / 映射） ----
        insert("INSERT INTO ai_generation_record (id, administered_item_id, task_id, generated_at, operator, output_kind, raw_output, action_type)"
                        + " VALUES (?, ?, 'task-1', ?, 'tester', 'TEXT', 'raw', 'GENERATE')",
                UUID.randomUUID(), ai1.getId(), LocalDateTime.now());
        insert("INSERT INTO version_history (version_number, changed_at, change_type, item_type, changed_by, item_id, snapshot_json, id)"
                        + " VALUES (1, ?, 'CREATE', 'Context', 'tester', ?, '{}', 990001)",
                LocalDateTime.now(), ctx1.getId());
        insert("INSERT INTO fhir_element_mapping (id, local_item_id, fhir_path, fhir_resource_type, fhir_equivalence, mapping_type, created_at, updated_at, version)"
                        + " VALUES (?, ?, 'Patient.id', 'Patient', 'EQUIVALENT', 'ONE_TO_ONE', ?, ?, 0)",
                UUID.randomUUID(), ai1.getId(), LocalDateTime.now(), LocalDateTime.now());
        UUID dataSetId = UUID.randomUUID();
        insert("INSERT INTO interop_data_set (id, name) VALUES (?, 'interop-ds')", dataSetId);
        insert("INSERT INTO interop_data_element (id, data_set_id, de_identifier, mapped_item_id, created_at, updated_at)"
                        + " VALUES (?, ?, 'DE-TEST-1', ?, ?, ?)",
                UUID.randomUUID(), dataSetId, ai1.getId(), LocalDateTime.now(), LocalDateTime.now());
        UUID nodeId = UUID.randomUUID();
        insert("INSERT INTO registry_node (id, name, rai, enabled, created_at) VALUES (?, 'node-1', 'RAI-1', true, ?)",
                nodeId, LocalDateTime.now());
        insert("INSERT INTO item_link (id, local_item_id, remote_node_id, remote_item_key, established_at) VALUES (?, ?, ?, 'RAI-1:CTX-1', ?)",
                UUID.randomUUID(), ctx1.getId(), nodeId, LocalDateTime.now());
        UUID mrId = UUID.randomUUID();
        insert("INSERT INTO merge_request (id, source_node_id, target_namespace_id, status, created_at) VALUES (?, ?, ?, 'OPEN', ?)",
                mrId, nodeId, sysNsId, LocalDateTime.now());
        insert("INSERT INTO merge_request_item (id, merge_request_id, remote_item_key, change_type, resolution, linked_local_item_id)"
                        + " VALUES (?, ?, 'RAI-1:CTX-1', 'ADD', 'PENDING', ?)",
                UUID.randomUUID(), mrId, ctx1.getId());
        UUID mappingId = UUID.randomUUID();
        insert("INSERT INTO item_mapping (id, degree) VALUES (?, 'same as')", mappingId);
        insert("INSERT INTO item_mapping_subject (id, item_mapping_id, item_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), mappingId, ctx1.getId());
        insert("INSERT INTO item_mapping_object (id, item_mapping_id, item_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), mappingId, ctx1.getId());

        // ---- 汇总"被删 id 集合"供零残留扫描 ----
        List<UUID> itemIds = List.of(ctx1.getId(), ctx2.getId(), attachedCtx.getId(), raNsId);
        List<UUID> regIds = List.of(ai1.getId(), ai2.getId(), attached.getId());
        Set<UUID> itemRef = new LinkedHashSet<>(itemIds);
        itemRef.addAll(regIds);
        deletedIds.put("item_id", itemIds);
        deletedIds.put("itemRefIds", List.copyOf(itemRef));
        deletedIds.put("administered_item_id", List.of(ai1.getId(), ai2.getId()));
        deletedIds.put("registered_item_id", regIds);
        deletedIds.put("identified_item_id",
                List.of(ai1.getId(), ai2.getId(), attached.getId(), nsIdentified.getId()));
        deletedIds.put("registration_authority_id", List.of(raId));
        deletedIds.put("organization_id", List.of(raId));
        deletedIds.put("naming_authority_id", List.of(raId));
        deletedIds.put("contact_id", List.of(registrarId, plainContactId));
        deletedIds.put("registrar_id", List.of(registrarId));
        deletedIds.put("active_registrar_id", List.of(registrarId));
        deletedIds.put("namespace_id", List.of(raNsId));
        deletedIds.put("scoped_identifier_id", List.of(si1.getId(), si2.getId(), nsSi.getId()));
        deletedIds.put("designation_id", designationIds);
        deletedIds.put("definition_id", definitionIds);
        deletedIds.put("stewardship_record_id", List.of(stewardship.getId()));
        deletedIds.put("registration_state_id",
                List.of(ai1.getRegistrationState().getId(), ai2.getRegistrationState().getId()));
        deletedIds.put("rule_id", ruleRepository.findByRegistrationAuthorityId(raId).stream()
                .map(RegistrationTransitionRule::getId).toList());
    }

    private RegistrationAuthority newAuthority(String name) {
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.getName().add(Sign.of(name));
        RegistrationAuthorityIdentifier rai = new RegistrationAuthorityIdentifier();
        rai.setInternationalCodeDesignator("ISO");
        rai.setOrganizationIdentifier("RA-" + UUID.randomUUID().toString().substring(0, 8));
        ra.setRegistrationAuthorityIdentifier(rai);
        return ra;
    }

    private Account newAccount(String username) {
        Account account = new Account();
        account.setUsername(username + "-" + UUID.randomUUID().toString().substring(0, 8));
        account.setPasswordHash("password");
        account.setSysRole(SystemRole.SUPER_ADMIN);
        return accountRepository.save(account);
    }

    private Context newContext(String designation, String definition) {
        Context ctx = new Context();
        Designation d = newDesignation(designation, zh);
        d.setItem(ctx);
        ctx.getDesignations().add(d);
        if (definition != null) {
            Definition def = new Definition();
            def.setText(Text.of(definition));
            def.setLanguage(zh);
            def.setItem(ctx);
            ctx.getDefinitions().add(def);
        }
        return contextRepository.save(ctx);
    }

    private Designation newDesignation(String value, LanguageIdentification language) {
        Designation d = new Designation();
        d.setSign(Sign.of(value));
        d.setLanguage(language);
        return d;
    }

    private AdministeredItem newAdministeredItem(Context ctx, RegistrationAuthority ra) {
        AdministeredItem ai = new AdministeredItem(ctx);
        ai.setCreationDateTime(LocalDateTime.now());
        RegistrationState state = new RegistrationState();
        state.setRegistrationStatus(RegistrationStatus.INCOMPLETE);
        state.setEffectiveDate(LocalDateTime.now());
        ai.setRegistrationState(state);
        ai.setRegistrationAuthority(ra);
        return administeredItemRepository.save(ai);
    }

    private void insertScopedIdentifierSatellites(UUID scopedIdentifierId) {
        insert("INSERT INTO scoped_identifier_lifecycle (id, scoped_identifier_id, identifier_creation_mode, version_creation_mode, version_status, created_at)"
                        + " VALUES (?, ?, 'MANUAL', 'MANUAL', 'DRAFT', ?)",
                UUID.randomUUID(), scopedIdentifierId, LocalDateTime.now());
        insert("INSERT INTO identifier_version_history (id, scoped_identifier_id, version, version_status, operator, timestamp)"
                        + " VALUES (?, ?, '1.0', 'DRAFT', 'tester', ?)",
                UUID.randomUUID(), scopedIdentifierId, LocalDateTime.now());
    }

    private void insert(String sql, Object... args) {
        Object[] converted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            converted[i] = args[i] instanceof UUID uuid
                    ? databaseEnvironment.toJdbcUuid(uuid)
                    : args[i];
        }
        jdbcTemplate.update(sql, converted);
    }

    private List<UUID> queryUuidList(String sql, UUID... args) {
        Object[] converted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            converted[i] = databaseEnvironment.toJdbcUuid(args[i]);
        }
        return jdbcTemplate.query(sql, (rs, rowNum) -> DatabaseEnvironment.toUuid(rs.getObject(1)), converted);
    }

    // ---------- 测试 ----------

    @Test
    @DisplayName("删除预览：统计级联删除将波及的各类数据量")
    void preview_returnsCounts() {
        RegistrationAuthorityDeletionPreview preview = deletionService.preview(raId);

        assertThat(preview.registrationAuthorityId()).isEqualTo(raId);
        assertThat(preview.administeredItemCount()).isEqualTo(2);
        assertThat(preview.registrarCount()).isEqualTo(1);
        assertThat(preview.namespaceCount()).isEqualTo(1);
        assertThat(preview.stewardshipRecordCount()).isEqualTo(1);
        // M3：仅"专属 SR"计入——候选集 = join 行触及的 SR；ra2 的无关联孤儿 SR 不在候选集
        assertThat(preview.submissionRecordCount()).isEqualTo(1);
        assertThat(preview.transitionRuleCount())
                .isEqualTo(ruleRepository.findByRegistrationAuthorityId(raId).size());
    }

    @Test
    @DisplayName("删除预览：仅 SUPER_ADMIN 或 REGISTRAR 业务角色可查看（M4）")
    void preview_roleGate() {
        // SUBMITTER（无任何 SUPER_ADMIN/REGISTRAR authority）→ 拒绝
        authenticateAs(null, "ROLE_SUBMITTER");
        assertThatThrownBy(() -> deletionService.preview(raId))
                .isInstanceOf(io.github.matrixlane.metadataregistry.web.common.BusinessException.class)
                .hasMessageContaining("仅超级管理员或注册人员");

        // REGISTRAR 业务角色 → 允许
        authenticateAs(null, "ROLE_REGISTRAR");
        assertThat(deletionService.preview(raId).administeredItemCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("删除预览：机构不存在时抛 404")
    void preview_notFound() {
        assertThatThrownBy(() -> deletionService.preview(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("级联删除：全表零残留，幸存数据与 sys: 命名空间不受影响")
    void delete_cascadesWithZeroResidue() {
        deletionService.delete(raId);
        // 删除经原生 JDBC 完成，清 JPA 一级缓存后再做实体级断言
        entityManager.clear();

        // 注册机构与组织行删除
        assertThat(raRepository.findById(raId)).isEmpty();
        // sys: 命名空间保留且 NamingAuthority 已置空
        Namespace sysNs = namespaceRepository.findById(sysNsId).orElseThrow();
        assertThat(sysNs.getNamingAuthority()).isNull();
        // 共享提交记录保留（仅剩幸存项关联）；ra2 的无关联孤儿 SR 原样保留（M3）
        assertThat(submissionRecordRepository.findById(sharedSrId)).isPresent();
        assertThat(submissionRecordRepository.findById(orphanSrId)).isPresent();
        assertThat(submissionRecordRepository.findAllByRegisteredItemId(otherItemAiId)).hasSize(1);
        // 账号激活指向已清理、Registrar 绑定已解绑
        Account admin = accountRepository.findById(adminAccountId).orElseThrow();
        assertThat(admin.getActiveRegistrarId()).isNull();
        assertThat(bindingService.registrarsOfAccount(adminAccountId)).isEmpty();
        // 幸存机构及其管理项不受影响（用 JDBC 计数断言，避免 JOINED 多态大 join 触碰 SQLite 64 表上限）
        assertThat(raRepository.findById(ra2Id)).isPresent();
        assertThat(countRows("administered_item", "id", otherItemAiId)).isEqualTo(1);

        // 主干表显式断言零行
        assertZeroRows("registration_authority", "id", deletedIds.get("registration_authority_id"));
        assertZeroRows("organization", "id", deletedIds.get("organization_id"));
        assertZeroRows("item", "id", deletedIds.get("item_id"));
        assertZeroRows("administered_item", "id", deletedIds.get("administered_item_id"));
        assertZeroRows("registered_item", "id", deletedIds.get("registered_item_id"));
        assertZeroRows("identified_item", "id", deletedIds.get("identified_item_id"));
        assertZeroRows("namespace", "id", deletedIds.get("namespace_id"));
        assertZeroRows("registrar", "id", deletedIds.get("registrar_id"));
        assertZeroRows("contact", "id", deletedIds.get("contact_id"));

        // 通用零残留扫描：sqlite schema 中所有含 item 指向列、context 指向列或 RA 指向列的表
        Map<String, String> columnToIdSet = Map.ofEntries(
                Map.entry("item_id", "item_id"),
                Map.entry("context_id", "item_id"),
                Map.entry("local_item_id", "itemRefIds"),
                Map.entry("previous_local_item_id", "itemRefIds"),
                Map.entry("linked_local_item_id", "itemRefIds"),
                Map.entry("mapped_item_id", "itemRefIds"),
                Map.entry("administered_item_id", "administered_item_id"),
                Map.entry("registered_item_id", "registered_item_id"),
                Map.entry("identified_item_id", "identified_item_id"),
                Map.entry("registration_authority_id", "registration_authority_id"),
                Map.entry("organization_id", "organization_id"),
                Map.entry("naming_authority_id", "naming_authority_id"),
                Map.entry("contact_id", "contact_id"),
                Map.entry("registrar_id", "registrar_id"),
                Map.entry("active_registrar_id", "active_registrar_id"),
                Map.entry("namespace_id", "namespace_id"),
                Map.entry("scoped_identifier_id", "scoped_identifier_id"),
                Map.entry("designation_id", "designation_id"),
                Map.entry("definition_id", "definition_id"),
                Map.entry("stewardship_record_id", "stewardship_record_id"),
                Map.entry("registration_state_id", "registration_state_id"),
                Map.entry("rule_id", "rule_id"));
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'", String.class);
        for (String table : tables) {
            List<String> columns = jdbcTemplate.queryForList("SELECT name FROM pragma_table_info(?)",
                    String.class, table);
            for (Map.Entry<String, String> mapping : columnToIdSet.entrySet()) {
                if (columns.contains(mapping.getKey())) {
                    assertZeroRows(table, mapping.getKey(), deletedIds.get(mapping.getValue()));
                }
            }
        }
    }

    @Test
    @DisplayName("级联删除：机构不存在时抛 404")
    void delete_notFound() {
        assertThatThrownBy(() -> deletionService.delete(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("治理分支：个人版主要注册机构拒绝删除，非主要机构经服务层级联删除")
    void serviceDelete_personalEditionPrimaryProtection() {
        systemSettingService.setEdition(DeploymentEdition.PERSONAL);

        RegistrationAuthority primary = newAuthority("主要机构");
        primary.setPrimary(true);
        primary = raRepository.save(primary);
        registrationProcessService.ensureSeed(primary.getId());
        UUID primaryId = primary.getId();

        assertThatThrownBy(() -> registrationAuthorityService.delete(primaryId, SystemRole.SUPER_ADMIN))
                .isInstanceOf(io.github.matrixlane.metadataregistry.web.common.BusinessException.class)
                .hasMessageContaining("主要注册机构不允许删除");
        assertThat(raRepository.findById(primaryId)).isPresent();

        // 个人版非主要注册机构：允许级联删除
        registrationAuthorityService.delete(raId, SystemRole.SUPER_ADMIN);
        entityManager.clear();
        assertThat(raRepository.findById(raId)).isEmpty();
    }

    @Test
    @DisplayName("M2：null-RA 的 Registrar 历史行随机构级联删除，registrar/contact 子表零孤儿")
    void delete_nullRaRegistrarRow_cascadedWithoutOrphans() {
        // 模拟历史/边缘数据：organization = RA 但 registration_authority 为 null 的
        // Registrar 行（createMyContact(REGISTRAR) 选普通组织产生的僵尸身份口径）
        RegistrationAuthority ra = raRepository.findById(raId).orElseThrow();
        Registrar zombie = new Registrar();
        zombie.setOrganization(ra);
        zombie.setRegistrationAuthority(null);
        zombie = registrarRepository.save(zombie);
        entityManager.flush();

        // 预览计数与该口径一致（既有 Registrar + null-RA 历史行）
        assertThat(deletionService.preview(raId).registrarCount()).isEqualTo(2);

        deletionService.delete(raId);
        entityManager.clear();

        // registrar JOINED 子表与 contact 父表均无残留
        assertThat(countRows("registrar", "id", zombie.getId())).isZero();
        assertThat(countRows("contact", "id", zombie.getId())).isZero();
        assertThat(countRows("registrar", "id", registrarId)).isZero();
        assertThat(countRows("contact", "id", registrarId)).isZero();
    }

    @Test
    @DisplayName("M2：createMyContact(REGISTRAR) 以注册机构为组织时挂接 registration_authority")
    void createMyContact_registrarWithRaOrganization_bindsRa() {
        Account admin = accountRepository.findById(adminAccountId).orElseThrow();
        MyContactRequest req = new MyContactRequest(raId, null, null,
                io.github.matrixlane.metadataregistry.web.dto.contact.ContactIdentityKind.REGISTRAR);

        contactService.createMyContact(admin.getId(), req);
        entityManager.flush();

        // organization_id 列在 contact 父表（JOINED），registrar 身份经子表判定
        List<UUID> registrarIds = jdbcTemplate.query(
                "SELECT c.id FROM contact c WHERE c.organization_id = ?"
                        + " AND c.id IN (SELECT id FROM registrar)",
                (rs, rowNum) -> DatabaseEnvironment.toUuid(rs.getObject(1)),
                databaseEnvironment.toJdbcUuid(raId));
        assertThat(registrarIds).hasSize(2);
        Long nullRaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registrar r JOIN contact c ON c.id = r.id"
                        + " WHERE c.organization_id = ? AND r.registration_authority_id IS NULL",
                Long.class, databaseEnvironment.toJdbcUuid(raId));
        assertThat(nullRaCount).isZero();
    }

    @Test
    @DisplayName("B1：Registry Context 随承载 RA 级联删除（context 行消失、spec.context 置空），启动调和补建并回填")
    void delete_raCarryingRegistryContext_contextRebuiltByBackfill() {
        // 1. 造 Registry Context（固定 id）挂在被删 RA 下
        RegistrationAuthority ra = raRepository.findById(raId).orElseThrow();
        Namespace ns = namespaceRepository.findById(raNsId).orElseThrow();
        ra.setNamespace(ns);
        raRepository.save(ra);
        // raNs 标识策略置 MANUAL_ONLY：ensureRegistryContext 走固定标识 "registry-context"
        insert("UPDATE namespace_identifier_settings SET identifier_input_mode = 'MANUAL_ONLY' WHERE namespace_id = ?",
                raNsId);
        Context registryContext = registryContextBootstrapService.ensureRegistryContext(ra);
        assertThat(registryContext.getId()).isEqualTo(RegistryContextBootstrapService.REGISTRY_CONTEXT_ID);
        entityManager.flush();
        // Item 联合继承整图加载会超出 SQLite 64 表上限——AI 归属一律走 JDBC 逐表查询
        UUID registryContextAiId = jdbcTemplate.queryForObject(
                "SELECT ai.id FROM administered_item ai JOIN identified_item ii ON ii.id = ai.id"
                        + " WHERE ii.item_id = ?",
                (rs, rowNum) -> DatabaseEnvironment.toUuid(rs.getObject(1)),
                databaseEnvironment.toJdbcUuid(RegistryContextBootstrapService.REGISTRY_CONTEXT_ID));

        // 2. 删除承载 RA：context 子表行消失、item 行消失、registry_specification.context_id 置空
        deletionService.delete(raId);
        entityManager.clear();
        assertThat(countRows("context", "id", RegistryContextBootstrapService.REGISTRY_CONTEXT_ID)).isZero();
        assertThat(countRows("item", "id", RegistryContextBootstrapService.REGISTRY_CONTEXT_ID)).isZero();
        assertThat(countRows("administered_item", "id", registryContextAiId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registry_specification WHERE context_id IS NOT NULL", Long.class))
                .isZero();
        assertThat(raRepository.findById(ra2Id)).isPresent();

        // 3. 启动调和：幸存 RA 先补建自有命名空间；Registry Context 补建到其下并回填 spec.context
        RegistrationAuthority ra2 = raRepository.findById(ra2Id).orElseThrow();
        Namespace ra2Ns = new Namespace();
        ra2Ns.setShorthandPrefix("ra2-ns");
        ra2Ns.setSchemeReference(Sign.of("urn:test:ra2"));
        ra2Ns.setRegistrationAuthority(ra2);
        ra2Ns = namespaceRepository.save(ra2Ns);
        ra2.setNamespace(ra2Ns);
        raRepository.save(ra2);
        insert("UPDATE namespace_identifier_settings SET identifier_input_mode = 'MANUAL_ONLY' WHERE namespace_id = ?",
                ra2Ns.getId());
        entityManager.flush();
        systemSettingService.setInitialized(true);

        registryContextBootstrapService.backfillIfInitialized();
        // backfill 内的 ensureRegistryContext 经 JPA 保存的 AI 行在提交前仍处于待 flush 状态；
        // 显式 flush 后再 clear，避免 pending 插入被丢弃（生产路径为独立事务提交）
        entityManager.flush();
        entityManager.clear();

        // 4. Registry Context 重建且归属幸存 RA；spec.context 回填为新 Registry Context（JDBC 断言）
        assertThat(countRows("context", "id", RegistryContextBootstrapService.REGISTRY_CONTEXT_ID)).isEqualTo(1);
        assertThat(countRows("item", "id", RegistryContextBootstrapService.REGISTRY_CONTEXT_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM administered_item ai JOIN identified_item ii ON ii.id = ai.id"
                        + " WHERE ii.item_id = ? AND ai.registration_authority_id = ?",
                Long.class,
                databaseEnvironment.toJdbcUuid(RegistryContextBootstrapService.REGISTRY_CONTEXT_ID),
                databaseEnvironment.toJdbcUuid(ra2Id))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registry_specification WHERE name = '悬挂规范' AND context_id = ?",
                Long.class, databaseEnvironment.toJdbcUuid(RegistryContextBootstrapService.REGISTRY_CONTEXT_ID)))
                .isEqualTo(1);
    }

    private void assertZeroRows(String table, String column, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (int i = 0; i < ids.size(); i += 100) {
            List<UUID> chunk = ids.subList(i, Math.min(i + 100, ids.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
            Object[] args = chunk.stream().map(databaseEnvironment::toJdbcUuid).toArray();
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM \"" + table + "\" WHERE \"" + column + "\" IN (" + placeholders + ")",
                    Long.class, args);
            assertThat(count)
                    .as("表 %s 列 %s 不应残留指向被删注册机构数据的行", table, column)
                    .isZero();
        }
    }

    private long countRows(String table, String column, UUID id) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"" + table + "\" WHERE \"" + column + "\" = ?",
                Long.class, databaseEnvironment.toJdbcUuid(id));
        return count == null ? 0 : count;
    }
}
