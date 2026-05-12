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
