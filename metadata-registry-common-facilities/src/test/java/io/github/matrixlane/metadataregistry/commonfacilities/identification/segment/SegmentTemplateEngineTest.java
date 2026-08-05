package io.github.matrixlane.metadataregistry.commonfacilities.identification.segment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Segment engine unit tests: segment types, implicit sequence scopes,
 * uniqueness-segment rules, overflow, generate/parse round-trip, and the
 * generic golden sample (fixed prefix + object abbreviation + sequence).
 */
class SegmentTemplateEngineTest {

    private final SegmentTemplateEngine engine = new SegmentTemplateEngine();

    /** In-memory per-scope allocator for tests. */
    private static final class MapAllocator implements SequenceAllocator {
        private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

        @Override
        public long allocate(String scopeKey, long start, int increment, int pad) {
            AtomicLong counter = counters.computeIfAbsent(scopeKey, k -> new AtomicLong(start - increment));
            return counter.addAndGet(increment);
        }

        void align(String scopeKey, long value) {
            counters.put(scopeKey, new AtomicLong(value));
        }

        long currentOf(String scopeKey) {
            AtomicLong c = counters.get(scopeKey);
            return c == null ? -1 : c.get();
        }
    }

    /** WN.DE.00001 风格：固定前缀 + 对象缩写 + 顺序号（pad=5）。 */
    private static SegmentTemplate prefixObjectSequenceTemplate() {
        return SegmentTemplate.fromJson("""
                {"segments":[
                  {"type":"LITERAL","params":{"value":"WN"}},
                  {"type":"SEPARATOR","params":{"char":"."}},
                  {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{}},
                  {"type":"SEPARATOR","params":{"char":"."}},
                  {"type":"SEQUENCE","name":"seq","params":{"start":1,"increment":1,"pad":5,"overflow":"REJECT"}}
                ]}""");
    }

    /** 对象缩写 + 顺序号（pad=5），系统默认模板形态。 */
    private static SegmentTemplate objectSequenceTemplate() {
        return SegmentTemplate.fromJson("""
                {"segments":[
                  {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{}},
                  {"type":"SEPARATOR","params":{"char":"."}},
                  {"type":"SEQUENCE","name":"seq","params":{"start":1,"increment":1,"pad":5,"overflow":"REJECT"}}
                ]}""");
    }

    @Nested
    @DisplayName("通用金样本（WN.DE.00001）")
    class GoldenSample {

        @Test
        @DisplayName("生成固定前缀+对象缩写+顺序号标识")
        void generatesGoldenIdentifier() {
            String identifier = engine.generate(prefixObjectSequenceTemplate(),
                    SegmentContext.of("DataElement", Map.of(), new MapAllocator()));
            assertThat(identifier).isEqualTo("WN.DE.00001");
        }

        @Test
        @DisplayName("生成与解析往返一致")
        void roundTrip() {
            SegmentTemplate template = prefixObjectSequenceTemplate();
            String identifier = engine.generate(template,
                    SegmentContext.of("DataElement", Map.of(), new MapAllocator()));
            Map<String, String> parsed = engine.parse(template, identifier);
            assertThat(parsed)
                    .containsEntry("objectAbbr", "DE")
                    .containsEntry("seq", "00001");
        }

        @Test
        @DisplayName("序号回填后从已用最大值继续")
        void backfillAlignment() {
            SegmentTemplate template = objectSequenceTemplate();
            MapAllocator allocator = new MapAllocator();
            // 模拟导入 DE.00001 .. DE.00026 后回填 DE 作用域计数器
            allocator.align("DE", 26);

            String identifier = engine.generate(template,
                    SegmentContext.of("DataElement", Map.of(), allocator));
            assertThat(identifier).isEqualTo("DE.00027");
        }

        @Test
        @DisplayName("预览不产生序号分配")
        void previewDoesNotAllocate() {
            String sample = engine.preview(prefixObjectSequenceTemplate(), "DataElement", Map.of());
            assertThat(sample).isEqualTo("WN.DE.00001");
        }

        @Test
        @DisplayName("格式正则派生")
        void derivedRegex() {
            Pattern pattern = engine.deriveFormatPattern(objectSequenceTemplate());
            assertThat(pattern.matcher("DE.00001").matches()).isTrue();
            assertThat(pattern.matcher("VD.00012").matches()).isTrue();
            assertThat(pattern.matcher("DE.1").matches()).isFalse();
            assertThat(pattern.matcher("XX.00001").matches()).isFalse();
        }
    }

    @Nested
    @DisplayName("顺序号作用域自动推导")
    class SequenceScopes {

        @Test
        @DisplayName("含对象缩写段时按对象类型各自计数")
        void perObjectTypeScopes() {
            SegmentTemplate template = prefixObjectSequenceTemplate();
            MapAllocator allocator = new MapAllocator();
            String de1 = engine.generate(template, SegmentContext.of("DataElement", Map.of(), allocator));
            String de2 = engine.generate(template, SegmentContext.of("DataElement", Map.of(), allocator));
            String vd1 = engine.generate(template, SegmentContext.of("ValueDomain", Map.of(), allocator));
            assertThat(de1).isEqualTo("WN.DE.00001");
            assertThat(de2).isEqualTo("WN.DE.00002");
            assertThat(vd1).isEqualTo("WN.VD.00001");
            assertThat(allocator.currentOf("DE")).isEqualTo(2);
            assertThat(allocator.currentOf("VD")).isEqualTo(1);
        }

        @Test
        @DisplayName("无对象缩写段为命名空间级全局计数")
        void globalScopeWithoutAbbreviation() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"SEQUENCE","name":"seq","params":{"start":1,"increment":1,"pad":5}}
                    ]}""");
            MapAllocator allocator = new MapAllocator();
            String first = engine.generate(template, SegmentContext.of("DataElement", Map.of(), allocator));
            String second = engine.generate(template, SegmentContext.of("ValueDomain", Map.of(), allocator));
            assertThat(first).isEqualTo("00001");
            assertThat(second).isEqualTo("00002");
            assertThat(allocator.currentOf("__GLOBAL__")).isEqualTo(2);
        }

        @Test
        @DisplayName("显式 scopeRefs 校验拒绝")
        void explicitScopeRefsRejected() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"SEQUENCE","name":"seq","params":{"pad":3,"scopeRefs":["month"]}}
                    ]}""");
            List<String> errors = engine.validate(template);
            assertThat(errors).anyMatch(e -> e.contains("scopeRefs"));
        }

        @Test
        @DisplayName("scopeKeyOf 静态推导：含缩写段取解析值，否则全局")
        void staticScopeKeyDerivation() {
            assertThat(SegmentTemplateEngine.scopeKeyOf(objectSequenceTemplate(),
                    Map.of("objectAbbr", "DE", "seq", "00007"))).isEqualTo("DE");
            SegmentTemplate globalTemplate = SegmentTemplate.fromJson("""
                    {"segments":[{"type":"SEQUENCE","name":"seq","params":{"pad":5}}]}""");
            assertThat(SegmentTemplateEngine.scopeKeyOf(globalTemplate, Map.of("seq", "00007")))
                    .isEqualTo("__GLOBAL__");
        }

        @Test
        @DisplayName("序号溢出拒绝生成")
        void overflowRejected() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"SEQUENCE","name":"seq","params":{"start":1,"increment":1,"pad":3}}
                    ]}""");
            SequenceAllocator fullAllocator = (scopeKey, start, increment, pad) -> 1000L;
            assertThatThrownBy(() -> engine.generate(template,
                    SegmentContext.of(null, Map.of(), fullAllocator)))
                    .isInstanceOf(SequenceOverflowException.class)
                    .hasMessageContaining("overflow");
        }
    }

    @Nested
    @DisplayName("唯一性段约束")
    class UniquenessSegmentRules {

        @Test
        @DisplayName("缺少唯一性段被拒绝")
        void missingUniquenessSegment() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"LITERAL","params":{"value":"WN"}},
                      {"type":"SEPARATOR","params":{"char":"."}}
                    ]}""");
            assertThat(engine.validate(template))
                    .anyMatch(e -> e.contains("唯一性段"));
        }

        @Test
        @DisplayName("多个唯一性段被拒绝")
        void multipleUniquenessSegments() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"SEQUENCE","name":"seq","params":{"pad":5}},
                      {"type":"TIMESTAMP","name":"ts","params":{"pattern":"yyyyMMddHHmmssSSS"}}
                    ]}""");
            assertThat(engine.validate(template))
                    .anyMatch(e -> e.contains("三者择其一"));
        }

        @Test
        @DisplayName("时间戳唯一性段精度不足被拒绝")
        void timestampPrecisionTooLow() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[{"type":"TIMESTAMP","name":"ts","params":{"pattern":"yyyyMMdd"}}]}""");
            assertThat(engine.validate(template))
                    .anyMatch(e -> e.contains("毫秒"));
        }

        @Test
        @DisplayName("毫秒与微秒精度的时间戳唯一性段通过")
        void timestampPrecisionAccepted() {
            SegmentTemplate millis = SegmentTemplate.fromJson("""
                    {"segments":[{"type":"TIMESTAMP","name":"ts","params":{"pattern":"yyyyMMddHHmmssSSS"}}]}""");
            SegmentTemplate micros = SegmentTemplate.fromJson("""
                    {"segments":[{"type":"TIMESTAMP","name":"ts","params":{"pattern":"yyyyMMddHHmmssSSSSSS"}}]}""");
            assertThat(engine.validate(millis)).isEmpty();
            assertThat(engine.validate(micros)).isEmpty();
        }

        @Test
        @DisplayName("UUID 唯一性段通过校验")
        void uuidUniquenessAccepted() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{}},
                      {"type":"SEPARATOR","params":{"char":"."}},
                      {"type":"UUID","name":"u","params":{"form":"full"}}
                    ]}""");
            assertThat(engine.validate(template)).isEmpty();
        }

        @Test
        @DisplayName("模板输出总长度超限被拒绝")
        void totalLengthExceeded() {
            StringBuilder longLiteral = new StringBuilder();
            longLiteral.append("X".repeat(240));
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"LITERAL","params":{"value":"%s"}},
                      {"type":"UUID","name":"u","params":{"form":"full"}}
                    ]}""".formatted(longLiteral.toString()));
            assertThat(engine.validate(template))
                    .anyMatch(e -> e.contains("255"));
        }

        @Test
        @DisplayName("对象缩写段必须位于顺序号段之前")
        void abbreviationMustPrecedeSequence() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"SEQUENCE","name":"seq","params":{"pad":5}},
                      {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{}}
                    ]}""");
            assertThat(engine.validate(template))
                    .anyMatch(e -> e.contains("位于顺序号段之前"));
        }

        @Test
        @DisplayName("对象缩写段必须命名")
        void abbreviationMustBeNamed() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"OBJECT_ABBREVIATION","params":{}},
                      {"type":"SEQUENCE","name":"seq","params":{"pad":5}}
                    ]}""");
            assertThat(engine.validate(template))
                    .anyMatch(e -> e.contains("必须命名"));
        }
    }

    @Nested
    @DisplayName("对象缩写系统级映射表")
    class AbbreviationCatalogBehavior {

        @Test
        @DisplayName("缩写随条目类型经系统映射表取值")
        void abbreviationFromCatalog() {
            SegmentTemplate template = objectSequenceTemplate();
            String de = engine.generate(template,
                    SegmentContext.of("DataElement", Map.of(), new MapAllocator()));
            String cd = engine.generate(template,
                    SegmentContext.of("ConceptualDomain", Map.of(), new MapAllocator()));
            assertThat(de).startsWith("DE.");
            assertThat(cd).startsWith("CD.");
        }

        @Test
        @DisplayName("未命中类型使用 default 兜底")
        void defaultFallback() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{"default":"MD"}},
                      {"type":"SEPARATOR","params":{"char":"."}},
                      {"type":"SEQUENCE","name":"seq","params":{"pad":5}}
                    ]}""");
            String id = engine.generate(template,
                    SegmentContext.of("CompletelyUnknownType", Map.of(), new MapAllocator()));
            assertThat(id).isEqualTo("MD.00001");
        }

        @Test
        @DisplayName("未命中且无 default 报错并指明类型")
        void missingMappingAndDefault() {
            SegmentTemplate template = objectSequenceTemplate();
            assertThatThrownBy(() -> engine.generate(template,
                    SegmentContext.of("CompletelyUnknownType", Map.of(), new MapAllocator())))
                    .isInstanceOf(SegmentTemplateException.class)
                    .hasMessageContaining("CompletelyUnknownType");
        }

        @Test
        @DisplayName("段内 mapping 参数被忽略，以系统映射表为准")
        void legacyMappingParamIgnored() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{"mapping":{"DataElement":"ZZ"}}},
                      {"type":"SEPARATOR","params":{"char":"."}},
                      {"type":"SEQUENCE","name":"seq","params":{"pad":5}}
                    ]}""");
            String id = engine.generate(template,
                    SegmentContext.of("DataElement", Map.of(), new MapAllocator()));
            assertThat(id).startsWith("DE.");
        }
    }

    @Nested
    @DisplayName("变量段")
    class Variables {

        @Test
        @DisplayName("必填变量缺失拒绝并指明段")
        void missingRequiredVariable() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"VARIABLE","name":"deptCode","params":{"label":"科室代码","pattern":"\\\\d{2}","required":true}},
                      {"type":"SEPARATOR","params":{"char":"-"}},
                      {"type":"SEQUENCE","name":"seq","params":{"pad":4}}
                    ]}""");
            assertThatThrownBy(() -> engine.generate(template,
                    SegmentContext.of(null, Map.of(), new MapAllocator())))
                    .isInstanceOf(SegmentTemplateException.class)
                    .hasMessageContaining("deptCode");
        }

        @Test
        @DisplayName("变量值不符合校验正则")
        void variablePatternViolation() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"VARIABLE","name":"deptCode","params":{"label":"科室代码","pattern":"\\\\d{2}"}},
                      {"type":"SEQUENCE","name":"seq","params":{"pad":4}}
                    ]}""");
            assertThatThrownBy(() -> engine.generate(template,
                    SegmentContext.of(null, Map.of("deptCode", "2"), new MapAllocator())))
                    .isInstanceOf(SegmentTemplateException.class)
                    .hasMessageContaining("科室代码");
        }

        @Test
        @DisplayName("默认值与补零")
        void defaultAndPad() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"VARIABLE","name":"code","params":{"pattern":"\\\\d{4}","pad":4,"default":"7"}}
                    ]}""");
            // 无唯一性段，仅验证变量段生成行为（generate 不强制唯一性段规则）
            assertThat(engine.generate(template,
                    SegmentContext.of(null, Map.of(), new MapAllocator()))).isEqualTo("0007");
        }

        @Test
        @DisplayName("maxLength 参数校验")
        void maxLengthValidation() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"VARIABLE","name":"code","params":{"pattern":"\\\\w+","maxLength":0}},
                      {"type":"SEQUENCE","name":"seq","params":{"pad":4}}
                    ]}""");
            assertThat(engine.validate(template)).anyMatch(e -> e.contains("maxLength"));
        }
    }

    @Nested
    @DisplayName("段类型行为")
    class SegmentTypes {

        @Test
        @DisplayName("分隔符字符受限")
        void separatorCharsetRestricted() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"SEPARATOR","params":{"char":"*"}},
                      {"type":"SEQUENCE","name":"seq","params":{"pad":4}}
                    ]}""");
            List<String> errors = engine.validate(template);
            assertThat(errors).anyMatch(e -> e.contains("SEPARATOR") || e.contains("分隔"));
        }

        @Test
        @DisplayName("UUID 段 full/short")
        void uuidForms() {
            SegmentTemplate full = SegmentTemplate.fromJson("""
                    {"segments":[{"type":"UUID","name":"u","params":{"form":"full"}}]}""");
            SegmentTemplate shortForm = SegmentTemplate.fromJson("""
                    {"segments":[{"type":"UUID","name":"u","params":{"form":"short"}}]}""");
            String fullId = engine.generate(full, SegmentContext.of(null, Map.of(), new MapAllocator()));
            String shortId = engine.generate(shortForm, SegmentContext.of(null, Map.of(), new MapAllocator()));
            assertThat(fullId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertThat(shortId).matches("[0-9a-f]{8}");
            assertThat(engine.matchesFormat(shortForm, shortId)).isTrue();
        }

        @Test
        @DisplayName("校验位 luhn 往返")
        void checksumRoundTrip() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"VARIABLE","name":"base","params":{"pattern":"\\\\d{6}"}},
                      {"type":"CHECKSUM","name":"check","params":{"algorithm":"luhn"}},
                      {"type":"SEQUENCE","name":"seq","params":{"pad":3}}
                    ]}""");
            String id = engine.generate(template,
                    SegmentContext.of(null, Map.of("base", "010001"), new MapAllocator()));
            Map<String, String> parsed = engine.parse(template, id);
            assertThat(parsed.get("base")).isEqualTo("010001");
            assertThat(parsed.get("check")).matches("\\d");
        }

        @Test
        @DisplayName("混合分隔符模板")
        void mixedSeparators() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{}},
                      {"type":"SEPARATOR","params":{"char":"-"}},
                      {"type":"SEQUENCE","name":"seq","params":{"pad":5}}
                    ]}""");
            String id = engine.generate(template,
                    SegmentContext.of("DataElement", Map.of(), new MapAllocator()));
            assertThat(id).isEqualTo("DE-00001");
        }

        @Test
        @DisplayName("RANDOM 段被拒绝并提示已移除")
        void randomRejected() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[{"type":"RANDOM","name":"r","params":{"length":6}}]}""");
            List<String> errors = engine.validate(template);
            assertThat(errors).anyMatch(e -> e.contains("RANDOM") && e.contains("已移除"));
            assertThatThrownBy(() -> engine.generate(template,
                    SegmentContext.of(null, Map.of(), new MapAllocator())))
                    .isInstanceOf(SegmentTemplateException.class)
                    .hasMessageContaining("RANDOM");
        }

        @Test
        @DisplayName("时间戳段按 pattern 生成与解析")
        void timestampGeneration() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"LITERAL","params":{"value":"LOG"}},
                      {"type":"SEPARATOR","params":{"char":"-"}},
                      {"type":"TIMESTAMP","name":"ts","params":{"pattern":"yyyyMMddHHmmssSSS"}}
                    ]}""");
            String id = engine.generate(template, SegmentContext.of(null, Map.of(), new MapAllocator()));
            Map<String, String> parsed = engine.parse(template, id);
            assertThat(parsed.get("ts")).matches("\\d{17}");
        }
    }

    @Nested
    @DisplayName("合法模板校验")
    class ValidTemplates {

        @Test
        @DisplayName("默认模板形态无校验错误")
        void defaultTemplateValid() {
            assertThat(engine.validate(objectSequenceTemplate())).isEmpty();
        }

        @Test
        @DisplayName("固定前缀模板形态无校验错误")
        void prefixTemplateValid() {
            assertThat(engine.validate(prefixObjectSequenceTemplate())).isEmpty();
        }

        @Test
        @DisplayName("含变量段与校验位的模板无校验错误")
        void variableAndChecksumTemplateValid() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{}},
                      {"type":"VARIABLE","name":"deptCode","params":{"label":"科室代码","pattern":"\\\\d{2}","maxLength":2}},
                      {"type":"SEPARATOR","params":{"char":"."}},
                      {"type":"SEQUENCE","name":"seq","params":{"pad":5}},
                      {"type":"CHECKSUM","name":"check","params":{"algorithm":"luhn"}}
                    ]}""");
            assertThat(engine.validate(template)).isEmpty();
        }
    }
}
