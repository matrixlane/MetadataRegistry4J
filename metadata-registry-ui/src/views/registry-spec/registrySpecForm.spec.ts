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
    designations: [
      { id: 'd1', languageId: 'lang-zh', value: '主注册规范', contexts: [{ contextId: 'ctx-1', acceptability: 'PREFERRED' }] },
      { id: 'd2', languageId: 'lang-en', value: 'Main Spec' },
    ],
    definitions: [{ id: 'f1', languageId: 'lang-zh', value: '规范定义文本' }],
    meta: { registrationAuthorityName: 'RA', namespaceId: 'ns-1', identifier: 'RS.00001', version: '1.0.0' },
  };
}

describe('applyRowToForm（编辑回显映射）', () => {
  it('映射表 28 全属性与指称/定义（保留行 id 与上下文）', () => {
    const form = createEmptyRegistrySpecForm();
    applyRowToForm(form, sampleRow());

    expect(form.id).toBe('spec-1');
    expect(form.name).toBe('主注册规范');
    expect(form.alternativeLanguageIds).toEqual(['lang-en', 'lang-de']);
    expect(form.referenceDocumentIdentifierKinds).toEqual(['URI', 'DOCNO']);
    expect(form.representationClassSchemeId).toBe('rcs-1');
    expect(form.contextId).toBe('ctx-1');
    expect(form.defaultTimeZone).toBe('+0800');
    expect(form.degreeOfConformance).toBe('STRICTLY_CONFORMING');
    expect(form.designations).toEqual([
      {
        id: 'd1',
        languageId: 'lang-zh',
        value: '主注册规范',
        sourceId: undefined,
        contexts: [{ contextId: 'ctx-1', acceptability: 'PREFERRED' }],
      },
      { id: 'd2', languageId: 'lang-en', value: 'Main Spec', sourceId: undefined, contexts: undefined },
    ]);
    expect(form.definitions).toEqual([
      { id: 'f1', languageId: 'lang-zh', value: '规范定义文本', sourceId: undefined, contexts: undefined },
    ]);
  });

  it('行内可选字段缺失时不产生 undefined 键之外的异常', () => {
    const form = createEmptyRegistrySpecForm();
    applyRowToForm(form, { id: 'spec-2', name: 'X', designations: [{ languageId: 'lang-zh', value: 'V' }] });
    expect(form.designations[0].id).toBeUndefined();
    expect(form.alternativeLanguageIds).toEqual([]);
  });
});

describe('buildRegistrySpecPayload（提交载荷组装）', () => {
  it('全字段填写时完整提交，集合字段原样传递', () => {
    const form = createEmptyRegistrySpecForm();
    applyRowToForm(form, sampleRow());
    form.designations.push({ languageId: 'lang-fr', value: 'Spec principal' });

    const payload = buildRegistrySpecPayload(form, { namespaceId: 'ns-1' });

    expect(payload).toMatchObject({
      name: '主注册规范',
      characterRepertoire: 'UTF-8',
      defaultTimeZone: '+0800',
      defaultLanguageId: 'lang-zh',
      alternativeLanguageIds: ['lang-en', 'lang-de'],
      referenceDocumentIdentifierKinds: ['URI', 'DOCNO'],
      representationClassSchemeId: 'rcs-1',
      contextId: 'ctx-1',
      degreeOfConformance: 'STRICTLY_CONFORMING',
      registration: { namespaceId: 'ns-1' },
    });
    // 已持久化行携带 id（就地更新），新建行不带 id
    expect(payload.designations).toHaveLength(3);
    expect(payload.designations[0]).toMatchObject({ id: 'd1', languageId: 'lang-zh', value: '主注册规范' });
    expect(payload.designations[0].contexts).toEqual([{ contextId: 'ctx-1', acceptability: 'PREFERRED' }]);
    expect(payload.designations[2]).toMatchObject({ id: undefined, languageId: 'lang-fr' });
    expect(payload.definitions[0]).toMatchObject({ id: 'f1', value: '规范定义文本' });
  });

  it('空值与空集合以 undefined 省略，空文本行被过滤', () => {
    const form = createEmptyRegistrySpecForm();
    form.name = '仅必填';
    form.characterRepertoire = 'UTF-8';
    form.designations = [{ languageId: 'lang-zh', value: '  ' }];

    const payload = buildRegistrySpecPayload(form, undefined);

    expect(payload.comment).toBeUndefined();
    expect(payload.alternativeLanguageIds).toBeUndefined();
    expect(payload.referenceDocumentIdentifierKinds).toBeUndefined();
    expect(payload.contextId).toBeUndefined();
    expect(payload.defaultTimeZone).toBeUndefined();
    expect(payload.designations).toEqual([]);
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
    expect(form.designations).toEqual([]);
    expect(form.name).toBe('');
    expect(form.registration).toEqual({});
  });
});
