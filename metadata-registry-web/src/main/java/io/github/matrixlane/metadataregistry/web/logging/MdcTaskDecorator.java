package io.github.matrixlane.metadataregistry.web.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Spring {@link TaskDecorator} that propagates SLF4J MDC context from the caller thread
 * to async task threads. This ensures {@code traceId} and other MDC variables are
 * available in logs produced by {@code @Async} methods and custom thread pools.
 * <p>
 * Usage: register on a {@link org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor}
 * via {@code executor.setTaskDecorator(mdcTaskDecorator)}.
 *
 * @see TraceIdFilter
 */
@Component
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
