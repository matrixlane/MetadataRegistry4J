import request from '@/utils/request';

export interface RegistrySpecification {
  id?: string;
  registryName: string;
  identifierPrefix?: string;
  autoGenerateIdentifier: boolean;
  identifierPattern?: string;
  versioningPolicy?: string;
  mandatoryClassification: boolean;
  defaultLanguage: string;
  description?: string;
}

export const systemApi = {
  /**
   * 恢复出厂设置 — 清除所有数据并将系统重置为初始状态
   * POST /api/v1/system/factory-reset
   */
  factoryReset: (password: string) =>
    request.post<void>('/system/factory-reset', { password }),

  /**
   * 获取新用户注册是否需要审核的开关状态
   */
  getNewUserRequireAudit: () =>
    request.get<boolean>('/system/settings/new-user-require-audit'),

  /**
   * 设置新用户注册是否需要审核的开关状态
   */
  setNewUserRequireAudit: (requireAudit: boolean) =>
    request.put<void>('/system/settings/new-user-require-audit', null, { params: { requireAudit } }),

  /**
   * 获取是否允许用户自主注册的开关状态
   */
  getAllowUserRegistration: () =>
    request.get<boolean>('/system/settings/allow-user-registration'),

  /**
   * 设置是否允许用户自主注册的开关状态
   */
  setAllowUserRegistration: (allowRegistration: boolean) =>
    request.put<void>('/system/settings/allow-user-registration', null, { params: { allowRegistration } }),
};

export const registrySpecificationApi = {
  /**
   * 查询当前的注册规范
   */
  get: () => request.get<RegistrySpecification | null>('/registry-specifications'),

  /**
   * 查询系统中是否已登记元数据
   */
  hasMetadata: () => request.get<boolean>('/registry-specifications/has-metadata'),

  /**
   * 创建注册规范
   */
  create: (data: RegistrySpecification) => request.post<RegistrySpecification>('/registry-specifications', data),

  /**
   * 更新注册规范
   */
  update: (id: string, data: RegistrySpecification) => request.put<RegistrySpecification>(`/registry-specifications/${id}`, data),
};

export interface DashboardStats {
  dataElements: number;
  objectClasses: number;
  properties: number;
  valueDomains: number;
  conceptualDomains: number;
  dataTypes: number;
  organizations: number;
  contexts: number;
}

export const dashboardApi = {
  /**
   * 获取 Dashboard 统计数据
   */
  getStats: () => request.get<DashboardStats>('/dashboard'),
};

