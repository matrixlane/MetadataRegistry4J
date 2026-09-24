import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { createI18n } from 'vue-i18n';
import { createRouter, createMemoryHistory, type Router } from 'vue-router';
import WorkspaceEntry from './WorkspaceEntry.vue';
import { workspaceApi } from '@/api/resources';
import zhCN from '@/i18n/zh-CN.json';

const summaryMock = vi.hoisted(() => ({ value: vi.fn() }));

vi.mock('@/api/resources', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/resources')>();
  return {
    ...actual,
    workspaceApi: { ...actual.workspaceApi, summary: summaryMock.value },
  };
});

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } });

let wrapper: VueWrapper | null = null;
let router: Router;

function mountEntry() {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/workspace', component: { template: '<div />' } }],
  });
  wrapper = mount(WorkspaceEntry, {
    global: { plugins: [ElementPlus, i18n, router] },
  });
  return wrapper;
}

function badgeSup() {
  return wrapper!.find('[data-testid="workspace-entry-badge"] sup');
}

describe('WorkspaceEntry', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    summaryMock.value.mockReset();
  });

  afterEach(() => {
    // 卸载以触发 onBeforeUnmount：徽标 composable 为模块级单例轮询，不卸载会跨用例泄漏计时器
    wrapper?.unmount();
    wrapper = null;
    vi.useRealTimers();
  });

  it('挂载后拉取草稿计数并显示徽标', async () => {
    summaryMock.value.mockResolvedValue({ drafts: 5, pending: 0, rejected: 0 });
    mountEntry();
    await flushPromises();
    expect(summaryMock.value).toHaveBeenCalled();
    expect(badgeSup().text()).toBe('5');
    expect(badgeSup().isVisible()).toBe(true);
  });

  it('计数为 0 时隐藏徽标', async () => {
    summaryMock.value.mockResolvedValue({ drafts: 0, pending: 2, rejected: 1 });
    mountEntry();
    await flushPromises();
    expect(badgeSup().exists()).toBe(false);
  });

  it('按间隔轮询刷新计数', async () => {
    summaryMock.value
      .mockResolvedValueOnce({ drafts: 1, pending: 0, rejected: 0 })
      .mockResolvedValueOnce({ drafts: 3, pending: 0, rejected: 0 });
    mountEntry();
    await flushPromises();
    expect(badgeSup().text()).toBe('1');
    await vi.advanceTimersByTimeAsync(31_000);
    expect(summaryMock.value).toHaveBeenCalledTimes(2);
    expect(badgeSup().text()).toBe('3');
  });

  it('拉取失败保留上一次计数', async () => {
    summaryMock.value
      .mockResolvedValueOnce({ drafts: 2, pending: 0, rejected: 0 })
      .mockRejectedValueOnce(new Error('network'));
    mountEntry();
    await flushPromises();
    await vi.advanceTimersByTimeAsync(31_000);
    await flushPromises();
    expect(badgeSup().text()).toBe('2');
  });

  it('点击入口导航到 /workspace', async () => {
    summaryMock.value.mockResolvedValue({ drafts: 0, pending: 0, rejected: 0 });
    const w = mountEntry();
    await flushPromises();
    await w.find('[data-testid="workspace-entry-btn"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/workspace');
  });
});
