package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.types.SequenceSegmentHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The segment template engine: one segment definition derives three
 * capabilities —
 * <ol>
 *   <li>the generator used at registration time,</li>
 *   <li>the format-validation regex for manual entry and imports,</li>
 *   <li>the reverse parser that splits an existing identifier back into
 *       segment values (basis of sequence backfill).</li>
 * </ol>
 *
 * <p>Stateless; all behavior is driven by {@link SegmentTypeRegistry} handlers.
 */
public class SegmentTemplateEngine {

    private final SegmentTypeRegistry registry;

    public SegmentTemplateEngine() {
        this(SegmentTypeRegistry.getInstance());
    }

    public SegmentTemplateEngine(SegmentTypeRegistry registry) {
        this.registry = registry;
    }

    /**
     * Generates an identifier by concatenating segment values in order.
     * SEQUENCE segments allocate through the context's allocator unless the
     * context is in preview mode.
     */
    public String generate(SegmentTemplate template, SegmentContext context) {
        StringBuilder out = new StringBuilder();
        for (SegmentDefinition def : template.getSegments()) {
            SegmentTypeHandler handler = registry.handlerOf(def.segmentType());
            String value = handler.generate(def, context);
            out.append(value);
            context.appendOutput(value);
            context.recordValue(def.getName(), value);
        }
        return out.toString();
    }

    /**
     * Previews a template with sample variables: SEQUENCE segments show their
     * start value and no allocation occurs.
     */
    public String preview(SegmentTemplate template, String itemType, Map<String, String> sampleVariables) {
        return generate(template, SegmentContext.preview(itemType, sampleVariables));
    }

    /**
     * Derives the full-match format-validation regex for the template.
     *
     * @throws SegmentTemplateException when any segment cannot derive a regex
     */
    public Pattern deriveFormatPattern(SegmentTemplate template) {
        StringBuilder regex = new StringBuilder("^");
        for (SegmentDefinition def : template.getSegments()) {
            regex.append(registry.handlerOf(def.segmentType()).valueRegex(def));
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    /** Returns true when the identifier fully matches the template's format. */
    public boolean matchesFormat(SegmentTemplate template, String identifier) {
        return identifier != null && deriveFormatPattern(template).matcher(identifier).matches();
    }

    /**
     * Reverse-parses an identifier back into segment values. Named segments
     * appear in the result keyed by name; unnamed segments are skipped.
     *
     * @throws SegmentTemplateException when the identifier does not match
     */
    public Map<String, String> parse(SegmentTemplate template, String identifier) {
        StringBuilder regex = new StringBuilder("^");
        List<String> groupNames = new ArrayList<>();
        int index = 0;
        for (SegmentDefinition def : template.getSegments()) {
            String fragment = registry.handlerOf(def.segmentType()).valueRegex(def);
            String groupName = "s" + index;
            regex.append("(?<").append(groupName).append('>').append(fragment).append(')');
            groupNames.add(groupName);
            index++;
        }
        regex.append('$');

        Matcher matcher = Pattern.compile(regex.toString()).matcher(identifier != null ? identifier : "");
        if (!matcher.matches()) {
            throw new SegmentTemplateException("Identifier '" + identifier + "' does not match the template format");
        }

        Map<String, String> values = new LinkedHashMap<>();
        List<SegmentDefinition> segments = template.getSegments();
        for (int i = 0; i < segments.size(); i++) {
            SegmentDefinition def = segments.get(i);
            if (def.getName() != null && !def.getName().isBlank()) {
                values.put(def.getName(), matcher.group(groupNames.get(i)));
            }
        }
        return values;
    }

    /**
     * Validates the template structurally: parameter schema of every segment,
     * unique variable names, scopeRefs pointing at earlier named segments,
     * regex derivability, and CHECKSUM placement (must not be first).
     *
     * @return list of validation error messages (empty = valid)
     */
    public List<String> validate(SegmentTemplate template) {
        List<String> errors = new ArrayList<>();
        if (template == null || template.getSegments().isEmpty()) {
            errors.add("模板不能为空：至少需要一个段");
            return errors;
        }

        Set<String> declaredNames = new LinkedHashSet<>();
        Set<String> variableNames = new LinkedHashSet<>();
        List<SegmentDefinition> segments = template.getSegments();

        for (int i = 0; i < segments.size(); i++) {
            SegmentDefinition def = segments.get(i);
            String label = "第" + (i + 1) + "段(" + def.getType() + ")";

            SegmentType type;
            try {
                type = def.segmentType();
            } catch (SegmentTemplateException e) {
                errors.add(label + ": " + e.getMessage());
                continue;
            }

            try {
                registry.handlerOf(type).validateParams(def);
            } catch (SegmentTemplateException e) {
                errors.add(label + ": " + e.getMessage());
            }

            try {
                registry.handlerOf(type).valueRegex(def);
            } catch (SegmentTemplateException e) {
                errors.add(label + " 无法派生格式正则: " + e.getMessage());
            }

            if (def.getName() != null && !def.getName().isBlank()) {
                if (type == SegmentType.VARIABLE && !variableNames.add(def.getName())) {
                    errors.add(label + ": 变量段名 '" + def.getName() + "' 重复");
                }
                declaredNames.add(def.getName());
            }

            if (type == SegmentType.SEQUENCE) {
                for (String ref : def.listParam("scopeRefs")) {
                    if (!declaredNames.contains(ref)) {
                        errors.add(label + ": 顺序号段引用了不存在的段名 '" + ref
                                + "'（scopeRefs 必须引用前面已声明的段）");
                    }
                }
            }

            if (type == SegmentType.CHECKSUM && i == 0) {
                errors.add(label + ": 校验位段不能是第一个段（需要前序段作为输入）");
            }
        }
        return errors;
    }

    /**
     * Computes the scope key a SEQUENCE segment would use for the given parsed
     * segment values. Blank-scope sequences yield
     * {@link SequenceSegmentHandler#GLOBAL_SCOPE_KEY}.
     */
    public static String scopeKeyOf(SegmentDefinition sequenceDef, Map<String, String> segmentValues) {
        List<String> refs = sequenceDef.listParam("scopeRefs");
        if (refs.isEmpty()) {
            return SequenceSegmentHandler.GLOBAL_SCOPE_KEY;
        }
        StringBuilder key = new StringBuilder();
        for (String ref : refs) {
            String value = segmentValues.get(ref);
            if (value == null) {
                throw new SegmentTemplateException("scopeRef '" + ref + "' has no parsed value");
            }
            if (key.length() > 0) {
                key.append('.');
            }
            key.append(value);
        }
        return key.toString();
    }
}
