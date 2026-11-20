package io.github.matrixlane.metadataregistry.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link JacksonFilterConfig}：主 ObjectMapper 对未知 @JsonFilter id 静默忽略，
 * 使同步导出等未经 createFilteredMapper 的路径可正常序列化 AdministeredItemExport。
 */
class JacksonFilterConfigTest {

    private final JacksonFilterConfig config = new JacksonFilterConfig();

    @Test
    @DisplayName("Customizer 装配的主 ObjectMapper：未知 filter id 不抛异常，正常输出全部字段")
    void customizedMapper_toleratesUnknownFilterId() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        config.unknownJsonFilterIdTolerant().customize(builder);
        ObjectMapper mapper = builder.build();

        SampleRecord sample = new SampleRecord("指称文本", "定义文本");
        assertThatCode(() -> mapper.writeValueAsString(sample)).doesNotThrowAnyException();
        String json = mapper.writeValueAsString(sample);
        assertThat(json).contains("指称文本").contains("定义文本");
    }

    @com.fasterxml.jackson.annotation.JsonFilter("exportFilter")
    private record SampleRecord(String itemLabel, String itemDefinition) {
    }
}
