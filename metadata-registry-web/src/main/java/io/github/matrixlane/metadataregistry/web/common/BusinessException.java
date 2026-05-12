package io.github.matrixlane.metadataregistry.web.common;

public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(ApiCodes.BUSINESS_ERROR, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
