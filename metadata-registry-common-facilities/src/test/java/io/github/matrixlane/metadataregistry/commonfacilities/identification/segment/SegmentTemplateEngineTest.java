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
 * Segment engine unit tests: segment types, scope combinations, overflow,
 * generate/parse round-trip, and the WS/T 363 golden sample.
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

        long currentOf(String scopeKey) {
            AtomicLong c = counters.get(scopeKey);
            return c == null ? -1 : c.get();
        }
    }

    private static SegmentTemplate wst363Template() {
        return SegmentTemplate.fromJson("""
                {"segments":[
                  {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{"mapping":{"DataElement":"DE","ValueDomain":"CV"}}},
                  {"type":"VARIABLE","name":"majorCategory","params":{"label":"大类编码","pattern":"\\\\d{2}","required":true}},
                  {"type":"SEPARATOR","params":{"char":"."}},
                  {"type":"VARIABLE","name":"minorCategory","params":{"label":"小类编码","pattern":"\\\\d{2}","required":true}},
                  {"type":"SEPARATOR","params":{"char":"."}},
                  {"type":"SEQUENCE","name":"seq","params":{"start":1,"increment":1,"pad":3,"scopeRefs":["majorCategory","minorCategory"],"overflow":"REJECT"}},
                  {"type":"SEPARATOR","params":{"char":"."}},
                  {"type":"VARIABLE","name":"suffixCode","params":{"label":"附加码","pattern":"\\\\d{2}","default":"00","required":false}}
                ]}""");
    }

    private static Map<String, String> vars(String major, String minor) {
        Map<String, String> vars = new HashMap<>();
        vars.put("majorCategory", major);
        vars.put("minorCategory", minor);
        return vars;
    }

    @Nested
    @DisplayName("WS/T 363 金样本")
    class Wst363GoldenSample {

        @Test
        @DisplayName("DE01.00.001.00 生成")
        void generatesGoldenIdentifier() {
            SegmentTemplate template = wst363Template();
            String identifier = engine.generate(template,
                    SegmentContext.of("DataElement", vars("01", "00"), new MapAllocator()));
            assertThat(identifier).isEqualTo("DE01.00.001.00");
        }

        @Test
        @DisplayName("生成与解析往返一致")
        void roundTrip() {
            SegmentTemplate template = wst363Template();
            String identifier = engine.generate(template,
                    SegmentContext.of("DataElement", vars("01", "00"), new MapAllocator()));
            Map<String, String> parsed = engine.parse(template, identifier);
            assertThat(parsed)
                    .containsEntry("objectAbbr", "DE")
                    .containsEntry("majorCategory", "01")
                    .containsEntry("minorCategory", "00")
                    .containsEntry("seq", "001")
                    .containsEntry("suffixCode", "00");
        }

        @Test
        @DisplayName("序号回填后从已用最大值继续")
        void backfillAlignment() {
            SegmentTemplate template = wst363Template();
            MapAllocator allocator = new MapAllocator();
            // 模拟导入 DE01.00.001.00 .. DE01.00.026.00 后回填计数器
            allocator.counters.put("01.00", new AtomicLong(26));

            String identifier = engine.generate(template,
                    SegmentContext.of("DataElement", vars("01", "00"), allocator));
            assertThat(identifier).isEqualTo("DE01.00.027.00");
        }

        @Test
        @DisplayName("对象缩写随条目类型取值")
        void objectAbbreviationByItemType() {
            SegmentTemplate template = wst363Template();
            String de = engine.generate(template,
                    SegmentContext.of("DataElement", vars("02", "01"), new MapAllocator()));
            String cv = engine.generate(template,
                    SegmentContext.of("ValueDomain", vars("02", "01"), new MapAllocator()));
            assertThat(de).startsWith("DE02.01.");
            assertThat(cv).startsWith("CV02.01.");
        }
    }

    @Nested
    @DisplayName("顺序号作用域")
    class SequenceScopes {

        @Test
        @DisplayName("按大类.小类各自计数")
        void scopesCountIndependently() {
            SegmentTemplate template = wst363Template();
            MapAllocator allocator = new MapAllocator();
            String a = engine.generate(template,
                    SegmentContext.of("DataElement", vars("02", "01"), allocator));
            String b = engine.generate(template,
                    SegmentContext.of("DataElement", vars("02", "10"), allocator));
            assertThat(a).isEqualTo("DE02.01.001.00");
            assertThat(b).isEqualTo("DE02.10.001.00");
        }

        @Test
        @DisplayName("无引用段为命名空间级全局计数")
        void globalScopeWithoutRefs() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"LITERAL","params":{"value":"CO"}},
                      {"type":"SEPARATOR","params":{"char":"-"}},
                      {"type":"SEQUENCE","name":"seq","params":{"start":1,"increment":1,"pad":6}}
                    ]}""");
            MapAllocator allocator = new MapAllocator();
            String first = engine.generate(template, SegmentContext.of(null, Map.of(), allocator));
            String second = engine.generate(template, SegmentContext.of(null, Map.of(), allocator));
            assertThat(first).isEqualTo("CO-000001");
            assertThat(second).isEqualTo("CO-000002");
            assertThat(allocator.currentOf("__GLOBAL__")).isEqualTo(2);
        }

        @Test
        @DisplayName("按月重置的流水号作用域")
        void monthlyScopedSequence() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"TIMESTAMP","name":"month","params":{"pattern":"yyyyMM"}},
                      {"type":"SEPARATOR","params":{"char":"-"}},
                      {"type":"SEQUENCE","name":"seq","params":{"start":1,"increment":1,"pad":4,"scopeRefs":["month"]}}
                    ]}""");
            MapAllocator allocator = new MapAllocator();
            String id = engine.generate(template, SegmentContext.of(null, Map.of(), allocator));
            Map<String, String> parsed = engine.parse(template, id);
            assertThat(parsed.get("month")).matches("\\d{6}");
            assertThat(parsed.get("seq")).isEqualTo("0001");
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
    @DisplayName("变量段")
    class Variables {

        @Test
        @DisplayName("必填变量缺失拒绝并指明段")
        void missingRequiredVariable() {
            SegmentTemplate template = wst363Template();
            assertThatThrownBy(() -> engine.generate(template,
                    SegmentContext.of("DataElement", Map.of("minorCategory", "00"), new MapAllocator())))
                    .isInstanceOf(SegmentTemplateException.class)
                    .hasMessageContaining("majorCategory");
        }

        @Test
        @DisplayName("变量值不符合校验正则")
        void variablePatternViolation() {
            SegmentTemplate template = wst363Template();
            Map<String, String> bad = new HashMap<>();
            bad.put("majorCategory", "2");
            bad.put("minorCategory", "00");
            assertThatThrownBy(() -> engine.generate(template,
                    SegmentContext.of("DataElement", bad, new MapAllocator())))
                    .isInstanceOf(SegmentTemplateException.class)
                    .hasMessageContaining("大类编码");
        }

        @Test
        @DisplayName("默认值与补零")
        void defaultAndPad() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"VARIABLE","name":"code","params":{"pattern":"\\\\d{4}","pad":4,"default":"7"}}
                    ]}""");
            assertThat(engine.generate(template,
                    SegmentContext.of(null, Map.of(), new MapAllocator()))).isEqualTo("0007");
        }
    }

    @Nested
    @DisplayName("段类型行为")
    class SegmentTypes {

        @Test
        @DisplayName("分隔符字符受限")
        void separatorCharsetRestricted() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[{"type":"SEPARATOR","params":{"char":"*"}}]}""");
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
                      {"type":"CHECKSUM","name":"check","params":{"algorithm":"luhn"}}
                    ]}""");
            String id = engine.generate(template,
                    SegmentContext.of(null, Map.of("base", "010001"), new MapAllocator()));
            assertThat(id).matches("\\d{7}");
            Map<String, String> parsed = engine.parse(template, id);
            assertThat(parsed.get("base")).isEqualTo("010001");
        }

        @Test
        @DisplayName("混合分隔符模板")
        void mixedSeparators() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"LITERAL","params":{"value":"DE"}},
                      {"type":"VARIABLE","name":"a","params":{"pattern":"\\\\d{2}"}},
                      {"type":"SEPARATOR","params":{"char":"."}},
                      {"type":"VARIABLE","name":"b","params":{"pattern":"\\\\d{2}"}},
                      {"type":"SEPARATOR","params":{"char":"-"}},
                      {"type":"SEQUENCE","name":"seq","params":{"pad":3}}
                    ]}""");
            Map<String, String> vars = new HashMap<>();
            vars.put("a", "02");
            vars.put("b", "01");
            String id = engine.generate(template, SegmentContext.of(null, vars, new MapAllocator()));
            assertThat(id).isEqualTo("DE02.01-001");
        }

        @Test
        @DisplayName("RANDOM 段定长")
        void randomLength() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[{"type":"RANDOM","name":"r","params":{"length":6}}]}""");
            String id = engine.generate(template, SegmentContext.of(null, Map.of(), new MapAllocator()));
            assertThat(id).hasSize(6);
            assertThat(engine.matchesFormat(template, id)).isTrue();
        }
    }

    @Nested
    @DisplayName("模板校验与预览")
    class ValidationAndPreview {

        @Test
        @DisplayName("scopeRef 引用不存在的段名")
        void invalidScopeRef() {
            SegmentTemplate template = SegmentTemplate.fromJson("""
                    {"segments":[
                      {"type":"SEQUENCE","name":"seq","params":{"pad":3,"scopeRefs":["missing"]}}
                    ]}""");
            List<String> errors = engine.validate(template);
            assertThat(errors).anyMatch(e -> e.contains("missing"));
        }

        @Test
        @DisplayName("预览不产生序号分配")
        void previewDoesNotAllocate() {
            SegmentTemplate template = wst363Template();
            String sample = engine.preview(template, "DataElement", vars("01", "00"));
            assertThat(sample).isEqualTo("DE01.00.001.00");
        }

        @Test
        @DisplayName("格式正则派生")
        void derivedRegex() {
            Pattern pattern = engine.deriveFormatPattern(wst363Template());
            assertThat(pattern.matcher("DE01.00.001.00").matches()).isTrue();
            assertThat(pattern.matcher("DE01.0.001").matches()).isFalse();
            assertThat(pattern.matcher("XX01.00.001.00").matches()).isFalse();
        }

        @Test
        @DisplayName("合法模板无校验错误")
        void validTemplateHasNoErrors() {
            assertThat(engine.validate(wst363Template())).isEmpty();
        }
    }
}
