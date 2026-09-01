package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfig;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.config.IdentifierStrategyLoader;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.PreviewResponse;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierCreateRequest;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierResponse;
import io.github.matrixlane.metadataregistry.web.repository.DataElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.IdentifiedItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierVersionHistoryRepository;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierStrategyConfigRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.repository.SequenceRegistryRepository;
import io.github.matrixlane.metadataregistry.web.service.identifier.IdentifierStrategyAdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end segment template strategy tests: fixed-prefix + object
 * abbreviation + sequence generation through the creation entry, implicit
 * per-object-type sequence scopes, backfill alignment, namespace-agnostic
 * preview, timestamp-collision retry, and the external-namespace gate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        // 取消用户主目录 application-local.yml 导入并显式锁定内存 SQLite,
        // 使本测试与宿主机数据源配置无关(导入的配置优先级高于非 profile 默认值)。
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:segment-template-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main"
})
@ActiveProfiles("test")
class SegmentTemplateStrategyIntegrationTest {

    /** WN.DE.00001 风格模板；未映射条目类型（如裸 IdentifiedItem）走 default=MD。 */
    private static final String WN_TEMPLATE_JSON = """
            {"segments":[
              {"type":"LITERAL","params":{"value":"WN"}},
              {"type":"SEPARATOR","params":{"char":"."}},
              {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{"default":"MD"}},
              {"type":"SEPARATOR","params":{"char":"."}},
              {"type":"SEQUENCE","name":"seq","params":{"start":1,"increment":1,"pad":5,"overflow":"REJECT"}}
            ]}""";

    @Autowired
    private ScopedIdentifierService scopedIdentifierService;
    @Autowired
    private SequenceRegistryService sequenceRegistryService;
    @Autowired
    private IdentifierStrategyAdminService identifierStrategyAdminService;
    @Autowired
    private NamespaceRepository namespaceRepository;
    @Autowired
    private IdentifiedItemRepository identifiedItemRepository;
    @Autowired
    private DataElementRepository dataElementRepository;
    @Autowired
    private RegistrationAuthorityRepository registrationAuthorityRepository;
    @Autowired
    private RegistrarRepository registrarRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private IdentifierStrategyConfigRepository configRepository;
    @Autowired
    private IdentifierStrategyLoader strategyLoader;
    @Autowired
    private ScopedIdentifierRepository scopedIdentifierRepository;
    @Autowired
    private IdentifierVersionHistoryRepository historyRepository;
    @Autowired
    private SequenceRegistryRepository sequenceRegistryRepository;

    private RegistrationAuthority registrationAuthority;
    private Account account;
    private Registrar registrar;
    private Namespace ownNamespace;
    private Namespace externalNamespace;
    private DataElement dataElement;
    private IdentifiedItem dataElementItem;
    private IdentifiedItem bareItem;
    private int templateVersion;

    // 不加 @Transactional：避免测试管理事务持有 SQLite 写锁（同并发用例的教训）。
    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        RegistrationAuthority ra = new RegistrationAuthority();
        RegistrationAuthorityIdentifier rai = new RegistrationAuthorityIdentifier();
        rai.setInternationalCodeDesignator("0000");
        rai.setOrganizationIdentifier("RA-TEST");
        ra.setRegistrationAuthorityIdentifier(rai);
        registrationAuthority = registrationAuthorityRepository.save(ra);

        account = new Account();
        account.setUsername("it-registrar-" + suffix);
        account.setPasswordHash("x");
        account = accountRepository.save(account);

        registrar = new Registrar(registrationAuthority);
        registrar.setIdentifier("registrar-" + suffix);
        registrar.setAccount(account);
        registrar = registrarRepository.save(registrar);

        ownNamespace = new Namespace();
        ownNamespace.setRegistrationAuthority(registrationAuthority);
        ownNamespace = namespaceRepository.save(ownNamespace);

        externalNamespace = namespaceRepository.save(new Namespace());

        dataElement = dataElementRepository.save(new DataElement());
        dataElementItem = identifiedItemRepository.save(new IdentifiedItem(dataElement));
        bareItem = identifiedItemRepository.save(new IdentifiedItem());

        // 活动 Registrar 会话：自有/外部判定的主判定链
        AuthenticatedUser principal = new AuthenticatedUser(account.getId(), account.getUsername(), SystemRole.SUPER_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        templateVersion = 0;
        publishTemplate(ownNamespace, WN_TEMPLATE_JSON);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        // 测试库是跨类共享的文件库：本类的提交数据必须按外键序清理干净，
        // 否则残留的注册机构会破坏其他测试类的前置条件（如命名空间门槛）。
        cleanup();
        strategyLoader.refreshRegistry();
    }

    private void cleanup() {
        for (Namespace ns : List.of(ownNamespace, externalNamespace)) {
            for (var si : scopedIdentifierRepository.findByNamespaceId(ns.getId())) {
                historyRepository.deleteAll(
                        historyRepository.findByScopedIdentifierIdOrderByTimestampDesc(si.getId()));
                scopedIdentifierRepository.delete(si);
            }
            configRepository.deleteAll(
                    configRepository.findByNamespaceIdOrderByVersionDesc(ns.getId()));
            sequenceRegistryRepository.deleteByNamespaceId(ns.getId());
        }
        registrarRepository.delete(registrar);
        namespaceRepository.deleteAll(List.of(ownNamespace, externalNamespace));
        // 批量删除：逐条 delete 会先 merge 加载实体，EAGER item 关联的
        // 联合子类整图 join 会超出 SQLite 单条 SQL 64 表上限
        identifiedItemRepository.deleteAllByIdIn(List.of(dataElementItem.getId(), bareItem.getId()));
        dataElementRepository.delete(dataElement);
        accountRepository.delete(account);
        registrationAuthorityRepository.delete(registrationAuthority);
    }

    /** 发布模板：先停用既有 PUBLISHED 配置，再写入新版本并刷新运行时注册表。 */
    private void publishTemplate(Namespace namespace, String templateJson) {
        configRepository.findByNamespaceIdOrderByVersionDesc(namespace.getId()).stream()
                .filter(c -> c.getStatus() == IdentifierStrategyConfig.StrategyStatus.PUBLISHED)
                .forEach(c -> {
                    c.disable();
                    configRepository.save(c);
                });

        IdentifierStrategyConfig config = new IdentifierStrategyConfig("SEGMENT_TEMPLATE");
        config.setNamespace(namespace);
        config.setConfigJson(templateJson);
        config.publish();
        config.setIsGlobalDefault(false);
        config.setVersion(++templateVersion);
        config.setCreatedAt(LocalDateTime.now());
        configRepository.save(config);
        strategyLoader.refreshRegistry();
    }

    private ScopedIdentifierCreateRequest createRequest(Namespace namespace, String identifier,
                                                        IdentifiedItem item) {
        return new ScopedIdentifierCreateRequest(
                namespace.getId(), identifier, "1.0.0", item.getId(), "segment test", null);
    }

    @Test
    void ownNamespace_generatesPerObjectTypeScopedSequence() {
        ScopedIdentifierResponse de1 = scopedIdentifierService.create(
                createRequest(ownNamespace, null, dataElementItem));
        assertThat(de1.identifier()).isEqualTo("WN.DE.00001");

        ScopedIdentifierResponse de2 = scopedIdentifierService.create(
                createRequest(ownNamespace, null, dataElementItem));
        assertThat(de2.identifier()).isEqualTo("WN.DE.00002");

        // 不同对象类型独立计数：裸 IdentifiedItem 未命中系统映射表，走 default=MD
        ScopedIdentifierResponse md1 = scopedIdentifierService.create(
                createRequest(ownNamespace, null, bareItem));
        assertThat(md1.identifier()).isEqualTo("WN.MD.00001");
    }

    @Test
    void missingRequiredVariable_rejected() {
        publishTemplate(ownNamespace, """
                {"segments":[
                  {"type":"VARIABLE","name":"deptCode","params":{"label":"科室代码","pattern":"\\\\d{2}","required":true}},
                  {"type":"SEPARATOR","params":{"char":"-"}},
                  {"type":"SEQUENCE","name":"seq","params":{"start":1,"increment":1,"pad":4,"overflow":"REJECT"}}
                ]}""");

        assertThatThrownBy(() -> scopedIdentifierService.create(
                createRequest(ownNamespace, null, dataElementItem)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deptCode");
    }

    @Test
    void backfillAlignsCounterAboveImportedMaximum() {
        // 模拟导入 WN.DE.00001 .. WN.DE.00026 后回填 DE 作用域计数器
        sequenceRegistryService.alignCounter(ownNamespace, "DE", 26, 1, 1, 5);

        ScopedIdentifierResponse next = scopedIdentifierService.create(
                createRequest(ownNamespace, null, dataElementItem));
        assertThat(next.identifier()).isEqualTo("WN.DE.00027");
    }

    @Test
    void namespaceAgnosticPreview_matchesRuntimeWithoutAllocation() {
        long allocationsBefore = allocationCountOf(ownNamespace);

        PreviewResponse preview = identifierStrategyAdminService.preview(WN_TEMPLATE_JSON, "DataElement", null);

        assertThat(preview.validationErrors()).isEmpty();
        assertThat(preview.sample()).isEqualTo("WN.DE.00001");
        assertThat(preview.formatRegex()).isNotBlank();
        // 预览不产生任何序号分配
        assertThat(allocationCountOf(ownNamespace)).isEqualTo(allocationsBefore);
    }

    private long allocationCountOf(Namespace namespace) {
        return sequenceRegistryRepository.findAll().stream()
                .filter(r -> r.getNamespace() != null && namespace.getId().equals(r.getNamespace().getId()))
                .count();
    }

    @Test
    void timestampTemplate_generatedIdentifiersUnique() {
        publishTemplate(ownNamespace, """
                {"segments":[
                  {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{"default":"MD"}},
                  {"type":"SEPARATOR","params":{"char":"."}},
                  {"type":"TIMESTAMP","name":"ts","params":{"pattern":"yyyyMMddHHmmssSSS"}}
                ]}""");

        TreeSet<String> identifiers = new TreeSet<>();
        for (int i = 0; i < 5; i++) {
            identifiers.add(scopedIdentifierService
                    .create(createRequest(ownNamespace, null, dataElementItem))
                    .identifier());
        }
        // 同一毫秒撞库时由有界重试重新生成，最终全部唯一
        assertThat(identifiers).hasSize(5);
        assertThat(identifiers.first()).startsWith("DE.");
    }

    @Test
    void manualDuplicateIdentifier_rejectedWithoutRetry() {
        // 手动标识须符合已发布模板格式（WN.<缩写>.<五位序号>）
        scopedIdentifierService.create(createRequest(ownNamespace, "WN.DE.99999", dataElementItem));

        assertThatThrownBy(() -> scopedIdentifierService.create(
                createRequest(ownNamespace, "WN.DE.99999", dataElementItem)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void externalNamespace_blankIdentifierRejected() {
        assertThatThrownBy(() -> scopedIdentifierService.create(
                createRequest(externalNamespace, null, dataElementItem)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("外部命名空间");
    }

    @Test
    void externalNamespace_manualIdentifierAccepted() {
        ScopedIdentifierResponse created = scopedIdentifierService.create(
                new ScopedIdentifierCreateRequest(externalNamespace.getId(), "EXT-DE-00001",
                        "1.0.0", dataElementItem.getId(), "segment test", null));
        assertThat(created.identifier()).isEqualTo("EXT-DE-00001");
    }

    @Test
    void concurrentScopedAllocation_countsIndependentlyAndAtomically() throws Exception {
        int perScope = 8;
        ExecutorService executor = Executors.newFixedThreadPool(perScope * 2);
        CountDownLatch latch = new CountDownLatch(perScope * 2);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < perScope * 2; i++) {
            final String scope = i < perScope ? "DE" : "VD";
            futures.add(executor.submit(() -> {
                latch.countDown();
                latch.await();
                return allocateWithLockRetry(scope);
            }));
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(120, TimeUnit.SECONDS)).isTrue();

        TreeSet<Long> scope1 = new TreeSet<>();
        TreeSet<Long> scope2 = new TreeSet<>();
        for (int i = 0; i < futures.size(); i++) {
            Long value = futures.get(i).get(1, TimeUnit.SECONDS);
            (i < perScope ? scope1 : scope2).add(value);
        }
        // 每个作用域各自从 1 连续分配，无重复无跳号
        assertThat(scope1).hasSize(perScope);
        assertThat(scope1.first()).isEqualTo(1L);
        assertThat(scope1.last()).isEqualTo((long) perScope);
        assertThat(scope2).hasSize(perScope);
        assertThat(scope2.first()).isEqualTo(1L);
        assertThat(scope2.last()).isEqualTo((long) perScope);
    }

    /** SQLite 单写者模型的锁升级重试（与并发创建用例同理）。 */
    private long allocateWithLockRetry(String scope) {
        final int maxAttempts = 20;
        for (int attempt = 1; ; attempt++) {
            try {
                return sequenceRegistryService.getNextValue(ownNamespace, scope, 1, 1, 5);
            } catch (CannotAcquireLockException | UncategorizedSQLException e) {
                if (attempt >= maxAttempts) {
                    throw e;
                }
                try {
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
