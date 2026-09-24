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
    public static final int FEATURE_NOT_ENABLED = 1001;

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
    // === Registration Process（registration-process-designer）===
    /** 迁移需经批准：目标迁移配置了 requires_approval，须经批准累积达 quorum 生效。 */
    public static final int TRANSITION_REQUIRES_APPROVAL = 1120;
    /** 该迁移无需审核：对非审核点迁移误用批准操作。 */
    public static final int TRANSITION_APPROVAL_NOT_REQUIRED = 1121;
    /** 重复批准：同一 Registrar 对同一迁移已投出批准。 */
    public static final int DUPLICATE_APPROVAL = 1122;
    /** 未找到注册机构，无法提供注册流程。 */
    public static final int REGISTRATION_AUTHORITY_NOT_FOUND = 1123;
    /** 注册状态不可经编辑表单变更，只能经推进流程（execute/approve）完成。 */
    public static final int STATUS_CHANGE_VIA_FORM_FORBIDDEN = 1124;
    /** 创建时显式初始状态非法：生命周期态只能自 Incomplete 经推进流程获得，仅 APPLICATION/HISTORICAL 可显式标定。 */
    public static final int INVALID_INITIAL_STATUS = 1125;

    // === Workspace / Administrative Status (1130-1139) ===
    /** 无基线快照可回退。 */
    public static final int NO_VERSION_TO_ROLLBACK = 1130;
    /** 当前注册状态或管理状态不允许回退。 */
    public static final int INVALID_ROLLBACK_STATE = 1131;
    /** 驳回时管理项必须处于 PROVISIONALLY_QUALIFIED。 */
    public static final int INVALID_ADMINISTRATIVE_STATUS_FOR_REJECTION = 1132;
    /** 无进行中的 RECORDED→QUALIFIED 申请（无未生效批准记录）。 */
    public static final int NO_PENDING_QUALIFICATION_APPLICATION = 1133;
    /** 当前用户不是该管理项的主管。 */
    public static final int NOT_ITEM_STEWARD = 1134;
    /** Submitter 白名单迁移要求管理项满足 Candidate 最低需求（主管记录非空）。 */
    public static final int CANDIDATE_MINIMUM_REQUIREMENT_NOT_MET = 1135;
    /** Submitter 必须归属本用户（提交记录联系人或主管记录联系人）。 */
    public static final int SUBMITTER_OWNERSHIP_REQUIRED = 1136;
    /** 该管理项已处于 PROVISIONALLY_QUALIFIED，请勿重复申请合格。 */
    public static final int ALREADY_PROVISIONALLY_QUALIFIED = 1137;
    /** 批量提交时管理项必须处于 DRAFT 管理状态。 */
    public static final int WORKSPACE_ITEM_NOT_DRAFT = 1138;
    /** 请求成为候选仅允许 INCOMPLETE 注册状态的管理项。 */
    public static final int WORKSPACE_REQUEST_CANDIDATE_INVALID_STATE = 1139;

}
