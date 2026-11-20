package io.github.matrixlane.metadataregistry.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code AdministeredItemExport} 标注 {@code @JsonFilter("exportFilter")} 供导出服务的
 * 字段白名单（createFilteredMapper）使用；其余经主 ObjectMapper 的序列化路径（如同步导出
 * POST 返回实体结果）不携带对应 FilterProvider，未知 filter id 必须静默忽略而非抛
 * InvalidDefinitionException（export-dictionary-pagination 评审期实机暴露的既有缺陷：
 * 本机库条目数超同步上限 1 万，同步导出正常返回路径此前从未被实际命中）。
 */
@Configuration
public class JacksonFilterConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer unknownJsonFilterIdTolerant() {
        return builder -> builder.postConfigurer(this::configure);
    }

    private void configure(ObjectMapper objectMapper) {
        objectMapper.setFilterProvider(new SimpleFilterProvider().setFailOnUnknownId(false));
    }
}
