package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One segment of an identifier template: a type plus typed parameters.
 *
 * <p>{@code name} identifies the segment for scope references (scopeRefs) and
 * parse results; it is optional for segments that never need referencing
 * (separators, literals).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SegmentDefinition {

    /** Segment type name (see {@link SegmentType}). */
    private String type;

    /** Optional logical name used by scopeRefs, variables and reverse parsing. */
    private String name;

    /** Type-specific parameters. */
    private Map<String, Object> params = new LinkedHashMap<>();

    public SegmentDefinition(String type, String name, Map<String, Object> params) {
        this.type = type;
        this.name = name;
        this.params = params != null ? params : new LinkedHashMap<>();
    }

    public SegmentType segmentType() {
        if (type == null || type.isBlank()) {
            throw new SegmentTemplateException("Segment is missing 'type'");
        }
        try {
            return SegmentType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            if ("RANDOM".equalsIgnoreCase(type.trim())) {
                throw new SegmentTemplateException(
                        "段类型 RANDOM 已移除：随机段无唯一性保证，请改用顺序号、时间戳或 UUID 段");
            }
            throw new SegmentTemplateException("Unknown segment type: " + type);
        }
    }

    // --- typed parameter helpers ---

    public String stringParam(String key) {
        Object v = params.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    public String stringParam(String key, String defaultValue) {
        String v = stringParam(key);
        return v != null && !v.isEmpty() ? v : defaultValue;
    }

    public long longParam(String key, long defaultValue) {
        Object v = params.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return defaultValue;
    }

    public int intParam(String key, int defaultValue) {
        return (int) longParam(key, defaultValue);
    }

    public boolean boolParam(String key, boolean defaultValue) {
        Object v = params.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> mapParam(String key) {
        Object v = params.get(key);
        Map<String, String> result = new LinkedHashMap<>();
        if (v instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                result.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public java.util.List<String> listParam(String key) {
        Object v = params.get(key);
        java.util.List<String> result = new java.util.ArrayList<>();
        if (v instanceof java.util.Collection<?> c) {
            for (Object o : c) {
                result.add(String.valueOf(o));
            }
        } else if (v instanceof String s && !s.isBlank()) {
            for (String part : s.split(",")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        return result;
    }
}
