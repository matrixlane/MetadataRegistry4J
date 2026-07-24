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
}

export interface PairingForm {
  id?: string;
  designationId: string;
  definitionId: string;
  contextId?: string;
  acceptability: 'PREFERRED' | 'ADMITTED' | 'DEPRECATED' | 'SUPERSEDED' | 'OBSOLETE';
}

export interface AppUserDto {
  id: string;
  username: string;
  displayName: string;
  email: string;
  role: 'ADMIN' | 'REGISTRAR' | 'VIEWER';
  enabled: boolean;
}
