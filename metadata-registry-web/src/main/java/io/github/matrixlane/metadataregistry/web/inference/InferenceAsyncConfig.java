package io.github.matrixlane.metadataregistry.web.inference;

import io.github.matrixlane.metadataregistry.web.assistant.AssistantJob;
import io.github.matrixlane.metadataregistry.web.assistant.AssistantJobService;
import io.github.matrixlane.metadataregistry.web.assistant.AssistantTask;
import io.github.matrixlane.metadataregistry.web.assistant.task.CompleteMetadataTask;
import io.github.matrixlane.metadataregistry.web.inference.provider.ProcessRunner;
import io.github.matrixlane.metadataregistry.web.logging.MdcTaskDecorator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 推断/协作作业异步执行基础设施：启用 @Async 并注册推断专用线程池。
 *
 * <p>线程池复用 {@link MdcTaskDecorator} 传递 traceId 等日志上下文；
 * ACP 进程执行抽象 {@link ProcessRunner} 在此注册默认实现（ProcessBuilder），
 * 便于单测替换为假实现。
 *
 * <p>统一协作作业内核在此以 @Bean 注册并注入作业工厂：complete-metadata 作业
 * 创建为 {@link InferenceJob} 子类实例，保持既有推断端点响应结构兼容。
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class InferenceAsyncConfig {

    private final MdcTaskDecorator mdcTaskDecorator;

    @Bean(name = "inferenceExecutor")
    public ThreadPoolTaskExecutor inferenceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("mdr-inference-");
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.initialize();
        return executor;
    }

    @Bean
    public ProcessRunner processRunner() {
        return ProcessRunner.defaultRunner();
    }

    @Bean
    public AssistantJobService assistantJobService(InferenceProperties properties,
                                                   List<AssistantTask> tasks,
                                                   InferenceProviderRegistry providerRegistry,
                                                   @Qualifier("inferenceExecutor") Executor inferenceExecutor) {
        return new AssistantJobService(properties, tasks, providerRegistry, inferenceExecutor,
                (taskId, itemId, selection, createdBy) -> CompleteMetadataTask.TASK_ID.equals(taskId)
                        ? new InferenceJob(itemId, createdBy)
                        : new AssistantJob(taskId, itemId, selection, createdBy));
    }
}
