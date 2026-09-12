import request from '@/utils/request';

export type InferenceField = 'definition' | 'designation';
export type InferenceJobStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

/** 推断提供方（GET /inference/providers 列表元素）：仅含已启用且探测可用的提供方 */
export interface InferenceProviderOption {
  id: string;
  displayName: string;
  isDefault: boolean;
}

/** 单条推断候选（后端 InferenceCandidate） */
export interface InferenceCandidate {
  field: InferenceField;
  language?: string;
  context?: string;
  text: string;
  rationale?: string;
}

/** 推断作业状态（GET /inference/jobs/{jobId} 返回体） */
export interface InferenceJob {
  jobId: string;
  status: InferenceJobStatus;
  candidates?: InferenceCandidate[];
  error?: string;
  providerId?: string;
  providerDisplayName?: string;
}

/** Agent 合并视图元素（GET/PUT /system/inference/agents） */
export interface AgentSettingView {
  id: string;
  displayName: string;
  kind: 'acp';
  command?: string;
  args?: string[];
  enabled: boolean;
  available: boolean;
  detail?: string;
  isDefault: boolean;
  builtin: boolean;
}

export interface AgentSaveItem {
  id: string;
  displayName?: string;
  enabled: boolean;
  command?: string;
  args?: string[];
}

export interface AgentsSettingsPayload {
  defaultProvider: string | null;
  agents: AgentSaveItem[];
}

export interface AgentsSettingsView {
  defaultProvider: string | null;
  agents: AgentSettingView[];
}

export interface CreateInferenceJobPayload {
  fields: InferenceField[];
  language?: string;
  contextId?: string;
  candidateCount?: number;
  provider?: string;
}

export interface PollJobOptions {
  intervalMs?: number;
  timeoutMs?: number;
  onTick?: (job: InferenceJob) => void;
}

export const inferenceApi = {
  /** 推断对话框用：仅已启用且探测可用的提供方（含默认标记） */
  providers: () =>
    request.get<InferenceProviderOption[]>('/inference/providers'),
  /** 后端立即返回 jobId，但放宽到 60s 以容忍慢启动/排队 */
  createJob: (itemId: string, payload: CreateInferenceJobPayload) =>
    request.post<{ jobId: string }>(`/inference/items/${itemId}/jobs`, payload, { timeout: 60000 }),
  getJob: (jobId: string) =>
    request.get<InferenceJob>(`/inference/jobs/${jobId}`),
  cancelJob: (jobId: string) =>
    request.delete<void>(`/inference/jobs/${jobId}`),
};

/** Agent 设置（SUPER_ADMIN）：合并设置与探测结果的视图 */
export const inferenceAgentApi = {
  getAgents: () =>
    request.get<AgentsSettingsView>('/system/inference/agents'),
  saveAgents: (payload: AgentsSettingsPayload) =>
    request.put<AgentsSettingsView>('/system/inference/agents', payload),
  probeAgents: () =>
    request.post<AgentsSettingsView>('/system/inference/agents/probe'),
};

const TERMINAL_STATUSES: ReadonlySet<InferenceJobStatus> = new Set([
  'SUCCEEDED',
  'FAILED',
  'CANCELLED',
]);

/**
 * 轮询作业直至进入终态。
 *
 * 终态（SUCCEEDED / FAILED / CANCELLED）一律 resolve 对应的作业对象，
 * 由调用方按 status 自行分支；仅整体超时（默认 5 分钟）才 reject。
 * 单次轮询的网络失败不终止轮询，等待下一拍重试。
 * 调用方主动取消应自行调用 cancelJob，并让本 Promise 随 CANCELLED 终态落定。
 */
export function pollJob(jobId: string, options: PollJobOptions = {}): Promise<InferenceJob> {
  const { intervalMs = 2000, timeoutMs = 300000, onTick } = options;
  const start = Date.now();
  return new Promise<InferenceJob>((resolve, reject) => {
    const tick = async () => {
      try {
        const job = await inferenceApi.getJob(jobId);
        onTick?.(job);
        if (TERMINAL_STATUSES.has(job.status)) {
          resolve(job);
          return;
        }
      } catch {
        // 单次失败（网络抖动/作业被清理）不终止轮询，交由超时兜底
      }
      if (Date.now() - start >= timeoutMs) {
        reject(new Error(`inference job ${jobId} polling timed out after ${timeoutMs}ms`));
        return;
      }
      setTimeout(() => void tick(), intervalMs);
    };
    void tick();
  });
}
