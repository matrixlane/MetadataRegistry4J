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
  | 'SUBMITTED'
  | 'RECORDED'
  | 'QUALIFIED'
  | 'STANDARD'
  | 'PREFERRED'
  | 'SUPERSEDED'
  | 'RETIRED'
  | 'HISTORICAL'
  | 'APPLICATION';

export type ElTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger';

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

export interface NamespaceDetailResponse extends NamespaceResponse {
}

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
  // Keep legacy properties as optional to prevent compilation issues
  displayName?: string;
  email?: string;
  role?: 'ADMIN' | 'REGISTRAR' | 'VIEWER';
  roleAssignments?: UserRoleAssignmentDto[];
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
