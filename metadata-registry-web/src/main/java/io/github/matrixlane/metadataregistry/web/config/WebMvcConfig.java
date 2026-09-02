package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.logging.ApiLoggingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC configuration that registers the API logging interceptor
 * for all request paths.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiLoggingInterceptor apiLoggingInterceptor;
    private final InitializationWizardInterceptor initializationWizardInterceptor;
    private final ForcePasswordResetInterceptor forcePasswordResetInterceptor;
    private final MaintenanceModeInterceptor maintenanceModeInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // 用户配置的解析器优先于 Spring Boot 自动配置的 Pageable 解析器，
        // 在分页参数进入 controller 前完成 Item 虚拟排序键改写
        resolvers.add(new ItemSortPageableResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiLoggingInterceptor)
                .addPathPatterns("/api/v1/**")
                .order(0);

        registry.addInterceptor(initializationWizardInterceptor)
                .addPathPatterns("/api/v1/**")
                .order(1);

        registry.addInterceptor(forcePasswordResetInterceptor)
                .addPathPatterns("/api/v1/**")
                .order(2);

        registry.addInterceptor(maintenanceModeInterceptor)
                .addPathPatterns("/api/v1/**")
                .order(3);
    }
}
