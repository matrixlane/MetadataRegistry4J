package io.github.matrixlane.metadataregistry.web.inference;

import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InferenceCandidateJsonExtractorTest {

    private final CandidateJsonExtractor extractor = new CandidateJsonExtractor();

    @Test
    void extract_plainJson_returnsCandidates() {
        String raw = "{\"candidates\":[{\"field\":\"definition\",\"language\":\"zh-CN\","
                + "\"text\":\"患者的唯一标识\",\"rationale\":\"与指称一致\"}]}";

        List<InferenceCandidate> candidates = extractor.extract(raw);

        assertThat(candidates).hasSize(1);
        InferenceCandidate candidate = candidates.get(0);
        assertThat(candidate.field()).isEqualTo("definition");
        assertThat(candidate.language()).isEqualTo("zh-CN");
        assertThat(candidate.text()).isEqualTo("患者的唯一标识");
        assertThat(candidate.rationale()).isEqualTo("与指称一致");
    }

    @Test
    void extract_withSurroundingLogText_findsFirstBalancedObject() {
        String raw = "Thinking...\n[info] loading tools\n"
                + "{\"candidates\":[{\"field\":\"designation\",\"text\":\"患者标识\",\"rationale\":\"简洁单义\"}]}\n"
                + "Done. 进程正常结束";

        List<InferenceCandidate> candidates = extractor.extract(raw);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).field()).isEqualTo("designation");
    }

    @Test
    void extract_bracesInsideString_areNotCountedForBalance() {
        String raw = "{\"candidates\":[{\"field\":\"definition\",\"text\":\"格式如 {yyyy-MM-dd} 的日期\","
                + "\"rationale\":\"含转义引号 \\\"{}\\\" 与 { 括号\"}]}";

        List<InferenceCandidate> candidates = extractor.extract(raw);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).text()).contains("{yyyy-MM-dd}");
    }

    @Test
    void extract_escapedQuoteBeforeBrace_doesNotEndString() {
        // 字符串内含转义双引号后跟 }，若误把转义引号当作字符串结束会破坏配平
        String raw = "{\"candidates\":[{\"field\":\"designation\",\"text\":\"名称为 \\\"}\\\" 的值\"}]}";

        List<InferenceCandidate> candidates = extractor.extract(raw);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).text()).isEqualTo("名称为 \"}\" 的值");
    }

    @Test
    void extract_textBeforeFirstObject_containsNoObject_throwsWithSnippet() {
        String raw = "just some log output without any json";

        assertThatThrownBy(() -> extractor.extract(raw))
                .isInstanceOf(InferenceException.class)
                .hasMessageContaining("原文片段")
                .hasMessageContaining("just some log output");
    }

    @Test
    void extract_unbalancedObject_treatsAsUnparseable() {
        String raw = "start {\"candidates\":[{\"field\":\"definition\",\"text\":\"截断的输出";

        assertThatThrownBy(() -> extractor.extract(raw))
                .isInstanceOf(InferenceException.class)
                .hasMessageContaining("不存在括号配平");
    }

    @Test
    void extract_missingCandidatesField_throws() {
        String raw = "{\"result\":\"ok\"}";

        assertThatThrownBy(() -> extractor.extract(raw))
                .isInstanceOf(InferenceException.class)
                .hasMessageContaining("candidates");
    }

    @Test
    void extract_unknownFields_areIgnored() {
        String raw = "{\"candidates\":[{\"field\":\"definition\",\"text\":\"定义\","
                + "\"rationale\":\"理由\",\"extra\":\"ignored\"}],\"traceId\":\"abc\"}";

        List<InferenceCandidate> candidates = extractor.extract(raw);

        assertThat(candidates).hasSize(1);
    }

    @Test
    void extract_designationCandidate_keepsContextAndLanguage() {
        String raw = "{\"candidates\":[{\"field\":\"designation\",\"language\":\"en-US\","
                + "\"context\":\"临床文档\",\"text\":\"patient identifier\",\"rationale\":\"en 命名\"}]}";

        InferenceCandidate candidate = extractor.extract(raw).get(0);

        assertThat(candidate.context()).isEqualTo("临床文档");
        assertThat(candidate.language()).isEqualTo("en-US");
    }

    @Test
    void extract_failureSnippet_isTruncatedTo2000Chars() {
        String longLog = "x".repeat(5000);
        String raw = "{\"broken\": " + longLog;

        assertThatThrownBy(() -> extractor.extract(raw))
                .isInstanceOf(InferenceException.class)
                .satisfies(e -> {
                    String message = e.getMessage();
                    int snippetStart = message.indexOf("原文片段：");
                    String snippet = message.substring(snippetStart);
                    assertThat(snippet.length()).isLessThanOrEqualTo("原文片段：".length() + 2000 + 10);
                    assertThat(snippet).contains("已截断");
                });
    }

    @Test
    void extract_nullOutput_throws() {
        assertThatThrownBy(() -> extractor.extract(null))
                .isInstanceOf(InferenceException.class);
    }

    @Test
    void extract_promptEchoWithContractExample_skipsPlaceholderAndReturnsRealAnswer() {
        // codex exec 等 CLI 的 stdout 会先回显完整提示词（含输出契约示例 "text":"..."），
        // 真实答案在其后；提取器必须跳过回显占位对象
        String promptEcho = "user\n……只输出一个 JSON 对象……结构如下：\n"
                + "{\"candidates\":[{\"field\":\"definition\",\"language\":\"zh-CN\",\"text\":\"...\",\"rationale\":\"...\"},"
                + "{\"field\":\"designation\",\"language\":\"en-US\",\"context\":\"...\",\"text\":\"...\",\"rationale\":\"...\"}]}\n"
                + "field 取值只能是要推断的字段名；\n";
        String realAnswer = "codex\n"
                + "{\"candidates\":[{\"field\":\"designation\",\"language\":\"zh-CN\",\"context\":\"临床检验\","
                + "\"text\":\"标本接收日期时间\",\"rationale\":\"与定义一致\"}]}\n"
                + "tokens used\n716\n";
        List<InferenceCandidate> candidates = extractor.extract(promptEcho + realAnswer);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).text()).isEqualTo("标本接收日期时间");
    }

    @Test
    void extract_onlyPlaceholderEcho_throws() {
        String raw = "user\n……结构如下：\n"
                + "{\"candidates\":[{\"field\":\"definition\",\"language\":\"zh-CN\",\"text\":\"...\",\"rationale\":\"...\"}]}\n";

        assertThatThrownBy(() -> extractor.extract(raw))
                .isInstanceOf(InferenceException.class)
                .hasMessageContaining("占位");
    }

    @Test
    void extract_placeholderEchoThenEmptyCandidates_returnsEmpty() {
        String raw = "{\"candidates\":[{\"field\":\"definition\",\"text\":\"...\",\"rationale\":\"...\"}]}\n"
                + "{\"candidates\":[]}\n";

        assertThat(extractor.extract(raw)).isEmpty();
    }
}
