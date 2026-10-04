export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PageQuery {
  page?: number;
  size?: number;
  sort?: string;
  q?: string;
  enabled?: boolean;
  /** 注册状态过滤（注册项列表，后端 GET /administered-items 已支持） */
  status?: string;
}

export interface ScopedIdentifierDto {
  id: string;
  identifier: string;
  version: string;
}

export interface DesignationDto {
  sign: string;
  languageIdentifier: string;
}

export interface DefinitionDto {
  text: string;
  languageIdentifier: string;
}

export interface RegistrationStateDto {
  status: string;
  effectiveDate: string;
  note?: string;
}

export type RegistrationStatus =
  | 'INCOMPLETE'
  | 'CANDIDATE'
  | 'RECORDED'
  | 'QUALIFIED'
  | 'STANDARD'
  | 'PREFERRED_STANDARD'
  | 'SUPERSEDED'
  | 'RETIRED'
  | 'HISTORICAL'
  | 'APPLICATION';

export type WorkspaceItemView = 'DRAFTS' | 'PENDING' | 'REJECTED';

export interface WorkspaceSummaryResponse {
  drafts: number;
  pending: number;
  rejected: number;
}

export interface WorkspaceItemFlags {
  canSubmit: boolean;
  canRequestCandidate: boolean;
  canRequestQualification: boolean;
  canApprove: boolean;
  canReject: boolean;
  canRollback: boolean;
}

export interface WorkspaceItemResponse {
  itemId: string;
  itemType: string;
  label: string;
  registrationStatus: RegistrationStatus;
  administrativeStatus: string | null;
  version: string;
  hasRollbackBaseline: boolean;
  flags: WorkspaceItemFlags;
}

export interface WorkspaceSubmitRequest {
  itemIds: string[];
  submission?: SubmissionInfo | null;
}

export interface WorkspaceSubmitResult {
  itemId: string;
  success: boolean;
  errorCode: number | null;
  message: string | null;
}

export type ElTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger';

// ── Registration Process（registration-process-designer）──────────────────

/** GET /registration-process 返回的迁移规则（按注册机构作用域） */
export interface RegistrationProcessTransition {
  from: RegistrationStatus;
  to: RegistrationStatus;
  /** 操作命名；空 = 用默认"迁移到 X" */
  displayName?: string | null;
  requiresApproval: boolean;
  approverMode: 'ALL_REGISTRARS' | 'SELECTED_REGISTRARS';
  quorumType: 'ANY_ONE' | 'AT_LEAST';
  quorumCount?: number | null;
  approverRegistrarIds: string[];
  enabled: boolean;
}

export interface RegistrationProcessDefinition {
  authorityId: string;
  transitions: RegistrationProcessTransition[];
}

/** 注册流程画布布局（保存/预加载的开放式 JSON；缺失项回落内置默认） */
export interface RegistrationProcessLayoutNode {
  x: number;
  y: number;
}

/** 正交连线折线段微调（绝对画布坐标） */
export interface RegistrationProcessEdgeOverride {
  laneY?: number;
  sx?: number;
  tx?: number;
}

export interface RegistrationProcessLayout {
  nodes?: Record<string, RegistrationProcessLayoutNode>;
  edgeOverrides?: Record<string, RegistrationProcessEdgeOverride>;
}

/** GET /administered-items/{id}/transitions 返回的单条可执行迁移视图 */
export interface ItemTransitionView {
  to: RegistrationStatus;
  displayName?: string | null;
  requiresApproval: boolean;
  approvedCount: number;
  /** 通过人数（ANY_ONE 时为 1；AT_LEAST 时为 quorumCount） */
  quorum: number;
  canExecute: boolean;
  canApprove: boolean;
  approvedByMe: boolean;
}

/**
 * 指称/定义上下文自引用哨兵（与后端 ItemAdministrationService.SELF_CONTEXT_REFERENCE 一致）：
 * 新建上下文时条目尚无真实 id，以此 NIL UUID 表示"关联到条目自身"。
 */
export const SELF_CONTEXT_REFERENCE = '00000000-0000-0000-0000-000000000000';

export interface TextLineContextForm {
  contextId: string;
  acceptability: 'PREFERRED' | 'ADMITTED' | 'DEPRECATED' | 'SUPERSEDED' | 'OBSOLETE';
}

export interface TextLineForm {
  id?: string;
  /** 新建（未持久化）行的前端临时键，供指称-定义配对在保存前引用；保存后映射为真实 id。 */
  clientKey?: string;
  languageId: string;
  value: string;
  sourceId?: string;
  contexts?: TextLineContextForm[];
  namingConventionIds?: string[];
  isolatedException?: boolean;
}

export interface PairingForm {
  id?: string;
  designationId: string;
  definitionId: string;
  contextId: string;
  acceptability: 'PREFERRED' | 'ADMITTED' | 'DEPRECATED' | 'SUPERSEDED' | 'OBSOLETE';
}

export interface ItemRelationshipContextResponse {
  id: string;
  label: string;
}

export interface ItemRelationshipResponse {
  designations: TextLineResponse[];
  definitions: TextLineResponse[];
  contexts: ItemRelationshipContextResponse[];
  pairings: PairingForm[];
}

export interface ContextUsageAssociation {
  associationType: 'DESIGNATION' | 'DEFINITION' | 'PAIRING';
  designationId?: string;
  designationValue?: string;
  definitionId?: string;
  definitionValue?: string;
  acceptability?: 'PREFERRED' | 'ADMITTED' | 'DEPRECATED' | 'SUPERSEDED' | 'OBSOLETE';
}

export interface ContextUsageItem {
  itemId: string;
  itemLabel?: string;
  associations: ContextUsageAssociation[];
}

export interface ContextUsageGroup {
  itemType: string;
  itemTypeLabel: string;
  items: ContextUsageItem[];
}

export interface ContextUsageResponse {
  groups: ContextUsageGroup[];
  truncated: boolean;
}

export interface UserRoleAssignmentDto {
  role: 'ADMIN' | 'REGISTRAR' | 'VIEWER';
  registryId?: string;
  namespaceId?: string;
}

// ─── Namespace / NamingConvention shared DTOs ────────────────────────────
// Aligned with backend DTOs (NamespaceRequest/Response, NamingConventionRequest/Response).

export interface ItemMetaResponse {
  itemId?: string;
  preferredDesignation?: string;
  preferredDefinition?: string;
  scopedIdentifier?: string;
  namespaceId?: string;
  namespaceLabel?: string;
  identifier?: string;
  version?: string;
  registrationStatus?: string;
  administeredItemId?: string;
  registrationAuthorityId?: string;
  registrationAuthorityName?: string;
  /** Registration_State（ISO/IEC 11179-3:2023 表 22）回显字段 */
  stateEffectiveDate?: string;
  stateUntilDate?: string;
  administrativeStatus?: string;
  administrativeNote?: string;
  unresolvedIssue?: string;
  /** Stewardship_Record 回显字段 */
  stewardshipRecordId?: string;
  stewardshipContactId?: string;
  stewardshipOrganizationId?: string;
}

export interface TextLineContextResponse {
  id?: string;
  contextId: string;
  acceptability: 'PREFERRED' | 'ADMITTED' | 'DEPRECATED' | 'SUPERSEDED' | 'OBSOLETE';
}

export interface TextLineResponse {
  id?: string;
  languageId: string;
  languageCode?: string;
  value: string;
  sourceId?: string;
  sourceTitle?: string;
  contexts?: TextLineContextResponse[];
}

export interface ItemRegistrationRequest {
  namespaceId?: string;
  identifier?: string;
  version?: string;
  designations?: TextLineForm[];
  definitions?: TextLineForm[];
  /** Registration_State（ISO/IEC 11179-3:2023 表 22）属性；缺省/null = 不变更 */
  registrationStatus?: string | null;
  administrativeStatus?: string | null;
  effectiveDate?: string | null;
  untilDate?: string | null;
  administrativeNote?: string | null;
  unresolvedIssue?: string | null;
  /** 主管信息；缺省/null = 不变更 */
  stewardship?: {
    contactId?: string | null;
    organizationId?: string | null;
  } | null;
}

// ── Active Registrar（active-registrar-ownership）───────────────────────

export interface RegistrarIdentity {
  registrarId: string;
  registrationAuthorityId: string;
  registrationAuthorityName?: string;
}

export interface ActiveRegistrarResponse {
  active?: RegistrarIdentity | null;
  identities: RegistrarIdentity[];
}

// ── Namespace DTOs ──────────────────────────────────────────────────────

export interface NamespaceResponse {
  id: string;
  label?: string;
  namingAuthorityId?: string;
  namingAuthorityName?: string;
  registrationAuthorityId?: string;
  designations?: TextLineResponse[];
  definitions?: TextLineResponse[];
  shorthandPrefix?: string;
  schemeReference?: string;
  mandatoryNamingConventionIndicator?: boolean;
  namingConventionIds?: string[];
  oneNamePerItemIndicator?: boolean;
  oneItemPerNameIndicator?: boolean;
  identifierInputMode?: string;
  versionInputMode?: string;
  versioningPolicy?: string;
  manualIdentifierPattern?: string;
  identifierStrategy?: string;
  strategyPrefix?: string;
  strategyTemplate?: string;
  strategySeqStart?: number;
  strategySeqPad?: number;
}

export type NamespaceDetailResponse = NamespaceResponse;

export interface NamespaceRequest {
  namingAuthorityId?: string;
  designations: TextLineForm[];
  definitions?: TextLineForm[];
  shorthandPrefix?: string;
  schemeReference?: string;
  mandatoryNamingConventionIndicator?: boolean;
  namingConventionIds?: string[];
  oneNamePerItemIndicator?: boolean;
  oneItemPerNameIndicator?: boolean;
  identifierInputMode?: string;
  versionInputMode?: string;
  versioningPolicy?: string;
  manualIdentifierPattern?: string;
  identifierStrategy?: string;
  strategyPrefix?: string;
  strategyTemplate?: string;
  strategySeqStart?: number;
  strategySeqPad?: number;
}

// ── NamingConvention DTOs ──────────────────────────────────────────────

export interface NamingConventionResponse {
  id: string;
  label?: string;
  scopeRule?: string;
  authorityRule?: string;
  designations?: TextLineResponse[];
  definitions?: TextLineResponse[];
  registrationStatus?: string;
  meta?: {
    identifier?: string;
    registrationStatus?: string;
  };
}

export interface NamingConventionRequest {
  scopeRule: string;
  authorityRule?: string;
  designations: TextLineForm[];
  definitions?: TextLineForm[];
  registration?: {
    identifier?: string;
    version?: string;
  };
}

// ── Organization / Role DTOs ───────────────────────────────────────────

export interface OrganizationResponse {
  id: string;
  name?: string;
  englishName?: string;
  additionalNames?: string[];
  mailAddress?: string;
  emailAddress?: string[];
  phoneNumber?: string[];
  webAddress?: string;
}

export interface AppUserDto {
  id: string;
  username: string;
  sysRole?: 'SUPER_ADMIN' | null;
  isRegistrar?: boolean;
  enabled: boolean;
  createdAt?: string;
  lastLoginAt?: string;
  individualId?: string | null;
  forceChangePassword?: boolean;
  emergencyLogin?: boolean;
  tempPasswordUsed?: boolean;
  /** 是否已完善个人/联系人信息 */
  hasContact?: boolean;
  /** 是否已持有 Registrar 身份 */
  hasRegistrar?: boolean;
  /** 是否提示首登用户完善联系人信息 */
  profilePrompt?: boolean;
  // Keep legacy properties as optional to prevent compilation issues
  displayName?: string;
  email?: string;
  role?: 'ADMIN' | 'REGISTRAR' | 'VIEWER';
  roleAssignments?: UserRoleAssignmentDto[];
}

/** GET /api/v1/roles 返回的角色 */
export interface RoleResponse {
  id: string;
  roleName?: string;
  description?: string;
}

/** GET /api/v1/my/contacts 返回的联系人身份 */
export interface MyContactResponse {
  id: string;
  organizationId: string;
  organizationName?: string;
  individualId?: string;
  individualName?: string;
  roleId?: string;
  roleName?: string;
  isRegistrar: boolean;
  registrationAuthorityId?: string;
  registrationAuthorityName?: string;
  isDefault: boolean;
}

/** 注册请求中携带的提交组织/联系人信息 */
export interface SubmissionInfo {
  contactId?: string | null;
  organizationId?: string | null;
}

/** GET /api/v1/registration-authorities/{id}/deletion-preview 返回的删除预览（级联删除计数） */
export interface RegistrationAuthorityDeletionPreview {
  registrationAuthorityId: string;
  administeredItemCount: number;
  submissionRecordCount: number;
  registrarCount: number;
  namespaceCount: number;
  stewardshipRecordCount: number;
  transitionRuleCount: number;
}

/** SubmissionRecord 来源枚举（align-submission-record-to-iso） */
export type SubmissionRecordSource = 'USER_SUBMISSION' | 'SYSTEM_DRAFT';

/**
 * 提交记录概要（ItemMetaResponse.submissionRecordSummaries 行项）：只读展示历史提交。
 * USER_SUBMISSION 工作空间提交产生；SYSTEM_DRAFT 历史脏数据迁移标注。
 */
export interface SubmissionRecordSummary {
  id: string;
  source: SubmissionRecordSource;
  contactLabel: string | null;
  organizationLabel: string | null;
  registeredItemCount: number;
  registeredItemIds: string[];
}

// ─── smart-item-picker ────────────────────────────────────────────────

/**
 * 跨实体统一元数据搜索结果行（SmartItemPicker 消费）。
 *
 * 后端 GET /api/v1/metadata/search 返回的 AdministeredItemResponse 经前端
 * 精简成 picker 关心的字段；type / matchType / namespaceLabel 用于 UI 渲染。
 */
export interface AdministeredItemSearchHit {
  /** AdministeredItem.id（PK，作为 picker value） */
  id: string;
  /** Item.id（用于 fetchById 反查与关联关系展示） */
  itemId: string;
  /** 实体类型枚举（后端通过 EXISTS 子查询推导）；常见值：DataElement / DataElementConcept / ConceptualDomain / ValueDomain / ObjectClass / Property / DataType / UnitOfMeasure / Namespace / ... */
  itemType: string | null;
  /** 偏好名称（designation 中 zh-CN 或第一个 designation） */
  itemLabel: string | null;
  /** 业务标识符（如 VD.00001 / MD.VD.00001）— picker 主显示 */
  identifier: string | null;
  /** 版本号（如 1.0） */
  version: string | null;
  /** 命名空间 UUID */
  namespaceId: string | null;
  /** 命名空间偏好名称（picker 次要显示） */
  namespaceLabel: string | null;
  /** 创建时间（AdministeredItem.creationDateTime） */
  creationDateTime?: string | null;
  /** 最后编辑时间（AdministeredItem.lastChangeDateTime） */
  lastChangeDateTime?: string | null;
  /** 匹配度：EXACT（精确命中）/ PARTIAL（模糊回退）/ undefined（既有调用方） */
  matchType?: string;
}

/**
 * 四实体标准关联条目（GET /api/v1/items/{itemId}/associations 返回元素）。
 */
export interface ItemAssociationEntry {
  /** 标准关联类型：DATA_ELEMENT_MEANING / DATA_ELEMENT_DOMAIN / VALUE_DOMAIN_MEANING / DATA_ELEMENT_CONCEPT_DOMAIN */
  relationType: string;
  /** 相对焦点实体的方向：OUTGOING / INCOMING */
  direction: 'OUTGOING' | 'INCOMING';
  /** 对方实体 Item.id */
  itemId: string;
  /** 对方实体类型 */
  itemType: string | null;
  /** 对方偏好指称 */
  label: string | null;
  /** 对方业务标识符 */
  identifier: string | null;
  /** 对方版本号 */
  version: string | null;
  creationDateTime: string | null;
  lastChangeDateTime: string | null;
}

export interface ItemAssociationsResponse {
  itemId: string;
  itemType: string;
  associations: ItemAssociationEntry[];
}

/**
 * SmartItemPicker 的可选过滤配置。
 */
export interface SmartItemPickerTypesFilter {
  /** 实体类型枚举列表；空表示全部 */
  types: string[];
}

/** 映射度（14 标准值） */
export type MappingDegree =
  | 'related to'
  | 'same as'
  | 'semantically equivalent'
  | 'semantically similar'
  | 'derived from'
  | 'has derivation'
  | 'broader than'
  | 'narrower than'
  | 'child of'
  | 'parent of'
  | 'contains'
  | 'contained in'
  | 'instance of'
  | 'has instance';

/** 项目映射端点绑定（subject/object） */
export interface ItemMappingEndpoint {
  itemId: string;
  variableName: string | null;
}

/** 项目映射响应（subject/object 多对多） */
export interface ItemMappingResponse {
  id: string;
  degree: MappingDegree;
  description: string | null;
  transformationRule: string | null;
  transformationRuleNotation: string | null;
  subjects: ItemMappingEndpoint[];
  objects: ItemMappingEndpoint[];
  registrationStatus: string | null;
}

/** 项目映射创建/更新请求（subjects/objects 各至少 1 项；registration 仅创建时可携带） */
export interface ItemMappingRequest {
  subjects: ItemMappingEndpoint[];
  objects: ItemMappingEndpoint[];
  degree: MappingDegree;
  description?: string | null;
  transformationRule?: string | null;
  transformationRuleNotation?: string | null;
  registration?: unknown | null;
}

/** 项目映射层级响应 */
export interface ItemMappingHierarchyResponse {
  supersetMappingId: string | null;
  subsetMappingIds: string[];
}
