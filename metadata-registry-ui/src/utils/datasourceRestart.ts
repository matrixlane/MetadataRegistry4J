import { datasourceRestartApi } from '@/api/datasourceRestart';
import type { DatasourceRestartStatus } from '@/api/datasourceRestart';

export const DATASOURCE_RESTART_SESSION_KEY = 'metadata-registry.datasource-restart.v1';
export const DATASOURCE_RESTART_TIMEOUT_MS = 390_000;
export const DATASOURCE_RESTART_POLL_INTERVAL_MS = 1000;

export interface DatasourceRestartSession {
  restartId: string;
  edition: string;
  step: 1;
  startedAt: number;
}

interface RestartSessionOptions {
  storage?: Storage;
  now?: () => number;
  createId?: () => string;
}

export type DatasourceRestartPhase =
  | 'SAVING'
  | 'RESTARTING'
  | 'PREPARING'
  | 'VERIFYING'
  | 'RECOVERING'
  | 'RESTORING'
  | 'SUCCEEDED'
  | 'ROLLED_BACK'
  | 'FAILED';

export interface DatasourceRestartSnapshot {
  phase: DatasourceRestartPhase;
  elapsedMs: number;
  status: DatasourceRestartStatus | null;
  reasonCode: string | null;
}

export type StartupProbeState = 'READY' | 'PREPARING' | 'UNAVAILABLE';

export interface DatasourceRestartMonitor {
  start: () => void;
  stop: () => void;
  retry: (startedAt?: number) => void;
}

interface DatasourceRestartMonitorOptions {
  restartId: string;
  startedAt: number;
  onUpdate: (snapshot: DatasourceRestartSnapshot) => void;
  timeoutMs?: number;
  pollIntervalMs?: number;
  now?: () => number;
  getStatus?: (restartId: string, signal?: AbortSignal) => Promise<DatasourceRestartStatus>;
  probeStartup?: (signal?: AbortSignal) => Promise<StartupProbeState>;
}

export function createRestartId(): string {
  return globalThis.crypto.randomUUID();
}

export function readRestartSession(
  storage: Storage = window.sessionStorage
): DatasourceRestartSession | null {
  try {
    const raw = storage.getItem(DATASOURCE_RESTART_SESSION_KEY);
    if (!raw) return null;
    const value = JSON.parse(raw) as Partial<DatasourceRestartSession>;
    if (
      typeof value.restartId !== 'string' || !value.restartId ||
      typeof value.edition !== 'string' || !value.edition ||
      value.step !== 1 ||
      typeof value.startedAt !== 'number' || !Number.isFinite(value.startedAt) || value.startedAt < 0
    ) {
      return null;
    }
    return {
      restartId: value.restartId,
      edition: value.edition,
      step: 1,
      startedAt: value.startedAt,
    };
  } catch {
    return null;
  }
}

export function writeRestartSession(
  session: DatasourceRestartSession,
  storage: Storage = window.sessionStorage
): DatasourceRestartSession {
  const marker: DatasourceRestartSession = {
    restartId: session.restartId,
    edition: session.edition,
    step: 1,
    startedAt: session.startedAt,
  };
  storage.setItem(DATASOURCE_RESTART_SESSION_KEY, JSON.stringify(marker));
  return marker;
}

export function getOrCreateRestartSession(
  edition: string,
  options: RestartSessionOptions = {}
): DatasourceRestartSession {
  const storage = options.storage ?? window.sessionStorage;
  const existing = readRestartSession(storage);
  if (existing?.edition === edition) return existing;
  return writeRestartSession({
    restartId: (options.createId ?? createRestartId)(),
    edition,
    step: 1,
    startedAt: (options.now ?? Date.now)(),
  }, storage);
}

export function clearRestartSession(storage: Storage = window.sessionStorage): void {
  try {
    storage.removeItem(DATASOURCE_RESTART_SESSION_KEY);
  } catch {
    // Session storage can be unavailable in hardened browser contexts.
  }
}

export async function probeStartupStatus(signal?: AbortSignal): Promise<StartupProbeState> {
  try {
    const response = await fetch('/api/v1/startup/status', {
      cache: 'no-store',
      signal,
    });
    if (response.status !== 200) return 'UNAVAILABLE';
    const body = await response.json() as { data?: { ready?: boolean } };
    if (typeof body?.data?.ready !== 'boolean') return 'UNAVAILABLE';
    return body.data.ready ? 'READY' : 'PREPARING';
  } catch {
    return 'UNAVAILABLE';
  }
}

function isTemporaryStatusError(error: unknown): boolean {
  const status = (error as { response?: { status?: number } })?.response?.status;
  return status === undefined || status >= 500 || status === 408 || status === 429;
}

export function createDatasourceRestartMonitor(
  options: DatasourceRestartMonitorOptions
): DatasourceRestartMonitor {
  const now = options.now ?? Date.now;
  const timeoutMs = options.timeoutMs ?? DATASOURCE_RESTART_TIMEOUT_MS;
  const pollIntervalMs = options.pollIntervalMs ?? DATASOURCE_RESTART_POLL_INTERVAL_MS;
  const getStatus = options.getStatus ?? datasourceRestartApi.getRestartStatus;
  const probeStartup = options.probeStartup ?? probeStartupStatus;
  let startedAt = options.startedAt;
  let active = false;
  let generation = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let deadlineTimer: ReturnType<typeof setTimeout> | undefined;
  let abortController: AbortController | undefined;

  function isCurrent(run: number): boolean {
    return active && run === generation;
  }

  function publish(
    run: number,
    phase: DatasourceRestartPhase,
    status: DatasourceRestartStatus | null = null,
    reasonCode: string | null = null
  ): void {
    if (!isCurrent(run)) return;
    options.onUpdate({
      phase,
      elapsedMs: Math.max(0, now() - startedAt),
      status,
      reasonCode,
    });
  }

  function finish(
    run: number,
    phase: 'SUCCEEDED' | 'ROLLED_BACK' | 'FAILED',
    status: DatasourceRestartStatus | null,
    reasonCode: string | null
  ): void {
    if (!isCurrent(run)) return;
    publish(run, phase, status, reasonCode);
    active = false;
    abortController = undefined;
    if (timer !== undefined) clearTimeout(timer);
    if (deadlineTimer !== undefined) clearTimeout(deadlineTimer);
    timer = undefined;
    deadlineTimer = undefined;
  }

  function hasTimedOut(): boolean {
    return now() - startedAt >= timeoutMs;
  }

  async function poll(run: number): Promise<void> {
    if (!isCurrent(run)) return;
    if (hasTimedOut()) {
      finish(run, 'FAILED', null, 'WAIT_TIMEOUT');
      return;
    }

    const controller = new AbortController();
    abortController = controller;
    const [statusResult, startup] = await Promise.all([
      getStatus(options.restartId, controller.signal).then(
        (status) => ({ status, error: null as unknown }),
        (error) => ({ status: null, error })
      ),
      probeStartup(controller.signal).catch(() => 'UNAVAILABLE' as StartupProbeState),
    ]);

    if (!isCurrent(run)) return;
    abortController = undefined;
    if (hasTimedOut()) {
      finish(run, 'FAILED', statusResult.status, 'WAIT_TIMEOUT');
      return;
    }

    const status = statusResult.status;
    if (status?.state === 'SUCCEEDED' && status.verification === 'MATCHED') {
      finish(run, 'SUCCEEDED', status, status.reasonCode ?? null);
      return;
    }
    if (status?.state === 'ROLLED_BACK') {
      finish(run, 'ROLLED_BACK', status, status.reasonCode ?? null);
      return;
    }
    if (status?.state === 'RECOVERY_FAILED') {
      finish(run, 'FAILED', status, status.reasonCode ?? 'RECOVERY_FAILED');
      return;
    }
    if (statusResult.error && !isTemporaryStatusError(statusResult.error)) {
      const httpStatus = (statusResult.error as { response?: { status?: number } })?.response?.status;
      finish(run, 'FAILED', null, httpStatus === 404 ? 'RESTART_NOT_FOUND' : 'STATUS_QUERY_FAILED');
      return;
    }

    let phase: DatasourceRestartPhase = 'RESTARTING';
    if (status?.state === 'ROLLING_BACK' || status?.verification === 'MISMATCHED') {
      phase = 'RECOVERING';
    } else if (status?.state === 'VERIFYING' || status?.state === 'SUCCEEDED') {
      phase = 'VERIFYING';
    } else if (startup === 'PREPARING') {
      phase = 'PREPARING';
    } else if (!status && startup === 'READY') {
      phase = 'VERIFYING';
    }
    publish(run, phase, status, status?.reasonCode ?? null);
    if (!isCurrent(run)) return;
    timer = setTimeout(() => {
      void poll(run);
    }, pollIntervalMs);
  }

  function start(): void {
    if (active) return;
    active = true;
    generation++;
    const run = generation;
    publish(run, 'RESTARTING');
    const remainingMs = Math.max(0, timeoutMs - Math.max(0, now() - startedAt));
    if (remainingMs > 0) {
      deadlineTimer = setTimeout(() => {
        if (!isCurrent(run)) return;
        const pendingRequest = abortController;
        finish(run, 'FAILED', null, 'WAIT_TIMEOUT');
        pendingRequest?.abort();
      }, remainingMs);
    }
    void poll(run);
  }

  function stop(): void {
    active = false;
    generation++;
    if (timer !== undefined) clearTimeout(timer);
    if (deadlineTimer !== undefined) clearTimeout(deadlineTimer);
    timer = undefined;
    deadlineTimer = undefined;
    abortController?.abort();
    abortController = undefined;
  }

  function retry(retryStartedAt = now()): void {
    stop();
    startedAt = retryStartedAt;
    start();
  }

  return { start, stop, retry };
}
