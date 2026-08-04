package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierStrategyConfig;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTemplate;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.config.IdentifierStrategyLoader;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierCreateRequest;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierResponse;
import io.github.matrixlane.metadataregistry.web.repository.IdentifiedItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierVersionHistoryRepository;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierStrategyConfigRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.repository.SequenceRegistryRepository;
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
import java.util.Map;
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
 * End-to-end segment template strategy tests: WS/T 363 generation through the
 * creation entry, scoped sequence allocation (incl. concurrency), backfill
 * alignment, and the external-namespace generation gate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class SegmentTemplateStrategyIntegrationTest {

    @Autowired
    private ScopedIdentifierService scopedIdentifierService;
    @Autowired
    private SequenceRegistryService sequenceRegistryService;
    @Autowired
    private NamespaceRepository namespaceRepository;
    @Autowired
    private IdentifiedItemRepository identifiedItemRepository;
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
    private IdentifiedItem identifiedItem;

    // 不加 @Transactional：避免测试管理事务持有 SQLite 写锁（同并发用例的教训）。
    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        registrationAuthority = registrationAuthorityRepository.save(new RegistrationAuthority());

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

        identifiedItem = identifiedItemRepository.save(new IdentifiedItem());

        // 活动 Registrar 会话：自有/外部判定的主判定链
        AuthenticatedUser principal = new AuthenticatedUser(account.getId(), account.getUsername(), SystemRole.SUPER_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        publishWst363Template(ownNamespace);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        // 测试库是跨类共享的文件库：本类的提交数据必须按外键序清理干净，
        // 否则残留的注册机构会破坏其他测试类的前置条件（如命名空间门槛）。
        // 仓库方法各自带事务，直接按序调用即可（DataSourceTransactionManager
        // 与 JPA EntityManager 不兼容，不能套用）。
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
        identifiedItemRepository.delete(identifiedItem);
        accountRepository.delete(account);
        registrationAuthorityRepository.delete(registrationAuthority);
    }

    private void publishWst363Template(Namespace namespace) {
        SegmentTemplate template = SegmentTemplate.fromJson("""
                {"segments":[
                  {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{"mapping":{"IdentifiedItem":"DE","DataElement":"DE","ValueDomain":"CV"}}},
                  {"type":"VARIABLE","name":"majorCategory","params":{"label":"大类编码","pattern":"\\\\d{2}","required":true}},
                  {"type":"SEPARATOR","params":{"char":"."}},
                  {"type":"VARIABLE","name":"minorCategory","params":{"label":"小类编码","pattern":"\\\\d{2}","required":true}},
                  {"type":"SEPARATOR","params":{"char":"."}},
                  {"type":"SEQUENCE","name":"seq","params":{"start":1,"increment":1,"pad":3,"scopeRefs":["majorCategory","minorCategory"],"overflow":"REJECT"}},
                  {"type":"SEPARATOR","params":{"char":"."}},
                  {"type":"VARIABLE","name":"suffixCode","params":{"label":"附加码","pattern":"\\\\d{2}","default":"00","required":false}}
                ]}""");

        IdentifierStrategyConfig config = new IdentifierStrategyConfig("SEGMENT_TEMPLATE");
        config.setNamespace(namespace);
        config.setConfigJson(template.toJson());
        config.publish();
        config.setIsGlobalDefault(false);
        config.setVersion(1);
        config.setCreatedAt(LocalDateTime.now());
        configRepository.save(config);
        strategyLoader.refreshRegistry();
    }

    private ScopedIdentifierCreateRequest createRequest(Namespace namespace, String identifier,
                                                        String major, String minor) {
        Map<String, String> variables = major == null ? null
                : Map.of("majorCategory", major, "minorCategory", minor);
        return new ScopedIdentifierCreateRequest(
                namespace.getId(), identifier, "1.0.0", identifiedItem.getId(), "segment test", variables);
    }

    @Test
    void ownNamespace_generatesWst363IdentifierWithScopedSequence() {
        ScopedIdentifierResponse first = scopedIdentifierService.create(
                createRequest(ownNamespace, null, "02", "01"));
        assertThat(first.identifier()).isEqualTo("DE02.01.001.00");

        ScopedIdentifierResponse second = scopedIdentifierService.create(
                createRequest(ownNamespace, null, "02", "01"));
        assertThat(second.identifier()).isEqualTo("DE02.01.002.00");

        // 不同小类独立计数
        ScopedIdentifierResponse otherScope = scopedIdentifierService.create(
                createRequest(ownNamespace, null, "02", "10"));
        assertThat(otherScope.identifier()).isEqualTo("DE02.10.001.00");
    }

    @Test
    void missingRequiredVariable_rejected() {
        assertThatThrownBy(() -> scopedIdentifierService.create(
                new ScopedIdentifierCreateRequest(ownNamespace.getId(), null, "1.0.0",
                        identifiedItem.getId(), "segment test", Map.of("majorCategory", "02"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("小类编码");
    }

    @Test
    void backfillAlignsCounterAboveImportedMaximum() {
        // 模拟导入 DE03.01.001.00 .. DE03.01.026.00 后回填计数器
        sequenceRegistryService.alignCounter(ownNamespace, "03.01", 26, 1, 1, 3);

        ScopedIdentifierResponse next = scopedIdentifierService.create(
                createRequest(ownNamespace, null, "03", "01"));
        assertThat(next.identifier()).isEqualTo("DE03.01.027.00");
    }

    @Test
    void externalNamespace_blankIdentifierRejected() {
        assertThatThrownBy(() -> scopedIdentifierService.create(
                createRequest(externalNamespace, null, "02", "01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("外部命名空间");
    }

    @Test
    void externalNamespace_manualIdentifierAccepted() {
        ScopedIdentifierResponse created = scopedIdentifierService.create(
                new ScopedIdentifierCreateRequest(externalNamespace.getId(), "EXT-DE01.00.001.00",
                        "1.0.0", identifiedItem.getId(), "segment test", null));
        assertThat(created.identifier()).isEqualTo("EXT-DE01.00.001.00");
    }

    @Test
    void concurrentScopedAllocation_countsIndependentlyAndAtomically() throws Exception {
        int perScope = 8;
        ExecutorService executor = Executors.newFixedThreadPool(perScope * 2);
        CountDownLatch latch = new CountDownLatch(perScope * 2);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < perScope * 2; i++) {
            final String scope = i < perScope ? "09.01" : "09.02";
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
                return sequenceRegistryService.getNextValue(ownNamespace, scope, 1, 1, 3);
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
