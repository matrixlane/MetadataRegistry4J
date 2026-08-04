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
