import request from '@/utils/request';
import type { InferenceJobStatus } from '@/api/inference';

export type AssistantOutputKind = 'CANDIDATES' | 'TEXT' | 'FINDINGS';
export type SelectionRequirement = 'NONE' | 'OPTIONAL' | 'REQUIRED';

/** 协作任务输入规格（GET /assistant/tasks 清单项） */
export interface TaskInputSpec {
  itemIdRequired: boolean;
  selection: SelectionRequirement;
  params: string[];
}

export interface AssistantTaskView {
  id: string;
  displayName: string;
  inputSpec: TaskInputSpec;
  outputKind: AssistantOutputKind;
}

/** 划选输入：选段原文 + 溯源信息（与后端 Selection record 对齐） */
export interface SelectionInput {
  field?: string;
  text: string;
  surroundingText?: string;
  language?: string;
  startOffset?: number;
  endOffset?: number;
}

/**
 * 协作作业状态响应。与 inference 作业不同，作业标识字段为 `id`；
 * result 按任务输出种类多态定型：TEXT → 单段字符串。
 */
export interface AssistantJobResponse {
  id: string;
  taskId: string;
  itemId?: string | null;
  selection?: SelectionInput | null;
  status: InferenceJobStatus;
  result?: unknown;
  /** true = 未关联注册项目，结果为通用解释，UI 须标注 */
  degraded: boolean;
  error?: string;
  providerId?: string;
  providerDisplayName?: string;
  createdAt?: string;
  finishedAt?: string;
}

export interface CreateAssistantJobPayload {
  taskId: string;
  itemId?: string;
  selection?: SelectionInput;
  /** 任务参数：值可为字符串、数组或对象（如 selection-chat 的 history 问答轮） */
  params?: Record<string, unknown>;
  provider?: string;
}

export interface PollAssistantJobOptions {
  intervalMs?: number;
  timeoutMs?: number;
  onTick?: (job: AssistantJobResponse) => void;
}

/**
 * AI 交互调试记录（GET /assistant/debug/exchanges 列表项）。
 * 后端仅存内存、不落库；按创建时间倒序、上限 50 条，限 REGISTRAR/SUPER_ADMIN。
 */
export interface DebugExchangeResponse {
  jobId: string;
  taskId: string;
  itemId?: string | null;
  selection?: SelectionInput | null;
  providerId?: string;
  providerDisplayName?: string;
  status: InferenceJobStatus;
  prompt?: string;
  rawOutput?: string;
  error?: string;
  createdAt?: string;
  finishedAt?: string;
}

export const assistantApi = {
  /** 任务清单：前端据此动态渲染任务入口 */
  tasks: () => request.get<AssistantTaskView[]>('/assistant/tasks'),
  /** 最近 AI 交互调试记录（内存态，仅注册人员可见） */
  listDebugExchanges: () =>
    request.get<DebugExchangeResponse[]>('/assistant/debug/exchanges'),
  /** 后端立即返回 jobId，放宽到 60s 以容忍慢启动/排队 */
  createJob: (payload: CreateAssistantJobPayload) =>
    request.post<{ jobId: string }>('/assistant/jobs', payload, { timeout: 60000 }),
  getJob: (jobId: string) =>
    request.get<AssistantJobResponse>(`/assistant/jobs/${jobId}`),
  cancelJob: (jobId: string) =>
    request.delete<void>(`/assistant/jobs/${jobId}`),
};

const TERMINAL_STATUSES: ReadonlySet<InferenceJobStatus> = new Set([
  'SUCCEEDED',
  'FAILED',
  'CANCELLED',
]);

/**
 * 轮询协作作业直至终态。语义与 inference.pollJob 一致：
 * 终态（SUCCEEDED / FAILED / CANCELLED）一律 resolve 终态对象，由调用方按 status 分支；
 * 仅整体超时（默认 5 分钟）reject；单次轮询失败不终止，等待下一拍重试。
 */
export function pollAssistantJob(
  jobId: string,
  options: PollAssistantJobOptions = {},
): Promise<AssistantJobResponse> {
  const { intervalMs = 2000, timeoutMs = 300000, onTick } = options;
  const start = Date.now();
  return new Promise<AssistantJobResponse>((resolve, reject) => {
    const tick = async () => {
      try {
        const job = await assistantApi.getJob(jobId);
        onTick?.(job);
        if (TERMINAL_STATUSES.has(job.status)) {
          resolve(job);
          return;
        }
      } catch {
        // 单次失败（网络抖动/作业被清理）不终止轮询，交由超时兜底
      }
      if (Date.now() - start >= timeoutMs) {
        reject(new Error(`assistant job ${jobId} polling timed out after ${timeoutMs}ms`));
        return;
      }
      setTimeout(() => void tick(), intervalMs);
    };
    void tick();
  });
}

/** 浮动对话窗的单轮问答历史（随请求重注入；服务端仅取最近 10 轮） */
export interface ChatHistoryEntry {
  question: string;
  answer: string;
}

/**
 * AI 辅助设置（GET/PUT /assistant/settings）。
 * GET 限 REGISTRAR（读取候选数与快捷键以驱动前端行为），PUT 限 SUPER_ADMIN。
 * hotkeys 为服务端默认合并后的完整命令 → 绑定映射。
 */
export interface AiAssistSettings {
  candidateCount: number;
  hotkeys: Record<string, string>;
}

export interface SaveAiAssistSettingsPayload {
  candidateCount: number;
  hotkeys: Record<string, string>;
}

export const assistantSettingsApi = {
  get: () => request.get<AiAssistSettings>('/assistant/settings'),
  save: (payload: SaveAiAssistSettingsPayload) =>
    request.put<AiAssistSettings>('/assistant/settings', payload),
};
