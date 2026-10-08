import request from '@/utils/request';

export interface RegistrySpecification {
  id?: string;
  name?: string;
  /** 默认语言 ID（与 RegistrySpecificationResponse 对齐）。 */
  defaultLanguageId?: string | null;
  /** 默认语言 identifier 字符串（如 zh-CN）。 */
  defaultLanguageIdentifier?: string | null;
}

export const systemApi = {
  /**
   * 恢复出厂设置 — 清除所有数据并将系统重置为初始状态
   * POST /api/v1/system/factory-reset
   *
   * 二次确认按部署版本差异化：团队版传当前管理员密码；个人版无密码概念，
   * 传确认短语（与固定短语"恢复出厂设置"精确匹配）。
   */
  factoryReset: (payload: { password?: string; confirmationPhrase?: string }) =>
    request.post<void>('/system/factory-reset', payload),

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