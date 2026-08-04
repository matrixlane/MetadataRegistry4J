package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierCreateRequest;
import io.github.matrixlane.metadataregistry.web.repository.IdentifiedItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ScopedIdentifierConcurrencyIntegrationTest {

    @Autowired
    private ScopedIdentifierService scopedIdentifierService;

    @Autowired
    private ScopedIdentifierRepository scopedIdentifierRepository;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private IdentifiedItemRepository identifiedItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private javax.sql.DataSource dataSource;

    private UUID namespaceId;
    private UUID identifiedItemId;
    private static final String CONCURRENT_IDENTIFIER = "concurrent-test-id";

    // 不加 @Transactional：Spring 测试管理的事务会把 setUp 的写操作挂起在整个
    // 测试期间，主连接持有 SQLite 写锁，工作线程的读→写升级会立即 SQLITE_BUSY
    // （busy_timeout 对锁升级路径不生效）。让 setUp 各自提交释放锁。
    @BeforeEach
    void setUp() {
        Namespace namespace = new Namespace();
        namespace = namespaceRepository.save(namespace);
        namespaceId = namespace.getId();

        IdentifiedItem identifiedItem = new IdentifiedItem();
        identifiedItem = identifiedItemRepository.save(identifiedItem);
        identifiedItemId = identifiedItem.getId();
    }

    @AfterEach
    void tearDown() {
        // Delete version history first due to FK constraint.
        // NOTE: the pool is configured with auto-commit=false (application-local.yml)
        // while Hibernate assumes provider_disables_autocommit, so bare JdbcTemplate
        // updates would stay uncommitted and be rolled back on connection return.
        // Run cleanup inside an explicit transaction to be independent of that.
        new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource))
                .executeWithoutResult(status -> {
                    jdbcTemplate.update("DELETE FROM identifier_version_history WHERE scoped_identifier_id IN (SELECT id FROM scoped_identifier WHERE identifier = ?)", CONCURRENT_IDENTIFIER);
                    jdbcTemplate.update("DELETE FROM identifier_version_history WHERE scoped_identifier_id IN (SELECT id FROM scoped_identifier WHERE identifier LIKE 'concurrent-unique-%')");
                    jdbcTemplate.update("DELETE FROM scoped_identifier WHERE identifier = ?", CONCURRENT_IDENTIFIER);
                    jdbcTemplate.update("DELETE FROM scoped_identifier WHERE identifier LIKE 'concurrent-unique-%'");
                });
    }

    @Test
    void concurrentCreateSameIdentifier_shouldOnlySucceedOnce() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.countDown();
                latch.await(); // wait for all threads to be ready
                try {
                    ScopedIdentifierCreateRequest request = new ScopedIdentifierCreateRequest(
                            namespaceId, CONCURRENT_IDENTIFIER, "1.0.0", identifiedItemId, "Concurrent test");
                    createWithLockRetry(request);
                    return true;
                } catch (BusinessException e) {
                    return false;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        executor.shutdown();
        // Worker threads must fully finish before assertions/cleanup, otherwise
        // in-flight INSERTs race with tearDown DELETEs and cause FK violations.
        assertThat(executor.awaitTermination(120, TimeUnit.SECONDS)).isTrue();
        for (Future<Boolean> f : futures) {
            assertThat(f).succeedsWithin(java.time.Duration.ofSeconds(1));
        }

        long successCount = futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .filter(Boolean::booleanValue)
                .count();

        assertThat(successCount).isEqualTo(1);

        // Verify only one record exists in database
        List<ScopedIdentifier> records = scopedIdentifierRepository
                .findAllByNamespaceIdAndIdentifier(namespaceId, CONCURRENT_IDENTIFIER);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getIdentifier()).isEqualTo(CONCURRENT_IDENTIFIER);
    }

    @Test
    void concurrentCreateDifferentIdentifiers_shouldAllSucceed() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            futures.add(executor.submit(() -> {
                latch.countDown();
                latch.await();
                try {
                    ScopedIdentifierCreateRequest request = new ScopedIdentifierCreateRequest(
                            namespaceId, "concurrent-unique-" + idx, "1.0.0", identifiedItemId, "Concurrent test");
                    createWithLockRetry(request);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        executor.shutdown();
        // See note above: on SQL Server, concurrent INSERTs against the same
        // unique-index region serialize on lock waits, which can exceed 30s.
        assertThat(executor.awaitTermination(120, TimeUnit.SECONDS)).isTrue();
        for (Future<Boolean> f : futures) {
            assertThat(f).succeedsWithin(java.time.Duration.ofSeconds(1));
        }

        long successCount = futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .filter(Boolean::booleanValue)
                .count();

        assertThat(successCount).isEqualTo(threadCount);
    }

    /**
     * SQLite 文件库是单写者模型：读→写事务的锁升级遇到他人持写锁时立即
     * 报 SQLITE_BUSY（busy_timeout 对升级路径不生效）。这里对锁冲突做有限
     * 重试；重复标识等业务异常不在重试之列。SQL Server 下锁等待由引擎处理，
     * 该重试通常一次都不用。
     */
    private void createWithLockRetry(ScopedIdentifierCreateRequest request) {
        final int maxAttempts = 20;
        for (int attempt = 1; ; attempt++) {
            try {
                scopedIdentifierService.create(request);
                return;
            } catch (CannotAcquireLockException | org.springframework.jdbc.UncategorizedSQLException e) {
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
