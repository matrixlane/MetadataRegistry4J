import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import type { VueWrapper } from '@vue/test-utils';
import AiCandidatePopover from './AiCandidatePopover.vue';

const mocks = vi.hoisted(() => ({
  createJob: vi.fn(),
  cancelJob: vi.fn(),
  poll: vi.fn(),
}));

vi.mock('@/api/assistant', () => ({
  assistantApi: {
    tasks: vi.fn(),
    listDebugExchanges: vi.fn(),
    createJob: mocks.createJob,
    getJob: vi.fn(),
    cancelJob: mocks.cancelJob,
  },
  pollAssistantJob: mocks.poll,
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

const mounted: VueWrapper[] = [];

function mountPopover(props: Record<string, unknown> = {}) {
  const anchor = document.createElement('div');
  document.body.appendChild(anchor);
  const wrapper = mount(AiCandidatePopover, {
    attachTo: document.body,
    props: {
      visible: true,
      anchor,
      field: 'designation',
      itemId: 'item-1',
      cacheKey: 'designation:dg-1',
      autoGenerate: false,
      ...props,
    },
    global: { plugins: [ElementPlus] },
  });
  mounted.push(wrapper);
  return wrapper;
}

function panel(): HTMLElement {
  const el = document.body.querySelector('.ai-candidate-panel');
  expect(el).toBeTruthy();
  return el as HTMLElement;
}

function leavePopper() {
  panel().dispatchEvent(new Event('mouseleave'));
}

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount();
  document.body.innerHTML = '';
  vi.useRealTimers();
});

describe('AiCandidatePopover 悬停回看', () => {
  it('有缓存时直接展示上次候选，不发请求', async () => {
    const wrapper = mountPopover({
      autoGenerate: false,
      cached: [{ text: '旧候选' }],
    });
    await flushPromises();
    expect(mocks.createJob).not.toHaveBeenCalled();
    const item = document.body.querySelector('[data-test="ai-candidate-item-0"]');
    expect(item?.textContent).toContain('旧候选');
    expect(wrapper.emitted('generated')).toBeUndefined();
  });
});

describe('AiCandidatePopover 点击生成', () => {
  it('发起请求并在成功后回传 generated 事件', async () => {
    mocks.createJob.mockResolvedValue({ jobId: 'job-1' });
    mocks.poll.mockResolvedValue({
      id: 'job-1', taskId: 'complete-metadata', status: 'SUCCEEDED',
      result: [{ text: '新候选' }], degraded: false,
    });
    const wrapper = mountPopover({ autoGenerate: true });
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(1);
    const item = document.body.querySelector('[data-test="ai-candidate-item-0"]');
    expect(item?.textContent).toContain('新候选');
    expect(wrapper.emitted('generated')?.[0]?.[0]).toEqual({
      key: 'designation:dg-1',
      candidates: [{ text: '新候选' }],
    });
  });

  it('失败进入错误态不覆盖旧缓存，重试再次发起请求', async () => {
    mocks.createJob.mockResolvedValue({ jobId: 'job-1' });
    mocks.poll.mockResolvedValue({
      id: 'job-1', taskId: 'complete-metadata', status: 'FAILED',
      error: 'boom', degraded: false,
    });
    const wrapper = mountPopover({ autoGenerate: true });
    await flushPromises();
    expect(document.body.querySelector('[data-test="ai-candidate-error"]')).toBeTruthy();
    expect(wrapper.emitted('generated')).toBeUndefined();

    (document.body.querySelector('[data-test="ai-candidate-retry"]') as HTMLElement).click();
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(2);
  });
});

describe('AiCandidatePopover 新建模式 draft 载荷', () => {
  it('无 itemId 时以 params.draft 发起；itemId 存在时不带 draft（后端 itemId 优先契约）', async () => {
    mocks.createJob.mockResolvedValue({ jobId: 'job-1' });
    mocks.poll.mockResolvedValue({
      id: 'job-1', taskId: 'complete-metadata', status: 'SUCCEEDED',
      result: [{ text: '新候选' }], degraded: false,
    });
    const draft = {
      itemType: 'dataElement',
      designations: [{ language: 'zh-CN', value: '血压' }],
      definitions: [{ value: '定义文本' }],
    };
    // 新建模式：itemId 为空 + draft
    const wrapper = mountPopover({ autoGenerate: true, itemId: '', draft });
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(1);
    expect(mocks.createJob.mock.calls[0][0]).toEqual({
      taskId: 'complete-metadata',
      params: { fields: ['designation'], draft },
    });

    // 编辑模式：itemId 存在 → 不发 draft
    await wrapper.setProps({ visible: false });
    await wrapper.setProps({ visible: true, itemId: 'item-9', draft: null });
    mocks.poll.mockResolvedValue({
      id: 'job-2', taskId: 'complete-metadata', status: 'SUCCEEDED',
      result: [{ text: '新候选2' }], degraded: false,
    });
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(2);
    expect(mocks.createJob.mock.calls[1][0]).toEqual({
      taskId: 'complete-metadata',
      itemId: 'item-9',
      params: { fields: ['designation'] },
    });
  });
});

describe('AiCandidatePopover 延迟关闭', () => {
  it('浮层 mouseleave 后约 200ms 才关闭', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    const wrapper = mountPopover({
      autoGenerate: false,
      cached: [{ text: '旧候选' }],
      buttonHover: false,
    });
    leavePopper();
    vi.advanceTimersByTime(199);
    expect(wrapper.emitted('close') ?? []).toHaveLength(0);
    vi.advanceTimersByTime(1);
    expect(wrapper.emitted('close')).toHaveLength(1);
  });

  it('按钮悬停中即使离开浮层也不关闭', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    const wrapper = mountPopover({
      autoGenerate: false,
      cached: [{ text: '旧候选' }],
      buttonHover: true,
    });
    leavePopper();
    vi.advanceTimersByTime(1000);
    expect(wrapper.emitted('close') ?? []).toHaveLength(0);
  });

  it('加载中离开不关闭，生成结束后补判定再关闭', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    mocks.createJob.mockResolvedValue({ jobId: 'job-1' });
    let resolvePoll!: (value: unknown) => void;
    mocks.poll.mockImplementation(() => new Promise((res) => { resolvePoll = res; }));
    const wrapper = mountPopover({ autoGenerate: true, buttonHover: false });
    await flushPromises();
    expect(document.body.querySelector('[data-test="ai-candidate-loading"]')).toBeTruthy();

    leavePopper();
    vi.advanceTimersByTime(1000);
    expect(wrapper.emitted('close') ?? []).toHaveLength(0);

    resolvePoll({
      id: 'job-1', taskId: 'complete-metadata', status: 'SUCCEEDED',
      result: [{ text: '新候选' }], degraded: false,
    });
    await flushPromises();
    vi.advanceTimersByTime(200);
    expect(wrapper.emitted('close')).toHaveLength(1);
  });
});

describe('AiCandidatePopover 生成中途关闭（回归：缓存 key 不得丢失）', () => {
  it('visible/cacheKey 在轮询期间被父级重置为空，成功仍回传发起时的 key', async () => {
    mocks.createJob.mockResolvedValue({ jobId: 'job-1' });
    let resolvePoll!: (value: unknown) => void;
    mocks.poll.mockImplementation(() => new Promise((res) => { resolvePoll = res; }));
    const wrapper = mountPopover({ autoGenerate: true, cacheKey: 'designation:dg-1' });
    await flushPromises();
    expect(document.body.querySelector('[data-test="ai-candidate-loading"]')).toBeTruthy();

    // 复刻真实缺陷：生成中浮层被关闭（如点击浮层外），父级 aiGen=null → visible=false、cacheKey 回退 ''
    await wrapper.setProps({ visible: false, cacheKey: '' });

    resolvePoll({
      id: 'job-1', taskId: 'complete-metadata', status: 'SUCCEEDED',
      result: [{ text: '新候选' }], degraded: false,
    });
    await flushPromises();
    expect(wrapper.emitted('generated')?.[0]?.[0]).toEqual({
      key: 'designation:dg-1',
      candidates: [{ text: '新候选' }],
    });
  });
});

describe('AiCandidatePopover 进行中悬停重开', () => {
  it('生成中关闭后悬停重开为加载态且不发新请求，结果到达就地展示候选', async () => {
    mocks.createJob.mockResolvedValue({ jobId: 'job-1' });
    let resolvePoll!: (value: unknown) => void;
    mocks.poll.mockImplementation(() => new Promise((res) => { resolvePoll = res; }));
    const wrapper = mountPopover({ autoGenerate: true });
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(1);
    expect(document.body.querySelector('[data-test="ai-candidate-loading"]')).toBeTruthy();
    expect(wrapper.emitted('inflight-change')?.[0]?.[0]).toEqual({ key: 'designation:dg-1', inflight: true });

    // 生成中浮层被关闭（click-outside/悬停移出）
    await wrapper.setProps({ visible: false });
    expect(document.body.querySelector('[data-test="ai-candidate-loading"]')).toBeFalsy();

    // 再次悬停：重开浮层并恢复加载态；inflight 防重入，不得重复发请求
    await wrapper.setProps({ visible: true, autoGenerate: true });
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(1);
    expect(document.body.querySelector('[data-test="ai-candidate-loading"]')).toBeTruthy();

    // 轮询返回：同一浮层就地切换为候选列表，inflight-change 收尾
    resolvePoll({
      id: 'job-1', taskId: 'complete-metadata', status: 'SUCCEEDED',
      result: [{ text: '新候选' }], degraded: false,
    });
    await flushPromises();
    expect(document.body.querySelector('[data-test="ai-candidate-item-0"]')?.textContent).toContain('新候选');
    expect(wrapper.emitted('generated')).toHaveLength(1);
    expect(wrapper.emitted('inflight-change')?.at(-1)?.[0]).toEqual({ key: 'designation:dg-1', inflight: false });
  });

  it('进行中重开优先展示加载态（即使父级已传入缓存）', async () => {
    mocks.createJob.mockResolvedValue({ jobId: 'job-1' });
    mocks.poll.mockImplementation(() => new Promise(() => {}));
    const wrapper = mountPopover({ autoGenerate: true });
    await flushPromises();
    await wrapper.setProps({ visible: false });
    await wrapper.setProps({ visible: true, autoGenerate: true, cached: [{ text: '旧候选' }] });
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(1);
    expect(document.body.querySelector('[data-test="ai-candidate-loading"]')).toBeTruthy();
    expect(document.body.querySelector('[data-test="ai-candidate-item-0"]')).toBeFalsy();
  });

  it('悬停区分：非进行中且 autoGenerate=false 时仍走缓存回看，不发请求', async () => {
    const wrapper = mountPopover({ autoGenerate: false, cached: [{ text: '旧候选' }] });
    await flushPromises();
    expect(mocks.createJob).not.toHaveBeenCalled();
    expect(document.body.querySelector('[data-test="ai-candidate-item-0"]')?.textContent).toContain('旧候选');
    expect(document.body.querySelector('[data-test="ai-candidate-loading"]')).toBeFalsy();
  });

  it('失败后悬停重开：展示错误态与重试入口，不自动重发', async () => {
    mocks.createJob.mockResolvedValue({ jobId: 'job-1' });
    mocks.poll.mockResolvedValue({
      id: 'job-1', taskId: 'complete-metadata', status: 'FAILED',
      error: 'boom', degraded: false,
    });
    const wrapper = mountPopover({ autoGenerate: true });
    await flushPromises();
    expect(document.body.querySelector('[data-test="ai-candidate-error"]')).toBeTruthy();
    expect(wrapper.emitted('failed')?.[0]?.[0]).toEqual({ key: 'designation:dg-1', message: 'boom' });
    expect(wrapper.emitted('inflight-change')?.at(-1)?.[0]).toEqual({ key: 'designation:dg-1', inflight: false });

    // 失败后关闭再悬停：父级经 lastError 传回失败信息 → 错误态保留，悬停不自动重发
    await wrapper.setProps({ visible: false });
    await wrapper.setProps({ visible: true, lastError: 'boom' });
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(1);
    expect(document.body.querySelector('[data-test="ai-candidate-error"]')).toBeTruthy();

    // 错误态提供重试入口，用户主动点击才再次发起
    (document.body.querySelector('[data-test="ai-candidate-retry"]') as HTMLElement).click();
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(2);
  });
});

describe('AiCandidatePopover 取消', () => {
  it('生成中点击取消：调用 cancelJob 并关闭', async () => {
    mocks.createJob.mockResolvedValue({ jobId: 'job-1' });
    mocks.poll.mockImplementation(() => new Promise(() => {}));
    const wrapper = mountPopover({ autoGenerate: true });
    await flushPromises();
    expect(document.body.querySelector('[data-test="ai-candidate-cancel"]')).toBeTruthy();

    (document.body.querySelector('[data-test="ai-candidate-cancel"]') as HTMLElement).click();
    await flushPromises();
    expect(mocks.cancelJob).toHaveBeenCalledWith('job-1');
    expect(wrapper.emitted('close')).toHaveLength(1);
  });
});

describe('AiCandidatePopover 防重入（一次点击严格一次请求）', () => {
  it('生成中 watcher 重复触发（模式切换）不重复发请求', async () => {
    mocks.createJob.mockResolvedValue({ jobId: 'job-1' });
    let resolvePoll!: (value: unknown) => void;
    mocks.poll.mockImplementation(() => new Promise((res) => { resolvePoll = res; }));
    const wrapper = mountPopover({ autoGenerate: true, cacheKey: 'k' });
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(1);
    expect(document.body.querySelector('[data-test="ai-candidate-loading"]')).toBeTruthy();

    // 模拟 watcher 并发/悬停-view 与 generate 来回切换导致的重复 start 触发
    await wrapper.setProps({ autoGenerate: false });
    await wrapper.setProps({ autoGenerate: true });
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(1);
    // 重复触发仅同步展示态，仍为 loading
    expect(document.body.querySelector('[data-test="ai-candidate-loading"]')).toBeTruthy();

    resolvePoll({
      id: 'job-1', taskId: 'complete-metadata', status: 'SUCCEEDED',
      result: [{ text: '新候选' }], degraded: false,
    });
    await flushPromises();
    expect(wrapper.emitted('generated')?.[0]?.[0]).toEqual({ key: 'k', candidates: [{ text: '新候选' }] });
  });

  it('生成失败后可重试（防重入不得挡住错误态重发）', async () => {
    mocks.createJob.mockResolvedValue({ jobId: 'job-1' });
    mocks.poll.mockResolvedValue({
      id: 'job-1', taskId: 'complete-metadata', status: 'FAILED', error: 'boom', degraded: false,
    });
    const wrapper = mountPopover({ autoGenerate: true });
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(1);
    expect(document.body.querySelector('[data-test="ai-candidate-error"]')).toBeTruthy();

    (document.body.querySelector('[data-test="ai-candidate-retry"]') as HTMLElement).click();
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(2);
  });

  it('取消后允许立即重新发起；旧作业晚到不再回写缓存或补发关闭', async () => {
    mocks.createJob
      .mockResolvedValueOnce({ jobId: 'job-1' })
      .mockResolvedValueOnce({ jobId: 'job-2' });
    let resolveFirst!: (value: unknown) => void;
    mocks.poll
      .mockImplementationOnce(() => new Promise((res) => { resolveFirst = res; }))
      .mockResolvedValueOnce({
        id: 'job-2', taskId: 'complete-metadata', status: 'SUCCEEDED',
        result: [{ text: '新候选2' }], degraded: false,
      });
    const wrapper = mountPopover({ autoGenerate: true, cacheKey: 'k' });
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(1);

    (document.body.querySelector('[data-test="ai-candidate-cancel"]') as HTMLElement).click();
    await flushPromises();
    expect(mocks.cancelJob).toHaveBeenCalledWith('job-1');
    expect(wrapper.emitted('close')).toHaveLength(1);

    // 取消后立即重新发起（父级重开浮层 → watcher 再次触发 start）
    await wrapper.setProps({ visible: false });
    await wrapper.setProps({ visible: true, autoGenerate: true });
    await flushPromises();
    expect(mocks.createJob).toHaveBeenCalledTimes(2);

    // 旧作业（job-1）晚到：不得覆盖新结果、不得补发 close
    resolveFirst({
      id: 'job-1', taskId: 'complete-metadata', status: 'SUCCEEDED',
      result: [{ text: '旧候选' }], degraded: false,
    });
    await flushPromises();
    expect(wrapper.emitted('generated')?.length ?? 0).toBe(1);
    expect(wrapper.emitted('generated')?.[0]?.[0]).toEqual({ key: 'k', candidates: [{ text: '新候选2' }] });
    expect(wrapper.emitted('close')).toHaveLength(1);
    expect(document.body.querySelector('[data-test="ai-candidate-item-0"]')?.textContent).toContain('新候选2');
  });
});
