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

    // === Approval Workflow Errors (1200-1299) ===
    public static final int APPROVAL_RECORD_NOT_FOUND = 1200;
    public static final int APPROVAL_INVALID_STATE = 1201;
    public static final int APPROVAL_ALREADY_PENDING = 1202;
    public static final int APPROVAL_NOT_PENDING = 1203;
    public static final int APPROVAL_OPINION_TOO_LONG = 1204;
    public static final int APPROVAL_NOT_REJECTED = 1205;
    public static final int APPROVAL_CANNOT_SUBMIT = 1206;

    // === Multi-Level Approval Workflow Errors (1300-1399) ===
    public static final int APPROVAL_TEMPLATE_NOT_FOUND = 1300;
    public static final int APPROVAL_TEMPLATE_LOCKED = 1301;
    public static final int APPROVAL_TEMPLATE_NAME_DUPLICATE = 1302;
    public static final int APPROVAL_STEP_NOT_FOUND = 1303;
    public static final int APPROVAL_PROCESS_INSTANCE_NOT_FOUND = 1304;
    public static final int APPROVAL_PROCESS_ALREADY_PENDING = 1305;
    public static final int APPROVAL_STEP_NOT_PENDING = 1306;
    public static final int APPROVAL_STEP_NOT_CURRENT = 1307;
    public static final int APPROVAL_UNAUTHORIZED_STEP = 1308;
    public static final int APPROVAL_INVALID_TEMPLATE = 1309;
    public static final int APPROVAL_TRANSFER_FAILED = 1310;
    public static final int APPROVAL_TIMEOUT_NOT_CONFIGURED = 1311;
}
