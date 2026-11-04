import request, { withSilentErrors } from '@/utils/request';

export interface DatasourceConfigRequest {
  vendor: string;
  host: string;
  port: number;
  database: string;
  filePath: string;
  username: string | null;
  password: string | null;
  edition: string;
}

export interface DatasourceRestartCapability {
  supported: boolean;
  reasonCode?: string | null;
}

export type DatasourceRestartState =
  | 'PENDING'
  | 'STARTING'
  | 'VERIFYING'
  | 'ROLLING_BACK'
  | 'SUCCEEDED'
  | 'ROLLED_BACK'
  | 'RECOVERY_FAILED';

export type DatasourceRestartVerification =
  | 'PENDING'
  | 'MATCHED'
  | 'MISMATCHED'
  | 'NOT_REQUIRED';

export interface DatasourceRestartStatus {
  restartId: string;
  state: DatasourceRestartState;
  verification: DatasourceRestartVerification;
  reasonCode?: string | null;
}

export interface DatasourceConfirmResult {
  restartRequired: boolean;
  restartId: string | null;
}

export const datasourceRestartApi = {
  getCapability: () =>
    withSilentErrors(() =>
      request.get<DatasourceRestartCapability>('/datasource/restart-capability')
    ),

  apply: (restartId: string, config: DatasourceConfigRequest) =>
    withSilentErrors(() =>
      request.post<{ restartId: string }>('/datasource/config/apply', { restartId, config })
    ),

  /**
   * "保持当前数据源并继续"：完整相直接返回 restartRequired=false（无需重启）；
   * 向导相创建重启事务并返回 restartId（随后的自动重启完成数据库初始化）。
   */
  confirmCurrent: (edition: string) =>
    withSilentErrors(() =>
      request.post<DatasourceConfirmResult>('/datasource/config/confirm-current', null, {
        params: { edition },
      })
    ),

  getRestartStatus: (restartId: string, signal?: AbortSignal) =>
    withSilentErrors(() =>
      request.get<DatasourceRestartStatus>(`/datasource/restarts/${restartId}`, { signal })
    ),
};
