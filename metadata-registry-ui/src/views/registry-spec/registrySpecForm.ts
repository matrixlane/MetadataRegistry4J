/**
 * 注册规范编辑对话框的表单模型与纯函数。
 * 独立成模块以便 vitest 直接覆盖：回显映射（applyRowToForm）、
 * 提交载荷组装（buildRegistrySpecPayload）、时区格式校验（timeZoneValidator）。
 */
export interface RegistrySpecFormModel {
  id?: string;
  name: string;
  comment: string;
  webAddress: string;
  defaultLanguageId: string;
  alternativeLanguageIds: string[];
  contextId: string;
  representationClassSchemeId: string;
  referenceDocumentIdentifierKinds: string[];
  characterRepertoire: string;
  standard: string;
  degreeOfConformance?: string;
  conformanceLevel: string;
  conformanceLabel: string;
  includedFeatures: string;
  excludedFeatures: string;
  defaultTimeZone: string;
}

const ARRAY_FIELDS: (keyof RegistrySpecFormModel)[] = [
  'alternativeLanguageIds',
  'referenceDocumentIdentifierKinds',
];

export function createEmptyRegistrySpecForm(): RegistrySpecFormModel {
  return {
    id: undefined,
    name: '',
    comment: '',
    webAddress: '',
    defaultLanguageId: '',
    alternativeLanguageIds: [],
    contextId: '',
    representationClassSchemeId: '',
    referenceDocumentIdentifierKinds: [],
    characterRepertoire: '',
    standard: '',
    degreeOfConformance: undefined,
    conformanceLevel: '',
    conformanceLabel: '',
    includedFeatures: '',
    excludedFeatures: '',
    defaultTimeZone: '',
  };
}

export function resetRegistrySpecForm(form: RegistrySpecFormModel): void {
  const empty = createEmptyRegistrySpecForm();
  (Object.keys(empty) as (keyof RegistrySpecFormModel)[]).forEach((k) => {
    if (ARRAY_FIELDS.includes(k)) {
      (form as any)[k] = [];
    } else {
      (form as any)[k] = (empty as any)[k];
    }
  });
}

/** 将列表行响应映射进表单（编辑回显）；row 为 RegistrySpecificationResponse。 */
export function applyRowToForm(form: RegistrySpecFormModel, row: Record<string, any>): void {
  form.id = row.id;
  form.name = row.name || '';
  form.comment = row.comment || '';
  form.webAddress = row.webAddress || '';
  form.defaultLanguageId = row.defaultLanguageId || '';
  form.alternativeLanguageIds = [...(row.alternativeLanguageIds || [])];
  form.contextId = row.contextId || '';
  form.representationClassSchemeId = row.representationClassSchemeId || '';
  form.referenceDocumentIdentifierKinds = [...(row.referenceDocumentIdentifierKinds || [])];
  form.characterRepertoire = row.characterRepertoire || '';
  form.standard = row.standard || '';
  form.degreeOfConformance = row.degreeOfConformance || undefined;
  form.conformanceLevel = row.conformanceLevel || '';
  form.conformanceLabel = row.conformanceLabel || '';
  form.includedFeatures = row.includedFeatures || '';
  form.excludedFeatures = row.excludedFeatures || '';
  form.defaultTimeZone = row.defaultTimeZone || '';
}

/** 组装提交载荷；空值字段以 undefined 省略，集合字段空数组同样省略。 */
export function buildRegistrySpecPayload(form: RegistrySpecFormModel): Record<string, any> {
  return {
    name: form.name,
    comment: form.comment || undefined,
    webAddress: form.webAddress || undefined,
    characterRepertoire: form.characterRepertoire,
    standard: form.standard || undefined,
    degreeOfConformance: form.degreeOfConformance || undefined,
    conformanceLevel: form.conformanceLevel || undefined,
    conformanceLabel: form.conformanceLabel || undefined,
    includedFeatures: form.includedFeatures || undefined,
    excludedFeatures: form.excludedFeatures || undefined,
    defaultTimeZone: form.defaultTimeZone || undefined,
    defaultLanguageId: form.defaultLanguageId || undefined,
    alternativeLanguageIds: form.alternativeLanguageIds.length ? form.alternativeLanguageIds : undefined,
    contextId: form.contextId || undefined,
    representationClassSchemeId: form.representationClassSchemeId || undefined,
    referenceDocumentIdentifierKinds: form.referenceDocumentIdentifierKinds.length
      ? form.referenceDocumentIdentifierKinds
      : undefined,
  };
}

/** ISO/IEC 11179-3:2023 表 28 default_time_zone 格式：[-+]HHMM（如 +0800、-0230）。 */
export function timeZoneValidator(_rule: any, value: any, callback: (error?: Error) => void): void {
  if (!value || /^[-+]\d{4}$/.test(value)) {
    callback();
  } else {
    callback(new Error('时区格式应为 [-+]HHMM，如 +0800、-0230'));
  }
}
