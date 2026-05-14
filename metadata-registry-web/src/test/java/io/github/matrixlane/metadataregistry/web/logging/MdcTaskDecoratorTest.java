package io.github.matrixlane.metadataregistry.web.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private MdcTaskDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new MdcTaskDecorator();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldPropagateMdcContextToDecoratedRunnable() throws InterruptedException {
        MDC.put("traceId", "abc-123");
        MDC.put("userId", "user-42");

        Runnable original = () -> {
            assertThat(MDC.get("traceId")).isEqualTo("abc-123");
            assertThat(MDC.get("userId")).isEqualTo("user-42");
        };

        Runnable decorated = decorator.decorate(original);

        // Clear MDC in current thread before running decorated to prove it uses the captured context
        MDC.clear();
        assertThat(MDC.get("traceId")).isNull();

        Thread t = new Thread(decorated);
        t.start();
        t.join();
    }

    @Test
    void shouldClearMdcAfterRunnableCompletes() throws InterruptedException {
        MDC.put("traceId", "xyz-789");

        Runnable original = () -> {
            // runnable body
        };

        Runnable decorated = decorator.decorate(original);

        MDC.clear();

        Thread t = new Thread(() -> {
            decorated.run();
            // After run, MDC should be cleared
            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        });
        t.start();
        t.join();
    }

    @Test
    void shouldClearMdcEvenOnException() throws InterruptedException {
        MDC.put("traceId", "err-001");

        Runnable original = () -> {
            throw new RuntimeException("boom");
        };

        Runnable decorated = decorator.decorate(original);

        MDC.clear();

        Thread t = new Thread(() -> {
            try {
                decorated.run();
            } catch (RuntimeException e) {
                // expected
            }
            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        });
        t.start();
        t.join();
    }

    @Test
    void shouldHandleEmptyMdcContext() throws InterruptedException {
        // No MDC set
        Runnable original = () -> {
            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        };

        Runnable decorated = decorator.decorate(original);

        Thread t = new Thread(decorated);
        t.start();
        t.join();
    }

    @Test
    void shouldCaptureMdcAtDecorationTime_notAtRunTime() throws InterruptedException {
        MDC.put("traceId", "captured-111");

        Runnable decorated = decorator.decorate(() -> {
            assertThat(MDC.get("traceId")).isEqualTo("captured-111");
        });

        // Change MDC after decoration but before execution
        MDC.put("traceId", "changed-222");

        Thread t = new Thread(decorated);
        t.start();
        t.join();

        // Current thread should keep its changed value
        assertThat(MDC.get("traceId")).isEqualTo("changed-222");
    }
}
