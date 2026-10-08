import { describe, it, expect } from 'vitest';
import {
  createEmptyRegistrySpecForm,
  resetRegistrySpecForm,
  applyRowToForm,
  buildRegistrySpecPayload,
  timeZoneValidator,
  type RegistrySpecFormModel,
} from './registrySpecForm';

function sampleRow(): Record<string, any> {
  return {
    id: 'spec-1',
    name: '主注册规范',
    comment: '注释',
    webAddress: 'https://example.org/mdr',
    defaultLanguageId: 'lang-zh',
    alternativeLanguageIds: ['lang-en', 'lang-de'],
    characterRepertoire: 'UTF-8',
    contextId: 'ctx-1',
    representationClassSchemeId: 'rcs-1',
    referenceDocumentIdentifierKinds: ['URI', 'DOCNO'],
    standard: 'ISO/IEC 11179-3:2023',
    degreeOfConformance: 'STRICTLY_CONFORMING',
    conformanceLevel: 'level-1',
    conformanceLabel: '标签',
    includedFeatures: '包含',
    excludedFeatures: '排除',
    defaultTimeZone: '+0800',
  };
}

describe('applyRowToForm（编辑回显映射）', () => {
  it('映射表 28 全属性', () => {
    const form = createEmptyRegistrySpecForm();
    applyRowToForm(form, sampleRow());

    expect(form.id).toBe('spec-1');
    expect(form.name).toBe('主注册规范');
    expect(form.comment).toBe('注释');
    expect(form.webAddress).toBe('https://example.org/mdr');
    expect(form.defaultLanguageId).toBe('lang-zh');
    expect(form.alternativeLanguageIds).toEqual(['lang-en', 'lang-de']);
    expect(form.referenceDocumentIdentifierKinds).toEqual(['URI', 'DOCNO']);
    expect(form.representationClassSchemeId).toBe('rcs-1');
    expect(form.contextId).toBe('ctx-1');
    expect(form.characterRepertoire).toBe('UTF-8');
    expect(form.standard).toBe('ISO/IEC 11179-3:2023');
    expect(form.defaultTimeZone).toBe('+0800');
    expect(form.degreeOfConformance).toBe('STRICTLY_CONFORMING');
    expect(form.conformanceLevel).toBe('level-1');
    expect(form.conformanceLabel).toBe('标签');
    expect(form.includedFeatures).toBe('包含');
    expect(form.excludedFeatures).toBe('排除');
  });

  it('不再回显指称/定义/注册标识（注册规范非 Item 子类）', () => {
    const form = createEmptyRegistrySpecForm();
    applyRowToForm(form, {
      ...sampleRow(),
      designations: [{ id: 'd1', languageId: 'lang-zh', value: '主注册规范' }],
      definitions: [{ id: 'f1', languageId: 'lang-zh', value: '规范定义文本' }],
      meta: { registrationAuthorityName: 'RA', namespaceId: 'ns-1', identifier: 'RS.00001' },
    });

    expect(form).not.toHaveProperty('designations');
    expect(form).not.toHaveProperty('definitions');
    expect(form).not.toHaveProperty('registration');
  });

  it('行内可选字段缺失时集合回退为空数组、标量回退为空串', () => {
    const form = createEmptyRegistrySpecForm();
    applyRowToForm(form, { id: 'spec-2', name: 'X' });
    expect(form.alternativeLanguageIds).toEqual([]);
    expect(form.referenceDocumentIdentifierKinds).toEqual([]);
    expect(form.defaultTimeZone).toBe('');
    expect(form.comment).toBe('');
    expect(form.degreeOfConformance).toBeUndefined();
  });
});

describe('buildRegistrySpecPayload（提交载荷组装）', () => {
  it('全字段填写时完整提交，集合字段原样传递', () => {
    const form = createEmptyRegistrySpecForm();
    applyRowToForm(form, sampleRow());

    const payload = buildRegistrySpecPayload(form);

    expect(payload).toMatchObject({
      name: '主注册规范',
      characterRepertoire: 'UTF-8',
      standard: 'ISO/IEC 11179-3:2023',
      defaultTimeZone: '+0800',
      defaultLanguageId: 'lang-zh',
      alternativeLanguageIds: ['lang-en', 'lang-de'],
      referenceDocumentIdentifierKinds: ['URI', 'DOCNO'],
      representationClassSchemeId: 'rcs-1',
      contextId: 'ctx-1',
      degreeOfConformance: 'STRICTLY_CONFORMING',
      conformanceLevel: 'level-1',
      conformanceLabel: '标签',
      includedFeatures: '包含',
      excludedFeatures: '排除',
      comment: '注释',
      webAddress: 'https://example.org/mdr',
    });
    // 表 28 之外不得再上行指称/定义/注册标识
    expect(payload).not.toHaveProperty('designations');
    expect(payload).not.toHaveProperty('definitions');
    expect(payload).not.toHaveProperty('registration');
    // 已持久化行携带 id 由调用方（更新路径）决定，载荷本身不含 id
    expect(payload).not.toHaveProperty('id');
  });

  it('空值与空集合以 undefined 省略', () => {
    const form = createEmptyRegistrySpecForm();
    form.name = '仅必填';
    form.characterRepertoire = 'UTF-8';

    const payload = buildRegistrySpecPayload(form);

    expect(payload.comment).toBeUndefined();
    expect(payload.webAddress).toBeUndefined();
    expect(payload.alternativeLanguageIds).toBeUndefined();
    expect(payload.referenceDocumentIdentifierKinds).toBeUndefined();
    expect(payload.contextId).toBeUndefined();
    expect(payload.defaultTimeZone).toBeUndefined();
    expect(payload.degreeOfConformance).toBeUndefined();
    expect(payload).toEqual({ name: '仅必填', characterRepertoire: 'UTF-8' });
  });
});

describe('timeZoneValidator（default_time_zone 格式校验）', () => {
  function validate(value: any): Promise<void> {
    return new Promise((resolve, reject) => {
      timeZoneValidator(null, value, (err?: Error) => (err ? reject(err) : resolve()));
    });
  }

  it.each(['+0800', '-0230', '+0000'])('合法格式 %s 通过', async (v) => {
    await expect(validate(v)).resolves.toBeUndefined();
  });

  it.each(['800', '+08:00', '0800', '+080', '+08000', 'abcd'])('非法格式 "%s" 拒绝并提示', async (v) => {
    await expect(validate(v)).rejects.toThrow('[-+]HHMM');
  });

  it('空值（0..1）通过', async () => {
    await expect(validate('')).resolves.toBeUndefined();
    await expect(validate(null)).resolves.toBeUndefined();
  });
});

describe('resetRegistrySpecForm（重置）', () => {
  it('集合字段重置为空数组而非空字符串，编辑过的 id 也被清除', () => {
    const form = createEmptyRegistrySpecForm();
    applyRowToForm(form, sampleRow());
    resetRegistrySpecForm(form as RegistrySpecFormModel);

    expect(form.id).toBeUndefined();
    expect(form.alternativeLanguageIds).toEqual([]);
    expect(form.referenceDocumentIdentifierKinds).toEqual([]);
    expect(form.name).toBe('');
    expect(form.contextId).toBe('');
    expect(form.defaultTimeZone).toBe('');
  });
});
