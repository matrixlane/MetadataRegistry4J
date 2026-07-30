import request from '@/utils/request';
import type {
  PageQuery,
  PageResponse,
  NamespaceRequest,
  NamespaceResponse,
  NamespaceDetailResponse,
  NamingConventionRequest,
  NamingConventionResponse,
  ItemRelationshipResponse,
} from '@/types/common';

function crud<T>(base: string) {
  return {
    list: (params?: PageQuery) =>
      request.get<PageResponse<T>>(base, { params }),
    get: (id: string) => request.get<T>(`${base}/${id}`),
    create: (data: Record<string, unknown>) => request.post<T>(base, data),
    update: (id: string, data: Record<string, unknown>) =>
      request.put<T>(`${base}/${id}`, data),
    delete: (id: string) => request.delete(`${base}/${id}`),
  };
}

export const registrationAuthorityApi = crud('/registration-authorities');
export const registrarApi = crud('/registrars');
export const namespaceApi = {
  list: (params?: PageQuery) =>
    request.get<PageResponse<NamespaceResponse>>('/namespaces', { params }),
  get: (id: string) =>
    request.get<NamespaceResponse>(`/namespaces/${id}`),
  detail: (id: string) =>
    request.get<NamespaceDetailResponse>(`/namespaces/${id}/detail`),
  create: (data: NamespaceRequest) =>
    request.post<NamespaceResponse>('/namespaces', data),
  update: (id: string, data: NamespaceRequest) =>
    request.put<NamespaceResponse>(`/namespaces/${id}`, data),
  delete: (id: string) => request.delete(`/namespaces/${id}`),
  deletionCheck: (id: string) =>
    request.get<{ hasIdentifiers: boolean; count: number; message: string }>(`/namespaces/${id}/deletion-check`),
  createExternal: (data: Record<string, unknown>) =>
    request.post<NamespaceResponse>('/namespaces/external', data),
  getPermissions: (id: string) =>
    request.get<unknown[]>(`/namespaces/${id}/permissions`),
  savePermission: (id: string, data: Record<string, unknown>) =>
    request.post<unknown>(`/namespaces/${id}/permissions`, data),
  deletePermission: (permissionId: string) =>
    request.delete(`/namespaces/permissions/${permissionId}`),
};
export const namingConventionApi = {
  list: (params?: PageQuery) =>
    request.get<PageResponse<NamingConventionResponse>>('/naming-conventions', { params }),
  get: (id: string) =>
    request.get<NamingConventionResponse>(`/naming-conventions/${id}`),
  create: (data: NamingConventionRequest) =>
    request.post<NamingConventionResponse>('/naming-conventions', data),
  update: (id: string, data: NamingConventionRequest) =>
    request.put<NamingConventionResponse>(`/naming-conventions/${id}`, data),
  delete: (id: string) => request.delete(`/naming-conventions/${id}`),
};
export const administeredItemApi = crud('/administered-items');
export const organizationApi = crud('/organizations');
export const individualApi = crud('/individuals');
export const contactApi = {
  ...crud<any>('/contacts'),
  batchConfigure: (data: { appUserIds: string[]; organizationId: string; roleName: string }) =>
    request.post<void>('/contacts/batch', data),
};
export const referenceDocumentApi = crud('/reference-documents');
export const objectClassApi = crud('/object-classes');
export const propertyApi = crud('/properties');
export const dataElementConceptApi = crud('/data-element-concepts');
export const conceptualDomainApi = crud('/conceptual-domains');
export const valueDomainApi = crud('/value-domains');
export const dataTypeApi = crud('/data-types');
export const dataTypeSchemeApi = crud('/data-type-schemes');
export const unitOfMeasureApi = crud('/units-of-measure');
export const measureClassApi = crud('/measure-classes');
export const valueMeaningApi = crud('/value-meanings');
export const dataElementApi = crud('/data-elements');
export const appUserApi = {
  ...crud<any>('/accounts'),
  audit: (id: string, enabled: boolean) =>
    request.put<any>(`/accounts/${id}/audit`, null, { params: { enabled } }),
  resetPassword: (id: string) =>
    request.post<string>(`/accounts/${id}/reset-password`),
};
export const languageApi = crud('/languages');
export const documentTypeApi = crud('/document-types');

export const registrySpecificationApi = crud('/registry-specifications');

export const contextApi = {
  list: (params?: PageQuery) => request.get<PageResponse<any>>('/contexts', { params }),
  get: (id: string) => request.get<any>(`/contexts/${id}`),
  create: (data: Record<string, unknown>) => request.post<any>('/contexts', data),
  update: (id: string, data: Record<string, unknown>) => request.put<any>(`/contexts/${id}`, data),
  delete: (id: string) => request.delete(`/contexts/${id}`),
  options: (params?: { q?: string }) => request.get<{ id: string; label: string }[]>('/contexts/options', { params }),
};

export const classificationSchemeApi = {
  list: (params?: PageQuery) =>
    request.get<PageResponse<any>>('/classification-schemes', { params }),
  get: (id: string) => request.get<any>(`/classification-schemes/${id}`),
  create: (data: Record<string, unknown>) =>
    request.post<any>('/classification-schemes', data),
  update: (id: string, data: Record<string, unknown>) =>
    request.put<any>(`/classification-schemes/${id}`, data),
  delete: (id: string) => request.delete(`/classification-schemes/${id}`),
  getTree: (schemeId: string) =>
    request.get<any[]>(`/classification-schemes/${schemeId}/nodes/tree`),
  getChildren: (schemeId: string, parentId?: string) =>
    request.get<any[]>(`/classification-schemes/${schemeId}/nodes/children`, {
      params: parentId ? { parentId } : {},
    }),
  getNode: (schemeId: string, nodeId: string) =>
    request.get<any>(`/classification-schemes/${schemeId}/nodes/${nodeId}`),
  createNode: (schemeId: string, data: Record<string, unknown>) =>
    request.post<any>(`/classification-schemes/${schemeId}/nodes`, data),
  updateNode: (schemeId: string, nodeId: string, data: Record<string, unknown>) =>
    request.put<any>(`/classification-schemes/${schemeId}/nodes/${nodeId}`, data),
  deleteNode: (schemeId: string, nodeId: string) =>
    request.delete(`/classification-schemes/${schemeId}/nodes/${nodeId}`),
  searchNodes: (schemeId: string, keyword: string) =>
    request.get<any[]>(`/classification-schemes/${schemeId}/nodes/search`, {
      params: { keyword },
    }),
  listNodes: (schemeId: string) =>
    request.get<any[]>(`/classification-schemes/${schemeId}/nodes`),
  moveNode: (schemeId: string, nodeId: string, parentId: string | null) =>
    request.put<any>(`/classification-schemes/${schemeId}/nodes/${nodeId}/move`, { parentId }),
  deleteNodeCascade: (schemeId: string, nodeId: string) =>
    request.delete(`/classification-schemes/${schemeId}/nodes/${nodeId}/cascade`),
  validateImport: (file: File, targetSchemeId?: string) => {
    const formData = new FormData();
    formData.append('file', file);
    if (targetSchemeId) formData.append('schemeId', targetSchemeId);
    return request.post<any>('/classification-schemes/import/validate', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  importFile: (file: File, targetSchemeId?: string) => {
    const formData = new FormData();
    formData.append('file', file);
    if (targetSchemeId) formData.append('schemeId', targetSchemeId);
    return request.post<any>('/classification-schemes/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};

/** Unified metadata search (WI-015a) */
export const metadataSearchApi = {
  search: (params?: PageQuery & { type?: string; status?: string }) =>
    request.get<PageResponse<any>>('/metadata/search', { params }),
};

/** Mapping scheme CRUD + version lifecycle (WI-011-2-3-1) */
export const mappingSchemeApi = {
  list: (params?: PageQuery) =>
    request.get<PageResponse<any>>('/mapping-schemes', { params }),
  get: (id: string) => request.get<any>(`/mapping-schemes/${id}`),
  create: (data: Record<string, unknown>) =>
    request.post<any>('/mapping-schemes', data),
  update: (id: string, data: Record<string, unknown>) =>
    request.put<any>(`/mapping-schemes/${id}`, data),
  delete: (id: string) => request.delete(`/mapping-schemes/${id}`),
  /** Transition status: DRAFT->PUBLISHED, PUBLISHED->ARCHIVED */
  transition: (id: string, to: string) =>
    request.post<any>(`/mapping-schemes/${id}/transition`, { to }),
  /** Create new DRAFT version from PUBLISHED scheme */
  createNewVersion: (id: string) =>
    request.post<any>(`/mapping-schemes/${id}/new-version`),
  /** Get all versions in the scheme lineage */
  versions: (id: string) =>
    request.get<any[]>(`/mapping-schemes/${id}/versions`),
  /** Diff two scheme versions (WI-011-2-3-2) */
  diff: (id: string, baseId: string, compareId: string, mappingType?: string) =>
    request.get<any>(`/mapping-schemes/${id}/diff`, { params: { baseId, compareId, ...(mappingType ? { mappingType } : {}) } }),
};

/** Mapping network graph (WI-011-2-1) */
export const getMappingNetwork = (
  centerId: string,
  depth?: number,
  filterTypes?: string[]
) =>
  request.get('/mappings/network', {
    params: { 
      centerId, 
      depth, 
      ...(filterTypes && filterTypes.length > 0 
        ? { filterTypes: filterTypes.join(',') } 
        : {})
    },
  });

export const pairingApi = {
  list: (itemId: string) =>
    request.get<any[]>(`/items/${itemId}/designation-definition-pairings`),
  create: (itemId: string, data: Record<string, unknown>) =>
    request.post<any>(`/items/${itemId}/designation-definition-pairings`, data),
  update: (itemId: string, id: string, data: Record<string, unknown>) =>
    request.put<any>(`/items/${itemId}/designation-definition-pairings/${id}`, data),
  delete: (itemId: string, id: string) =>
    request.delete(`/items/${itemId}/designation-definition-pairings/${id}`),
};

export const itemRelationshipApi = {
  get: (itemId: string) =>
    request.get<ItemRelationshipResponse>(`/items/${itemId}/designation-definition-relationships`),
};

/** Version history (WI-015b) */
export const versionHistoryApi = {
  list: (itemId: string) =>
    request.get<any[]>(`/items/${itemId}/versions`),
  diff: (itemId: string, baseVersion: number, compareVersion: number) =>
    request.get<any>(`/items/${itemId}/versions/${baseVersion}/diff`, {
      params: { compareVersionId: compareVersion },
    }),
  snapshot: (itemId: string, versionNumber: number) =>
    request.get<any>(`/items/${itemId}/versions/${versionNumber}/snapshot`),
};

/** Interoperability mapping result management (WI-013-2b) */
export const interopMappingApi = {
  list: (params?: PageQuery & { datasetId?: string; status?: string; minConfidence?: number; maxConfidence?: number }) =>
    request.get<PageResponse<any>>('/interop-mappings', { params }),
  get: (id: string) => request.get<any>(`/interop-mappings/${id}`),
  confirm: (id: string, userId: string) =>
    request.put<any>(`/interop-mappings/${id}/confirm`, null, { params: { userId } }),
  reject: (id: string, userId: string) =>
    request.put<any>(`/interop-mappings/${id}/reject`, null, { params: { userId } }),
  manualMap: (id: string, localItemId: string, userId: string) =>
    request.put<any>(`/interop-mappings/${id}/manual-map`, { localItemId }, { params: { userId } }),
  undo: (id: string) => request.delete<any>(`/interop-mappings/${id}/undo`),
  markUnmatched: (id: string) =>
    request.put<any>(`/interop-mappings/${id}/mark-unmatched`),
  countPending: (datasetId: string) =>
    request.get<number>('/interop-mappings/batch/pending-count', { params: { datasetId } }),
  batchConfirm: (datasetId: string, userId: string) =>
    request.post<any>('/interop-mappings/batch-confirm', null, { params: { datasetId, userId } }),
  batchReject: (datasetId: string, userId: string) =>
    request.post<any>('/interop-mappings/batch-reject', null, { params: { datasetId, userId } }),
  undoBatch: (batchOperationId: string) =>
    request.post<any>('/interop-mappings/batch-undo', null, { params: { batchOperationId } }),
  qualityScore: (datasetId: string) =>
    request.get<any>('/interop-mappings/quality-score', { params: { datasetId } }),
  emptyCheck: (datasetId: string) =>
    request.get<any>('/interop-mappings/empty-check', { params: { datasetId } }),
};
