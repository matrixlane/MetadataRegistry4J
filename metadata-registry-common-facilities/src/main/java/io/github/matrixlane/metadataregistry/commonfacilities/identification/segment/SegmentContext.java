package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generation context passed through the segment pipeline.
 *
 * <ul>
 *   <li>{@code itemType}: persistent class simple name of the registered item
 *       (drives OBJECT_ABBREVIATION).</li>
 *   <li>{@code variables}: caller-supplied values for VARIABLE segments.</li>
 *   <li>{@code resolvedValues}: values produced so far, keyed by segment name
 *       (drives SEQUENCE scopeRefs and CHECKSUM input).</li>
 *   <li>{@code preview}: when true, SEQUENCE segments use their start value
 *       without touching the allocator (preview must not allocate).</li>
 * </ul>
 */
public class SegmentContext {

    private final String itemType;
    private final Map<String, String> variables;
    private final Map<String, String> resolvedValues = new LinkedHashMap<>();
    private final SequenceAllocator allocator;
    private final boolean preview;
    /** Concatenation of segment values produced so far (drives CHECKSUM). */
    private final StringBuilder currentPrefix = new StringBuilder();

    public SegmentContext(String itemType, Map<String, String> variables,
                          SequenceAllocator allocator, boolean preview) {
        this.itemType = itemType;
        this.variables = variables != null ? variables : Collections.emptyMap();
        this.allocator = allocator;
        this.preview = preview;
    }

    public static SegmentContext of(String itemType, Map<String, String> variables,
                                    SequenceAllocator allocator) {
        return new SegmentContext(itemType, variables, allocator, false);
    }

    public static SegmentContext preview(String itemType, Map<String, String> variables) {
        return new SegmentContext(itemType, variables, null, true);
    }

    public String getItemType() {
        return itemType;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public Map<String, String> getResolvedValues() {
        return resolvedValues;
    }

    public void recordValue(String name, String value) {
        if (name != null && !name.isBlank()) {
            resolvedValues.put(name, value);
        }
    }

    public SequenceAllocator getAllocator() {
        return allocator;
    }

    public boolean isPreview() {
        return preview;
    }

    public String getCurrentPrefix() {
        return currentPrefix.toString();
    }

    public void appendOutput(String value) {
        currentPrefix.append(value);
    }
}
