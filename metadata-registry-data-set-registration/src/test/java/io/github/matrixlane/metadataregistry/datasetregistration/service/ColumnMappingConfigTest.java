package io.github.matrixlane.metadataregistry.datasetregistration.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnMappingConfigTest {

    private final ColumnMappingConfig mapping = new ColumnMappingConfig();

    @Test
    void shouldResolveChineseHeader() {
        assertThat(mapping.resolve("数据元标识符")).isEqualTo("deIdentifier");
        assertThat(mapping.resolve("数据元名称")).isEqualTo("deName");
        assertThat(mapping.resolve("定义")).isEqualTo("deDefinition");
        assertThat(mapping.resolve("数据类型")).isEqualTo("dataType");
        assertThat(mapping.resolve("表示格式")).isEqualTo("representationFormat");
        assertThat(mapping.resolve("允许值")).isEqualTo("permissibleValues");
        assertThat(mapping.resolve("必填标识")).isEqualTo("required");
    }

    @Test
    void shouldResolveChineseAliases() {
        assertThat(mapping.resolve("数据元标识")).isEqualTo("deIdentifier");
        assertThat(mapping.resolve("数据元名")).isEqualTo("deName");
        assertThat(mapping.resolve("是否必填")).isEqualTo("required");
        assertThat(mapping.resolve("必填")).isEqualTo("required");
    }

    @Test
    void shouldResolveEnglishHeader() {
        assertThat(mapping.resolve("Data Element Identifier")).isEqualTo("deIdentifier");
        assertThat(mapping.resolve("Data Element Name")).isEqualTo("deName");
        assertThat(mapping.resolve("Definition")).isEqualTo("deDefinition");
        assertThat(mapping.resolve("Data Type")).isEqualTo("dataType");
        assertThat(mapping.resolve("Representation Format")).isEqualTo("representationFormat");
        assertThat(mapping.resolve("Permissible Values")).isEqualTo("permissibleValues");
        assertThat(mapping.resolve("Required")).isEqualTo("required");
    }

    @Test
    void shouldReturnHeaderAsFallbackForUnknownColumns() {
        assertThat(mapping.resolve("UnknownColumn")).isEqualTo("UnknownColumn");
        assertThat(mapping.resolve("自定义列")).isEqualTo("自定义列");
    }

    @Test
    void shouldPreferCustomMappingOverDefault() {
        mapping.addMapping("数据元标识符", "customField");
        assertThat(mapping.resolve("数据元标识符")).isEqualTo("customField");
    }

    @Test
    void shouldAddCustomMappingForNewColumn() {
        mapping.addMapping("My Column", "myField");
        assertThat(mapping.resolve("My Column")).isEqualTo("myField");
    }

    @Test
    void shouldTrimWhitespace() {
        assertThat(mapping.resolve("  数据元标识符  ")).isEqualTo("deIdentifier");
    }

    @Test
    void shouldReturnNullForNullInput() {
        assertThat(mapping.resolve(null)).isNull();
    }
}
