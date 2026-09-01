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

    // === Registration Lifecycle State Transition Errors (1100-1199) ===
    public static final int INVALID_STATE_TRANSITION = 1100;
    public static final int ALREADY_IN_TARGET_STATE = 1101;
    public static final int UNAUTHORIZED_STATE_TRANSITION = 1102;
    public static final int TARGET_STATE_REQUIRED = 1103;
    public static final int ADMINISTERED_ITEM_NOT_FOUND = 1104;
    public static final int INVALID_STATE_TRANSITION_FROM_INCOMPLETE = 1110;
    public static final int INVALID_STATE_TRANSITION_FROM_CANDIDATE = 1111;
    public static final int INVALID_STATE_TRANSITION_FROM_RECORDED = 1112;
    public static final int INVALID_STATE_TRANSITION_FROM_QUALIFIED = 1113;
    public static final int INVALID_STATE_TRANSITION_FROM_STANDARD = 1114;
    public static final int INVALID_STATE_TRANSITION_FROM_PREFERRED_STANDARD = 1115;
    public static final int INVALID_STATE_TRANSITION_FROM_SUPERSEDED = 1116;
    public static final int INVALID_STATE_TRANSITION_FROM_RETIRED = 1117;
    public static final int INVALID_STATE_TRANSITION_FROM_HISTORICAL = 1118;
    public static final int INVALID_STATE_TRANSITION_FROM_APPLICATION = 1119;

}
