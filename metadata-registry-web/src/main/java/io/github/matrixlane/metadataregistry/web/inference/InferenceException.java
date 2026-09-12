package io.github.matrixlane.metadataregistry.web.inference;

/**
 * 推断执行失败异常。消息中可能携带截断后的原始输出片段，便于排查。
 */
public class InferenceException extends RuntimeException {

    public InferenceException(String message) {
        super(message);
    }

    public InferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
