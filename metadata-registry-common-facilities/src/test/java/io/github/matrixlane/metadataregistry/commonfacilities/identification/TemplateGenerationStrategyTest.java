package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateGenerationStrategyTest {

    private final TemplateGenerationStrategy strategy = new TemplateGenerationStrategy();

    @Test
    void shouldReturnCorrectType() {
        assertThat(strategy.getType()).isEqualTo("TEMPLATE");
    }

    @Test
    void shouldGeneratePlaceholderForNullTemplate() {
        // When used without config, generates a placeholder
        String id = strategy.generate(null);
        assertThat(id).startsWith("TPL-");
    }

    @Test
    void shouldValidateCorrectTemplates() {
        assertThat(TemplateGenerationStrategy.isValidTemplate("{prefix}-{seq:4}")).isTrue();
        assertThat(TemplateGenerationStrategy.isValidTemplate("{yyyyMM}-{seq:4}")).isTrue();
        assertThat(TemplateGenerationStrategy.isValidTemplate("DE-{yyyy}-{MM}-{seq:6}")).isTrue();
        assertThat(TemplateGenerationStrategy.isValidTemplate("{prefix}")).isTrue();
        assertThat(TemplateGenerationStrategy.isValidTemplate("ABC")).isTrue();
    }

    @Test
    void shouldRejectInvalidTemplates() {
        // SQL injection attempt
        assertThat(TemplateGenerationStrategy.isValidTemplate("{prefix}'; DROP TABLE users; --")).isFalse();

        // Script injection
        assertThat(TemplateGenerationStrategy.isValidTemplate("<script>alert(1)</script>")).isFalse();

        // Path traversal
        assertThat(TemplateGenerationStrategy.isValidTemplate("{prefix}../../../etc/passwd")).isFalse();

        // Special characters
        assertThat(TemplateGenerationStrategy.isValidTemplate("{prefix}@gmail.com")).isFalse();
        assertThat(TemplateGenerationStrategy.isValidTemplate("{prefix}${(env.JAVA_HOME)}")).isFalse();

        // Null/blank
        assertThat(TemplateGenerationStrategy.isValidTemplate(null)).isFalse();
        assertThat(TemplateGenerationStrategy.isValidTemplate("")).isFalse();
        assertThat(TemplateGenerationStrategy.isValidTemplate("   ")).isFalse();
    }

    @Test
    void shouldResolveDatePlaceholders() {
        String template = "{yyyyMM}-{dd}";
        TemplateGenerationStrategy.TemplateContext ctx = new TemplateGenerationStrategy.TemplateContext(1, "");

        String result = TemplateGenerationStrategy.generateFromTemplate(template, ctx);

        assertThat(result).matches("^\\d{6}-\\d{2}$"); // yyyyMM-dd format
    }

    @Test
    void shouldResolveSequencePlaceholders() {
        String template = "{seq:4}";
        TemplateGenerationStrategy.TemplateContext ctx = new TemplateGenerationStrategy.TemplateContext(42, "");

        String result = TemplateGenerationStrategy.generateFromTemplate(template, ctx);

        assertThat(result).isEqualTo("0042");
    }

    @Test
    void shouldResolvePrefixPlaceholder() {
        String template = "{prefix}-{seq:4}";
        TemplateGenerationStrategy.TemplateContext ctx = new TemplateGenerationStrategy.TemplateContext(7, "DE");

        String result = TemplateGenerationStrategy.generateFromTemplate(template, ctx);

        assertThat(result).isEqualTo("DE-0007");
    }

    @Test
    void shouldResolveUuidPlaceholder() {
        String template = "{uuid}";
        TemplateGenerationStrategy.TemplateContext ctx = new TemplateGenerationStrategy.TemplateContext(1, "");

        String result = TemplateGenerationStrategy.generateFromTemplate(template, ctx);

        assertThat(result).hasSize(8);
        assertThat(result).matches("^[0-9A-F]+$");
    }

    @Test
    void shouldHandleComplexTemplate() {
        String template = "{prefix}-{yyyyMM}-{seq:6}";
        TemplateGenerationStrategy.TemplateContext ctx = new TemplateGenerationStrategy.TemplateContext(12345, "CO");

        String result = TemplateGenerationStrategy.generateFromTemplate(template, ctx);

        assertThat(result).matches("^CO-\\d{6}-\\d{6}$");
    }

    @Test
    void shouldHandleNoContext() {
        String template = "{yyyyMM}-{seq:4}";
        String result = TemplateGenerationStrategy.generateFromTemplate(template, null);

        assertThat(result).matches("^\\d{6}-0000$"); // seq:4 defaults to 0000 without context
    }

    @Test
    void shouldThrowForInvalidTemplate() {
        String maliciousTemplate = "{prefix}'; DROP TABLE --";
        TemplateGenerationStrategy.TemplateContext ctx = new TemplateGenerationStrategy.TemplateContext(1, "");

        assertThatThrownBy(() -> TemplateGenerationStrategy.generateFromTemplate(maliciousTemplate, ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid template");
    }

    @Test
    void shouldHandleDefaultPadForSeq() {
        String template = "{seq}";
        TemplateGenerationStrategy.TemplateContext ctx = new TemplateGenerationStrategy.TemplateContext(99, "");

        String result = TemplateGenerationStrategy.generateFromTemplate(template, ctx);

        assertThat(result).isEqualTo("0099"); // 4-digit default pad
    }

    @Test
    void configShouldHaveCorrectDefaults() {
        TemplateGenerationStrategy.Config config = new TemplateGenerationStrategy.Config();
        assertThat(config.getTemplate()).isEqualTo("{prefix}-{seq:4}");
        assertThat(config.getPrefix()).isEqualTo("");
        assertThat(config.getPad()).isEqualTo(4);
    }

    @Test
    void shouldResolveDateTimePlaceholders() {
        String template = "{HH}:{mm}:{ss}";
        TemplateGenerationStrategy.TemplateContext ctx = new TemplateGenerationStrategy.TemplateContext(1, "");

        String result = TemplateGenerationStrategy.generateFromTemplate(template, ctx);

        assertThat(result).matches("^\\d{2}:\\d{2}:\\d{2}$");
    }
}
