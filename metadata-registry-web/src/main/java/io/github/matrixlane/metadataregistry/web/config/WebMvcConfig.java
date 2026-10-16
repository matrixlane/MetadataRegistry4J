package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import io.github.matrixlane.metadataregistry.web.logging.ApiLoggingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
    public void addFormatters(FormatterRegistry registry) {
        // 映射度查询参数使用标准小写值
        // （如 "related to"），此处注册解析器以支持 @RequestParam 绑定。
        registry.addConverter(String.class, MappingDegree.class, MappingDegree::fromStandardValue);
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
