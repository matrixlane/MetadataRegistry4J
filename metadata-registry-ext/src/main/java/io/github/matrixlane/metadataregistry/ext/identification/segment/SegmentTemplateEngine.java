package io.github.matrixlane.metadataregistry.ext.identification.segment;

import io.github.matrixlane.metadataregistry.ext.identification.segment.types.SequenceSegmentHandler;

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

    /** Maximum identifier length (scoped_identifier.identifier column). */
    public static final int MAX_IDENTIFIER_LENGTH = 255;

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
     * unique variable names, regex derivability, CHECKSUM placement (must not
     * be first), exactly one uniqueness segment (SEQUENCE / TIMESTAMP / UUID),
     * millisecond precision for a uniqueness TIMESTAMP, implicit SEQUENCE
     * scope rules (no explicit scopeRefs; OBJECT_ABBREVIATION must precede
     * and be named), and the total output length ceiling.
     *
     * @return list of validation error messages (empty = valid)
     */
    public List<String> validate(SegmentTemplate template) {
        List<String> errors = new ArrayList<>();
        if (template == null || template.getSegments().isEmpty()) {
            errors.add("模板不能为空：至少需要一个段");
            return errors;
        }

        Set<String> variableNames = new LinkedHashSet<>();
        List<SegmentDefinition> segments = template.getSegments();
        List<SegmentDefinition> uniquenessSegments = new ArrayList<>();
        int abbreviationIndex = -1;
        SegmentDefinition abbreviationDef = null;
        int sequenceIndex = -1;

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

            boolean paramsValid = true;
            try {
                registry.handlerOf(type).validateParams(def);
            } catch (SegmentTemplateException e) {
                paramsValid = false;
                errors.add(label + ": " + e.getMessage());
            }

            // 参数校验未通过的段跳过格式正则派生，避免 handler 在缺失参数上抛出 NPE
            if (paramsValid) {
                try {
                    registry.handlerOf(type).valueRegex(def);
                } catch (SegmentTemplateException e) {
                    errors.add(label + " 无法派生格式正则: " + e.getMessage());
                }
            }

            if (def.getName() != null && !def.getName().isBlank()) {
                if (type == SegmentType.VARIABLE && !variableNames.add(def.getName())) {
                    errors.add(label + ": 变量段名 '" + def.getName() + "' 重复");
                }
            }

            switch (type) {
                case OBJECT_ABBREVIATION -> {
                    if (abbreviationIndex < 0) {
                        abbreviationIndex = i;
                        abbreviationDef = def;
                    }
                }
                case SEQUENCE -> {
                    if (!def.listParam("scopeRefs").isEmpty()) {
                        errors.add(label + ": 顺序号段不允许显式配置 scopeRefs——作用域由系统自动推导"
                                + "（模板含对象缩写段时按对象类型计数，否则全局计数）");
                    }
                    if (sequenceIndex < 0) {
                        sequenceIndex = i;
                    }
                    uniquenessSegments.add(def);
                }
                case TIMESTAMP, UUID -> uniquenessSegments.add(def);
                case CHECKSUM -> {
                    if (i == 0) {
                        errors.add(label + ": 校验位段不能是第一个段（需要前序段作为输入）");
                    }
                }
                default -> { }
            }
        }

        // 唯一性段：顺序号、时间戳、UUID 三者择其一
        if (uniquenessSegments.isEmpty()) {
            errors.add("模板必须恰好包含一个唯一性段（顺序号 SEQUENCE、时间戳 TIMESTAMP、UUID 三者择其一），"
                    + "当前未包含任何唯一性段");
        } else if (uniquenessSegments.size() > 1) {
            errors.add("模板包含 " + uniquenessSegments.size() + " 个唯一性段"
                    + "（顺序号、时间戳、UUID 必须三者择其一），请仅保留一个");
        } else {
            SegmentDefinition only = uniquenessSegments.get(0);
            if (only.segmentType() == SegmentType.TIMESTAMP) {
                String pattern = only.stringParam("pattern", "");
                if (maxRunOf(pattern, 'S') < 3) {
                    errors.add("时间戳段作为唯一性段时必须精确到毫秒（pattern 中连续 S 不少于 3 位，"
                            + "如 yyyyMMddHHmmssSSS），当前 pattern: " + pattern);
                }
            }
        }

        // 顺序号作用域取对象缩写段的值：缩写段必须先于顺序号段且必须命名
        if (sequenceIndex >= 0 && abbreviationIndex >= 0) {
            if (abbreviationIndex > sequenceIndex) {
                errors.add("对象缩写段必须位于顺序号段之前（顺序号作用域取其值）");
            }
            if (abbreviationDef.getName() == null || abbreviationDef.getName().isBlank()) {
                errors.add("对象缩写段必须命名（name），以便顺序号作用域与序号回填定位其值");
            }
        }

        // 模板输出总长度上限（scoped_identifier.identifier 列长 255）
        long totalMaxLength = 0;
        for (SegmentDefinition def : segments) {
            SegmentType type;
            try {
                type = def.segmentType();
            } catch (SegmentTemplateException e) {
                continue;
            }
            totalMaxLength += maxSegmentLength(def, type);
        }
        if (totalMaxLength > MAX_IDENTIFIER_LENGTH) {
            errors.add("模板各段最大输出长度之和 " + totalMaxLength + " 超出标识列上限 "
                    + MAX_IDENTIFIER_LENGTH + "：请缩短固定字符串、减小变量段 maxLength 或改用短型 UUID");
        }
        return errors;
    }

    /**
     * Computes the scope key a SEQUENCE segment uses for the given parsed
     * segment values: the parsed OBJECT_ABBREVIATION value when the template
     * contains such a segment, otherwise
     * {@link SequenceSegmentHandler#GLOBAL_SCOPE_KEY}.
     */
    public static String scopeKeyOf(SegmentTemplate template, Map<String, String> segmentValues) {
        for (SegmentDefinition def : template.getSegments()) {
            if (def.segmentType() == SegmentType.OBJECT_ABBREVIATION) {
                String value = def.getName() != null ? segmentValues.get(def.getName()) : null;
                if (value != null && !value.isBlank()) {
                    return value;
                }
                break;
            }
        }
        return SequenceSegmentHandler.GLOBAL_SCOPE_KEY;
    }

    /** Longest run of the given character in the value. */
    private static int maxRunOf(String value, char c) {
        int max = 0;
        int run = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == c) {
                run++;
                max = Math.max(max, run);
            } else {
                run = 0;
            }
        }
        return max;
    }

    /** Maximum output length one segment can produce (for the total-length check). */
    private static long maxSegmentLength(SegmentDefinition def, SegmentType type) {
        return switch (type) {
            case LITERAL -> def.stringParam("value", "").length();
            case OBJECT_ABBREVIATION -> {
                int catalogMax = ObjectAbbreviationCatalog.getInstance().maxAbbreviationLength();
                String fallback = def.stringParam("default");
                yield Math.max(catalogMax, fallback != null ? fallback.length() : 0);
            }
            case SEQUENCE -> def.intParam("pad", 6);
            case UUID -> "short".equalsIgnoreCase(def.stringParam("form", "full")) ? 8 : 36;
            case SEPARATOR -> Math.max(1, def.stringParam("char", ".").length());
            case TIMESTAMP -> def.stringParam("pattern", "").length();
            case CHECKSUM -> 1;
            case VARIABLE -> def.intParam("maxLength", 32);
        };
    }
}
