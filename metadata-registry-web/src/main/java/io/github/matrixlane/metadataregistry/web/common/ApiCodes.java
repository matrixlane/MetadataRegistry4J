package io.github.matrixlane.metadataregistry.web.common;

public final class ApiCodes {

    private ApiCodes() {
    }

    public static final int OK = 0;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    public static final int VALIDATION_FAILED = 422;
    public static final int INTERNAL_ERROR = 500;
    public static final int BUSINESS_ERROR = 1000;
}
