package io.github.matrixlane.metadataregistry.ext.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WI-028-B: Generates identifiers from a configurable template string.
 *
 * <p>Supported placeholders:
 * <ul>
 *   <li>{@code {seq:N}} - sequence number with N-digit zero-padding (requires external sequence service)</li>
 *   <li>{@code {yyyyMM}} - current year-month (e.g. "202605")</li>
 *   <li>{@code {yyyy}} - current 4-digit year</li>
 *   <li>{@code {MM}} - current 2-digit month</li>
 *   <li>{@code {dd}} - current 2-digit day</li>
 *   <li>{@code {HH}} - current hour</li>
 *   <li>{@code {mm}} - current minute</li>
 *   <li>{@code {ss}} - current second</li>
 *   <li>{@code {uuid}} - random UUID (short form, 8 hex chars)</li>
 *   <li>{@code {prefix}} - literal prefix field value</li>
 *   <li>{@code {ns}} - namespace shorthand prefix (shorthand_prefix)</li>
 *   <li>{@code {type}} - registered item type abbreviation (e.g. DataElement → DE)</li>
 * </ul>
 *
 * <p>Configuration JSON:
 * <pre>
 * {
 *   "template": "{prefix}-{yyyyMM}-{seq:4}",
 *   "prefix": "CO",
 *   "pad": 4
 * }
 * </pre>
 *
 * <p>Security: Template is validated against a whitelist pattern to prevent injection.
 * Allowed characters: alphanumeric, separators {@code - _ .} and placeholder
 * characters {@code { } :}.
 */
public class TemplateGenerationStrategy implements IdentifierGenerationStrategy {

    /**
     * Whitelist pattern for template security.
     * Only allows: letters, digits, spaces, separators - _ . and placeholder characters { } :
     */
    private static final Pattern TEMPLATE_WHITELIST = Pattern.compile(
            "^[a-zA-Z0-9\\s\\{\\}\\:\\-\\_\\.]+$"
    );

    /**
     * Placeholder pattern: {placeholder} or {placeholder:args}
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "\\{(yyyyMM|yyyy|MM|dd|HH|mm|ss|uuid|seq(?:\\:\\d+)?|prefix|ns|type)\\}"
    );

    @Override
    public String generate(Namespace namespace) {
        // Template strategy requires external config; use a default for standalone invocation
        return "TPL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public String getType() {
        return "TEMPLATE";
    }

    /**
     * Validates a template string against the whitelist.
     * @param template the template string to validate
     * @return true if the template is safe
     */
    public static boolean isValidTemplate(String template) {
        if (template == null || template.isBlank()) {
            return false;
        }
        return TEMPLATE_WHITELIST.matcher(template).matches();
    }

    /**
     * Generates an identifier from the given template and context.
     *
     * @param template the template string
     * @param context  the generation context (may contain sequence number, prefix, etc.)
     * @return the generated identifier
     * @throws IllegalArgumentException if template is invalid
     */
    public static String generateFromTemplate(String template, TemplateContext context) {
        if (!isValidTemplate(template)) {
            throw new IllegalArgumentException(
                    "Invalid template: must contain only alphanumeric, spaces, and template placeholder characters. " +
                    "Template: " + template);
        }

        String result = template;
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();

        result = result.replace("{yyyyMM}", today.format(DateTimeFormatter.ofPattern("yyyyMM")));
        result = result.replace("{yyyy}", today.format(DateTimeFormatter.ofPattern("yyyy")));
        result = result.replace("{MM}", today.format(DateTimeFormatter.ofPattern("MM")));
        result = result.replace("{dd}", today.format(DateTimeFormatter.ofPattern("dd")));
        result = result.replace("{HH}", String.format("%02d", now.getHour()));
        result = result.replace("{mm}", String.format("%02d", now.getMinute()));
        result = result.replace("{ss}", String.format("%02d", now.getSecond()));
        result = result.replace("{uuid}", UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        if (context != null) {
            // Handle {seq:N} placeholders
            Matcher m = Pattern.compile("\\{seq(?:\\:(\\d+))?\\}").matcher(result);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                int pad = 4; // default pad
                if (m.group(1) != null) {
                    pad = Integer.parseInt(m.group(1));
                }
                long seq = context.getSequenceNumber();
                String replacement = String.format("%0" + pad + "d", seq);
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            result = sb.toString();

            // Handle {prefix}
            if (context.getPrefix() != null) {
                result = result.replace("{prefix}", context.getPrefix());
            }

            // Handle {ns} (namespace shorthand prefix) and {type} (item type abbreviation)
            result = result.replace("{ns}",
                    context.getNamespacePrefix() != null ? context.getNamespacePrefix() : "");
            result = result.replace("{type}",
                    context.getItemType() != null ? context.getItemType() : "");
        } else {
            // Remove unresolvable placeholders if no context
            result = result.replace("{seq}", "0000");
            result = result.replaceAll("\\{seq\\:\\d+\\}", "0000");
            result = result.replace("{prefix}", "");
            result = result.replace("{ns}", "");
            result = result.replace("{type}", "");
        }

        return result;
    }

    /**
     * Context object for template resolution.
     */
    public static class TemplateContext {
        private long sequenceNumber;
        private String prefix;
        private String namespacePrefix;
        private String itemType;
        private int defaultPad = 4;

        public TemplateContext() {}

        public TemplateContext(long sequenceNumber, String prefix) {
            this.sequenceNumber = sequenceNumber;
            this.prefix = prefix;
        }

        public long getSequenceNumber() { return sequenceNumber; }
        public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        public String getNamespacePrefix() { return namespacePrefix; }
        public void setNamespacePrefix(String namespacePrefix) { this.namespacePrefix = namespacePrefix; }
        public String getItemType() { return itemType; }
        public void setItemType(String itemType) { this.itemType = itemType; }
        public int getDefaultPad() { return defaultPad; }
        public void setDefaultPad(int defaultPad) { this.defaultPad = defaultPad; }
    }

    /**
     * Configuration POJO for TemplateGenerationStrategy.
     * Uses simple field access without Jackson dependency.
     */
    public static class Config {
        private String template = "{prefix}-{seq:4}";
        private String prefix = "";
        private int pad = 4;

        public String getTemplate() { return template; }
        public void setTemplate(String template) { this.template = template; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        public int getPad() { return pad; }
        public void setPad(int pad) { this.pad = pad; }

        /**
         * Parse from JSON string without Jackson.
         */
        public static Config fromJson(String json) {
            Config config = new Config();
            if (json == null || json.isBlank()) return config;
            try {
                config.template = extractString(json, "template", "{prefix}-{seq:4}");
                config.prefix = extractString(json, "prefix", "");
                config.pad = extractInt(json, "pad", 4);
            } catch (Exception e) {
                // Return defaults on parse failure
            }
            return config;
        }

        private static String extractString(String json, String key, String defaultVal) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
            java.util.regex.Matcher m = p.matcher(json);
            return m.find() ? m.group(1) : defaultVal;
        }

        private static int extractInt(String json, String key, int defaultVal) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\"" + key + "\"\\s*:\\s*(\\d+)");
            java.util.regex.Matcher m = p.matcher(json);
            return m.find() ? Integer.parseInt(m.group(1)) : defaultVal;
        }
    }
}
