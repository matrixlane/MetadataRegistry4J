import request from '@/utils/request';
import type { PageQuery, PageResponse } from '@/types/common';

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
export const namespaceApi = crud('/namespaces');
export const administeredItemApi = crud('/administered-items');
export const organizationApi = crud('/organizations');
export const individualApi = crud('/individuals');
export const contactApi = crud('/contacts');
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
export const dataElementApi = crud('/data-elements');
export const appUserApi = crud('/app-users');
export const languageApi = crud('/languages');
export const documentTypeApi = crud('/document-types');

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
};

/** Unified metadata search (WI-015a) */
export const metadataSearchApi = {
  search: (params?: PageQuery & { type?: string; status?: string }) =>
    request.get<PageResponse<any>>('/metadata/search', { params }),
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
