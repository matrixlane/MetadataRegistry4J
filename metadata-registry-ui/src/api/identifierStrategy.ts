import request from '@/utils/request';

/** Segment-template identifier strategy admin API client. */

export interface StrategyConfig {
  id: number;
  strategyType: string;
  configJson: string;
  version: number;
  status: 'DRAFT' | 'PUBLISHED' | 'DISABLED';
  isGlobalDefault: boolean;
}

export interface NamespaceStrategyView {
  published: StrategyConfig | null;
  drafts: StrategyConfig[];
  history: StrategyConfig[];
}

export interface PreviewResult {
  sample: string | null;
  formatRegex: string | null;
  validationErrors: string[];
}

export interface ValidateFormatResult {
  matches: boolean;
  parsedValues: Record<string, string>;
  error: string | null;
}

export interface StrategyPreset {
  key: string;
  name: string;
  description: string;
  configJson: string;
}

export interface BackfillResult {
  parsed: number;
  skipped: number;
  scopeMaxValues: Record<string, number>;
}

const base = (namespaceId: string) => `/namespaces/${namespaceId}/identifier-strategy`;

export const identifierStrategyApi = {
  view: (namespaceId: string) =>
    request.get<NamespaceStrategyView>(base(namespaceId)),

  saveDraft: (namespaceId: string, configJson: string) =>
    request.post<StrategyConfig>(`${base(namespaceId)}/draft`, { configJson }),

  publish: (namespaceId: string, configJson: string) =>
    request.post<StrategyConfig>(`${base(namespaceId)}/publish`, { configJson }),

  publishDraft: (namespaceId: string, configId: number) =>
    request.post<StrategyConfig>(`${base(namespaceId)}/drafts/${configId}/publish`),

  disable: (namespaceId: string) =>
    request.post<void>(`${base(namespaceId)}/disable`),

  preview: (namespaceId: string, configJson: string,
            variables?: Record<string, string>, itemType?: string) =>
    request.post<PreviewResult>(`${base(namespaceId)}/preview`, { configJson, variables, itemType }),

  validateFormat: (namespaceId: string, configJson: string, identifier: string) =>
    request.post<ValidateFormatResult>(`${base(namespaceId)}/validate`, { configJson, identifier }),

  backfill: (namespaceId: string) =>
    request.post<BackfillResult>(`${base(namespaceId)}/backfill`),

  presets: () =>
    request.get<StrategyPreset[]>('/identifier-strategy/presets'),
};
