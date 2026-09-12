package io.github.matrixlane.metadataregistry.web.inference;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 候选 JSON 宽容提取器：从提供方的原始输出（ACP 会话累积文本）中截取首个
 * 括号配平的 {@code {...}} 片段（容忍头尾附加的日志或包装文本），反序列化为候选列表。
 *
 * <p>解析失败时抛出 {@link InferenceException}，消息携带截断至 2000 字符的原文片段。
 */
@Component
public class CandidateJsonExtractor {

    private static final int SNIPPET_LIMIT = 2000;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 从原始输出提取候选列表。
     *
     * <p>原始输出常包含提示词回显（输出契约示例 {@code "text":"..."} 占位）。
     * 因此遍历全部括号配平对象，
     * 跳过「全体候选均为占位内容」的回显对象，返回首个含真实候选文本的对象；
     * 只有占位/回显对象时抛出异常，空 candidates 数组视为合法空结果。
     *
     * @param rawOutput ACP 会话累积文本
     * @return 候选列表（可能为空）
     * @throws InferenceException 输出中不存在可解析的候选 JSON 对象
     */
    public List<InferenceCandidate> extract(String rawOutput) {
        List<InferenceCandidate> emptyResult = null;
        boolean sawPlaceholderOnly = false;
        for (String json : extractBalancedObjects(rawOutput)) {
            CandidateList candidateList = tryParse(json);
            if (candidateList == null || candidateList.candidates() == null) {
                continue;
            }
            List<InferenceCandidate> candidates = candidateList.candidates();
            if (candidates.stream().anyMatch(CandidateJsonExtractor::hasRealText)) {
                return candidates;
            }
            if (candidates.isEmpty()) {
                emptyResult = candidates;
            } else {
                sawPlaceholderOnly = true;
            }
        }
        if (emptyResult != null) {
            return emptyResult;
        }
        if (sawPlaceholderOnly) {
            throw new InferenceException("输出中仅发现提示词回显的占位候选（text 为 \"...\"），未发现真实候选，原文片段：\n"
                    + snippet(rawOutput));
        }
        throw new InferenceException("输出中不存在可解析的候选 JSON 对象（缺少 candidates 数组），原文片段：\n" + snippet(rawOutput));
    }

    /** 候选含真实文本（非空且非契约占位符 "..."）。 */
    private static boolean hasRealText(InferenceCandidate candidate) {
        if (candidate == null || candidate.text() == null) {
            return false;
        }
        String text = candidate.text().trim();
        return !text.isEmpty() && !"...".equals(text);
    }

    private CandidateList tryParse(String json) {
        try {
            return objectMapper.readValue(json, CandidateList.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 截取文本中全部括号配平的顶层 {@code {...}} 片段；正确处理字符串字面量内的
     * 括号与转义引号。找不到任何配平对象时抛出 {@link InferenceException}。
     */
    List<String> extractBalancedObjects(String text) {
        if (text == null) {
            throw new InferenceException("输出为空，无法提取候选 JSON");
        }
        List<String> objects = new ArrayList<>();
        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(text.substring(start, i + 1));
                    start = -1;
                }
                if (depth < 0) {
                    depth = 0;
                }
            }
        }
        if (objects.isEmpty()) {
            throw new InferenceException("输出中不存在括号配平的 JSON 对象，原文片段：\n" + snippet(text));
        }
        return objects;
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= SNIPPET_LIMIT ? text : text.substring(0, SNIPPET_LIMIT) + "…（已截断）";
    }

    /** D6 输出契约的顶层结构。 */
    record CandidateList(List<InferenceCandidate> candidates) {
    }
}
