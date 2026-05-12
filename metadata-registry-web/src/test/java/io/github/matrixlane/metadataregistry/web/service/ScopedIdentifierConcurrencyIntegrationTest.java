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

    private UUID namespaceId;
    private UUID identifiedItemId;
    private static final String CONCURRENT_IDENTIFIER = "concurrent-test-id";

    @BeforeEach
    @Transactional
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
        // Delete version history first due to FK constraint
        jdbcTemplate.update("DELETE FROM identifier_version_history WHERE scoped_identifier_id IN (SELECT id FROM scoped_identifier WHERE identifier = ?)", CONCURRENT_IDENTIFIER);
        jdbcTemplate.update("DELETE FROM identifier_version_history WHERE scoped_identifier_id IN (SELECT id FROM scoped_identifier WHERE identifier LIKE 'concurrent-unique-%')");
        jdbcTemplate.update("DELETE FROM scoped_identifier WHERE identifier = ?", CONCURRENT_IDENTIFIER);
        jdbcTemplate.update("DELETE FROM scoped_identifier WHERE identifier LIKE 'concurrent-unique-%'");
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
                    scopedIdentifierService.create(request);
                    return true;
                } catch (BusinessException e) {
                    return false;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

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
                    scopedIdentifierService.create(request);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

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
}
