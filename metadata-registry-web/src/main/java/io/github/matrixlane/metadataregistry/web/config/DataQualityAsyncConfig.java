package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.logging.MdcTaskDecorator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 数据质量评估异步执行器配置.
 *
 * <p>限制并发评估任务数（建议 ≤ 2）以避免数据库负载过高.
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class DataQualityAsyncConfig {

    private final MdcTaskDecorator mdcTaskDecorator;

    @Bean(name = "dataQualityTaskExecutor")
    public Executor dataQualityTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("dq-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
