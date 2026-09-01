package io.github.matrixlane.metadataregistry.ext.identification.segment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * System-level static mapping of metadata object types (persistent class
 * simple names) to their identifier abbreviations, loaded from the classpath
 * resource {@code object-abbreviations.json}. The file is user-editable;
 * {@link #reload()} picks up changes (used by tests and admin reload flows).
 */
public class ObjectAbbreviationCatalog {

    /** Classpath location of the user-editable mapping file. */
    public static final String RESOURCE = "/object-abbreviations.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile ObjectAbbreviationCatalog INSTANCE;

    private final Map<String, String> mapping;

    private ObjectAbbreviationCatalog(Map<String, String> mapping) {
        this.mapping = mapping;
    }

    public static ObjectAbbreviationCatalog getInstance() {
        if (INSTANCE == null) {
            synchronized (ObjectAbbreviationCatalog.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ObjectAbbreviationCatalog(load());
                }
            }
        }
        return INSTANCE;
    }

    /** Reloads the mapping from the classpath resource. */
    public static synchronized void reload() {
        INSTANCE = new ObjectAbbreviationCatalog(load());
    }

    private static Map<String, String> load() {
        try (InputStream in = ObjectAbbreviationCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new SegmentTemplateException(
                        "Object abbreviation catalog resource missing: " + RESOURCE);
            }
            Map<String, String> map = MAPPER.readValue(in,
                    new TypeReference<LinkedHashMap<String, String>>() { });
            return Collections.unmodifiableMap(map);
        } catch (SegmentTemplateException e) {
            throw e;
        } catch (Exception e) {
            throw new SegmentTemplateException(
                    "Cannot load object abbreviation catalog " + RESOURCE + ": " + e.getMessage(), e);
        }
    }

    /** Abbreviation for the given item type, or null when unmapped. */
    public String abbreviationOf(String itemType) {
        return itemType != null ? mapping.get(itemType) : null;
    }

    /** All mappings, in file order. */
    public Map<String, String> all() {
        return mapping;
    }

    /** Longest abbreviation length in the catalog (for template length checks). */
    public int maxAbbreviationLength() {
        int max = 0;
        for (String value : mapping.values()) {
            max = Math.max(max, value != null ? value.length() : 0);
        }
        return max;
    }
}
