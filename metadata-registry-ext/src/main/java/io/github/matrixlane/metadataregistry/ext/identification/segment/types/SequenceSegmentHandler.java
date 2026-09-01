package io.github.matrixlane.metadataregistry.ext.identification.segment.types;

import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentContext;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentDefinition;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTemplateException;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentType;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTypeHandler;
import io.github.matrixlane.metadataregistry.ext.identification.segment.SequenceOverflowException;

import java.util.List;

/**
 * Auto-incrementing sequence number. Params: start, increment, pad, overflow.
 *
 * <p>The scope is derived implicitly from the template structure and cannot
 * be configured: when the template contains an OBJECT_ABBREVIATION segment
 * (which must precede the sequence), the resolved abbreviation is the scope
 * key, so each object type counts independently; otherwise the counter is
 * namespace-global. Explicit {@code scopeRefs} params are rejected at
 * template validation. Overflow is detected against the pad ceiling and
 * always rejected — identifiers are never truncated or repeated.
 */
public class SequenceSegmentHandler implements SegmentTypeHandler {

    /** Scope key used when the template has no OBJECT_ABBREVIATION segment. */
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
        String scopeKey = scopeKeyOf(context);

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
     * Derives the scope key from the generation context: the resolved
     * OBJECT_ABBREVIATION value when the template produced one, otherwise
     * the namespace-global scope.
     */
    public static String scopeKeyOf(SegmentContext context) {
        String abbreviation = context.getObjectAbbreviation();
        return abbreviation != null && !abbreviation.isBlank() ? abbreviation : GLOBAL_SCOPE_KEY;
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
                + "{\"key\":\"overflow\",\"label\":\"溢出策略\",\"type\":\"select\",\"options\":[\"REJECT\"],\"default\":\"REJECT\"}]}";
    }
}
