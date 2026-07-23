package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 命名冲突检测性能基准测试。
 *
 * <p>验证单 Namespace 内批量 Sign 值冲突检测的响应时间符合非功能性需求（< 100ms）。
 */
@ExtendWith(MockitoExtension.class)
class NamingConflictDetectorPerformanceTest {

    @Mock
    private DesignationRepository designationRepository;

    @Mock
    private ScopedIdentifierRepository scopedIdentifierRepository;

    private NamingConflictDetector detector;
    private UUID namespaceId;

    @BeforeEach
    void setUp() {
        detector = new NamingConflictDetector(designationRepository, scopedIdentifierRepository);
        namespaceId = UUID.randomUUID();
        when(designationRepository.existsByNamespaceIdAndSignValueAndItemIdNot(
                eq(namespaceId), any(), any()))
                .thenReturn(false);
    }

    @Test
    void batchConflictDetection_shouldCompleteWithin100ms() {
        int batchSize = 1000;
        long startNanos = System.nanoTime();

        for (int i = 0; i < batchSize; i++) {
            detector.hasConflict(namespaceId, Sign.of("sign_" + i));
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        double elapsedMs = elapsedNanos / 1_000_000.0;
        double avgMs = elapsedMs / batchSize;

        // 总批量检测应在 100ms 内完成（Mock 场景下主要验证方法本身开销）
        assertThat(elapsedMs)
                .withFailMessage(
                        "批量 %d 次冲突检测耗时 %.2f ms，平均 %.4f ms/次，超过 100ms 阈值",
                        batchSize, elapsedMs, avgMs)
                .isLessThan(100.0);

        // 平均单次应远低于 1ms
        assertThat(avgMs)
                .withFailMessage("平均单次冲突检测耗时 %.4f ms，超过 1ms", avgMs)
                .isLessThan(1.0);
    }
}
