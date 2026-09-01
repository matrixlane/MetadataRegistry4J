package io.github.matrixlane.metadataregistry.ext.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * WI-028-B: Generates sequential identifiers with configurable prefix and padding.
 *
 * <p>Format: {@code [prefix][zero-padded-sequence-number]}
 * Example: prefix="DE", pad=6, seq=42 → "DE000042"
 *
 * <p>Configuration JSON:
 * <pre>
 * {
 *   "prefix": "DE",      // optional, default ""
 *   "start": 1,          // optional, default 1
 *   "pad": 6,            // optional, default 6
 *   "incrementBy": 1     // optional, default 1
 * }
 * </pre>
 *
 * <p>Thread-safety: This strategy requires atomic increment via the
 * {@link SequenceRegistry}. The actual increment must be done through a
 * DB-backed service with SELECT FOR UPDATE locking.
 */
public class SequenceGenerationStrategy implements IdentifierGenerationStrategy {

    @Override
    public String generate(Namespace namespace) {
        // This method generates an identifier without persisting state.
        // Real increment happens via SequenceRegistryService (web module).
        // For template-based standalone use, return a placeholder.
        return "SEQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public String getType() {
        return "SEQUENCE";
    }

    /**
     * Configuration POJO for SequenceGenerationStrategy.
     * Uses simple reflection-free field access to avoid Jackson dependency.
     */
    public static class Config {
        private String prefix = "";
        private long start = 1;
        private int pad = 6;
        private int incrementBy = 1;

        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix != null ? prefix : ""; }
        public long getStart() { return start; }
        public void setStart(long start) { this.start = start; }
        public int getPad() { return pad; }
        public void setPad(int pad) { this.pad = pad; }
        public int getIncrementBy() { return incrementBy; }
        public void setIncrementBy(int incrementBy) { this.incrementBy = incrementBy; }

        /**
         * Parse from JSON string without Jackson.
         */
        public static Config fromJson(String json) {
            Config config = new Config();
            if (json == null || json.isBlank()) return config;
            try {
                // Simple regex-based parsing for known fields
                config.prefix = extractString(json, "prefix", "");
                config.start = extractLong(json, "start", 1);
                config.pad = extractInt(json, "pad", 6);
                config.incrementBy = extractInt(json, "incrementBy", 1);
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

        private static long extractLong(String json, String key, long defaultVal) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "\"" + key + "\"\\s*:\\s*(\\d+)");
            java.util.regex.Matcher m = p.matcher(json);
            return m.find() ? Long.parseLong(m.group(1)) : defaultVal;
        }

        private static int extractInt(String json, String key, int defaultVal) {
            return (int) extractLong(json, key, defaultVal);
        }

        /**
         * Applies date placeholders in prefix.
         * Supports: {yyyyMM}, {yyyy}, {MM}, {dd}
         */
        public String resolvePrefix() {
            if (prefix == null) return "";
            String resolved = prefix;
            resolved = resolved.replace("{yyyyMM}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM")));
            resolved = resolved.replace("{yyyy}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy")));
            resolved = resolved.replace("{MM}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM")));
            resolved = resolved.replace("{dd}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd")));
            return resolved;
        }
    }
}
