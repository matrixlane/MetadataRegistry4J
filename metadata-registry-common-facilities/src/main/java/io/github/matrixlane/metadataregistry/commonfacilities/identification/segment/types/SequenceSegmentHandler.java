package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.types;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentContext;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentDefinition;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTemplateException;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentType;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTypeHandler;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SequenceOverflowException;

import java.util.List;

/**
 * Auto-incrementing sequence number. Params: start, increment, pad,
 * scopeRefs (names of other segments composing the scope key), overflow.
 *
 * <p>Without scopeRefs the counter is namespace-global. Overflow is detected
 * against the pad ceiling and always rejected — identifiers are never
 * truncated or repeated.
 */
public class SequenceSegmentHandler implements SegmentTypeHandler {

    /** Scope key used when the sequence references no other segments. */
    public static final String GLOBAL_SCOPE_KEY = "__GLOBAL__";

    @Override
    public SegmentType type() {
        return SegmentType.SEQUENCE;
    }

    @Override
    public void validateParams(SegmentDefinition def) {
        int pad = def.intParam("pad", 6);
        if (pad < 1 || pad > 18) {
            throw new SegmentTemplateException("SEQUENCE segment 'pad' must be between 1 and 18");
        }
        long start = def.longParam("start", 1);
        if (start < 0) {
            throw new SegmentTemplateException("SEQUENCE segment 'start' must be >= 0");
        }
        int increment = def.intParam("increment", 1);
        if (increment < 1) {
            throw new SegmentTemplateException("SEQUENCE segment 'increment' must be >= 1");
        }
        if (start > maxValue(pad)) {
            throw new SegmentTemplateException(
                    "SEQUENCE segment 'start' " + start + " exceeds the pad ceiling " + maxValue(pad));
        }
    }

    @Override
    public String generate(SegmentDefinition def, SegmentContext context) {
        long start = def.longParam("start", 1);
        int increment = def.intParam("increment", 1);
        int pad = def.intParam("pad", 6);
        String scopeKey = scopeKeyOf(def, context);

        long next;
        if (context.isPreview()) {
            // Preview must not allocate: show the scope's first value.
            next = start;
        } else {
            if (context.getAllocator() == null) {
                throw new SegmentTemplateException("SEQUENCE segment requires a sequence allocator");
            }
            next = context.getAllocator().allocate(scopeKey, start, increment, pad);
        }

        if (next > maxValue(pad)) {
            throw new SequenceOverflowException(
                    "Sequence overflow in scope '" + scopeKey + "': value " + next
                            + " exceeds the pad=" + pad + " ceiling " + maxValue(pad)
                            + "; adjust the overflow configuration (larger pad or new scope)");
        }
        return String.format("%0" + pad + "d", next);
    }

    /**
     * Composes the scope key from the values of referenced segments, in
     * template order. References must point at earlier segments whose values
     * are already resolved.
     */
    public static String scopeKeyOf(SegmentDefinition def, SegmentContext context) {
        List<String> refs = def.listParam("scopeRefs");
        if (refs.isEmpty()) {
            return GLOBAL_SCOPE_KEY;
        }
        StringBuilder key = new StringBuilder();
        for (String ref : refs) {
            String value = context.getResolvedValues().get(ref);
            if (value == null) {
                value = context.getVariables().get(ref);
            }
            if (value == null) {
                throw new SegmentTemplateException(
                        "SEQUENCE segment scopeRef '" + ref + "' has no resolved value; "
                                + "scopeRefs must reference earlier segments");
            }
            if (key.length() > 0) {
                key.append('.');
            }
            key.append(value);
        }
        return key.toString();
    }

    /** Maximum value representable with the given pad length (pad=3 -&gt; 999). */
    public static long maxValue(int pad) {
        long max = 0;
        for (int i = 0; i < pad; i++) {
            max = max * 10 + 9;
        }
        return max;
    }

    @Override
    public String valueRegex(SegmentDefinition def) {
        int pad = def.intParam("pad", 6);
        return "\\d{" + pad + "}";
    }

    @Override
    public String uiPanelSchema() {
        return "{\"fields\":[{\"key\":\"start\",\"label\":\"起始值\",\"type\":\"number\",\"default\":1},"
                + "{\"key\":\"increment\",\"label\":\"步长\",\"type\":\"number\",\"default\":1},"
                + "{\"key\":\"pad\",\"label\":\"补零位数\",\"type\":\"number\",\"default\":6},"
                + "{\"key\":\"scopeRefs\",\"label\":\"作用域引用\",\"type\":\"segment-refs\"},"
                + "{\"key\":\"overflow\",\"label\":\"溢出策略\",\"type\":\"select\",\"options\":[\"REJECT\"],\"default\":\"REJECT\"}]}";
    }
}
