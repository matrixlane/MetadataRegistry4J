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
  ContextUsageResponse,
  ActiveRegistrarResponse,
  ItemAssociationsResponse,
  RegistrationProcessDefinition,
  RegistrationProcessLayout,
  ItemTransitionView,
  ItemMappingRequest,
  ItemMappingResponse,
  ItemMappingHierarchyResponse,
  MyContactResponse,
  RoleResponse,
  OrganizationResponse,
  WorkspaceSummaryResponse,
  WorkspaceItemResponse,
  WorkspaceItemView,
  WorkspaceSubmitRequest,
  WorkspaceSubmitResult,
  SubmissionInfo,
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

export const readinessApi = {
  raNamespaceExists: () =>
    request.get<{ exists: boolean; guidance: string | null }>('/readiness/ra-namespace-exists'),
};

export const activeRegistrarApi = {
  current: () => request.get<ActiveRegistrarResponse>('/active-registrar'),
  select: (registrarId: string) =>
    request.put<ActiveRegistrarResponse>('/active-registrar', { registrarId }),
};

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
export const administeredItemApi = {
  ...crud('/administered-items'),
  requestQualification: (id: string) =>
    request.post(`/administered-items/${id}/request-qualification`),
  reject: (id: string, note?: string) =>
    request.post(`/administered-items/${id}/transitions/reject`, { note }),
};

/** 注册流程（registration-process-designer）：流程定义 + 条目级迁移动作 */
export const registrationProcessApi = {
  /** 当前注册机构的完整流程定义（登录即可读） */
  get: () => request.get<RegistrationProcessDefinition>('/registration-process'),
  /** 某注册项当前可用迁移（含审批进度与当前用户权限） */
  availableTransitions: (itemId: string) =>
    request.get<ItemTransitionView[]>(`/administered-items/${itemId}/transitions`),
  /** 直接执行无需审核的迁移 */
  execute: (itemId: string, to: string, note?: string) =>
    request.post(`/administered-items/${itemId}/transitions/execute`, { to, note }),
  /** 批准需审核的迁移；达到通过人数时后端同事务生效 */
  approve: (itemId: string, to: string) =>
    request.post(`/administered-items/${itemId}/transitions/approve`, { to }),
};

/**
 * 注册流程画布布局：GET 登录可读（渲染恢复，文件由后端启动播种预置、可手工编辑）；
 * PUT 仅 SUPER_ADMIN（SecurityConfig 显式限定）。
 */
export const registrationProcessLayoutApi = {
  get: () => request.get<RegistrationProcessLayout>('/registration-process/layout'),
  save: (layout: RegistrationProcessLayout) =>
    request.put<void>('/registration-process/layout', layout),
};
export const organizationApi = {
  ...crud<OrganizationResponse>('/organizations'),
  // add-registrar-three-elements: 供「注册人员」编辑表单"组织"下拉消费
  options: () =>
    request.get<{ id: string; name: string | null }[]>('/organizations/options'),
};
export const individualApi = crud('/individuals');
export const roleApi = {
  ...crud<RoleResponse>('/roles'),
  options: () => request.get<{ id: string; roleName: string }[]>('/roles/options'),
};
export const contactApi = {
  ...crud<any>('/contacts'),
  batchConfigure: (data: { appUserIds: string[]; organizationId: string; roleName: string }) =>
    request.post<void>('/contacts/batch', data),
};

/** 当前登录用户的联系人身份（user-profile-individual / contact-identity-management / add-registrar-three-elements） */
export const myContactApi = {
  list: () => request.get<MyContactResponse[]>('/my/contacts'),
  create: (data: { organizationId: string; roleId?: string; individualId?: string }) =>
    request.post<MyContactResponse>('/my/contacts', data),
  update: (
    id: string,
    data: { organizationId: string; roleId?: string | null; individualId?: string | null },
  ) => request.put<MyContactResponse>(`/my/contacts/${id}`, data),
  delete: (id: string) => request.delete(`/my/contacts/${id}`),
  setDefault: (id: string) => request.post<MyContactResponse>(`/my/contacts/${id}/set-default`),
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
export const dimensionalityApi = crud('/dimensionalities');
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
  usages: (id: string) => request.get<ContextUsageResponse>(`/contexts/${id}/usages`),
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
  search: (params?: PageQuery & {
    type?: string;
    status?: string;
    contextId?: string;
    acceptability?: string[];
    namespaceId?: string;
    exactMatch?: boolean;
    identifier?: string;
    namespaceShorthand?: string;
  }) =>
    request.get<PageResponse<any>>('/metadata/search', { params }),
};

/**
 * 四实体标准关联（ISO/IEC 11179-31:2023 §7.2.3 / §7.4.4.1 / §7.6.3）。
 * relationType 路径值：data-element-meaning / data-element-domain /
 * value-domain-meaning / data-element-concept-domain；
 * source/target 角色由关联类型固定（见后端 StandardAssociationType）。
 */
export const itemAssociationApi = {
  list: (itemId: string) =>
    request.get<ItemAssociationsResponse>(`/items/${itemId}/associations`),
  associate: (relationType: string, sourceItemId: string, targetItemId: string) =>
    request.post<ItemAssociationsResponse>(`/associations/${relationType}`, { sourceItemId, targetItemId }),
  dissociate: (relationType: string, sourceItemId: string, targetItemId: string) =>
    request.delete<ItemAssociationsResponse>(`/associations/${relationType}`, {
      data: { sourceItemId, targetItemId },
    }),
};

/**
 * 项目映射 CRUD + 映射层级。
 * degree 14 标准值见后端 MappingDegree；subject/object 均为已存在条目（Item.id）。
 */
export const itemMappingApi = {
  list: (params?: PageQuery & { subjectItemId?: string; objectItemId?: string; degree?: string }) =>
    request.get<PageResponse<ItemMappingResponse>>('/item-mappings', { params }),
  get: (id: string) => request.get<ItemMappingResponse>(`/item-mappings/${id}`),
  create: (data: ItemMappingRequest) =>
    request.post<ItemMappingResponse>('/item-mappings', data),
  update: (id: string, data: ItemMappingRequest) =>
    request.put<ItemMappingResponse>(`/item-mappings/${id}`, data),
  delete: (id: string) => request.delete(`/item-mappings/${id}`),
  getHierarchy: (id: string) =>
    request.get<ItemMappingHierarchyResponse>(`/item-mappings/${id}/hierarchy`),
  putHierarchy: (id: string, supersetMappingId: string | null) =>
    request.put<ItemMappingHierarchyResponse>(`/item-mappings/${id}/hierarchy`, { supersetMappingId }),
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
  delete: (itemId: string, id: string, opts?: { deleteLines?: boolean }) =>
    request.delete(`/items/${itemId}/designation-definition-pairings/${id}`, {
      params: opts?.deleteLines ? { deleteLines: true } : {},
    }),
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

/** 工作空间：三视图管理项列表与行级/批量操作 */
export const workspaceApi = {
  summary: () => request.get<WorkspaceSummaryResponse>('/workspace/summary'),
  items: (view: WorkspaceItemView, page?: number, size?: number) =>
    request.get<PageResponse<WorkspaceItemResponse>>('/workspace/items', {
      params: { view, page, size },
    }),
  submit: (itemIds: string[], submission?: SubmissionInfo | null) =>
    request.post<WorkspaceSubmitResult[]>('/workspace/submit', { itemIds, submission } as WorkspaceSubmitRequest),
  requestCandidate: (itemId: string, submission?: SubmissionInfo | null) =>
    request.post<void>(`/workspace/request-candidate/${itemId}`, submission || {}),
  rollback: (itemId: string) => request.post<void>(`/workspace/rollback/${itemId}`),
};
