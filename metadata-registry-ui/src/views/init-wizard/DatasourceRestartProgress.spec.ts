import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import DatasourceRestartProgress from './DatasourceRestartProgress.vue';
import progressSource from './DatasourceRestartProgress.vue?raw';

describe('DatasourceRestartProgress', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-24T08:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('用原生不确定进度、真实阶段和已等待时间展示状态，不伪造百分比', async () => {
    const wrapper = mount(DatasourceRestartProgress, {
      global: {
        stubs: { 'el-button': { template: '<button type="button"><slot /></button>' } },
      },
      props: {
        phase: 'PREPARING',
        startedAt: Date.now() - 65_000,
      },
    });

    const progress = wrapper.find('progress');
    expect(progress.exists()).toBe(true);
    expect(progress.attributes('value')).toBeUndefined();
    expect(progress.attributes('aria-label')).toBe('数据库设置应用进度');
    expect(progress.attributes('tabindex')).toBeUndefined();
    expect(wrapper.text()).toContain('正在准备数据库');
    expect(wrapper.text()).toContain('已等待 1 分 05 秒');
    expect(wrapper.text()).not.toContain('%');

    const status = wrapper.find('[role="status"]');
    expect(status.attributes('aria-live')).toBe('polite');
    expect(status.attributes('aria-atomic')).toBe('true');
    expect(status.text()).toContain('正在准备数据库');
    expect(status.text()).not.toContain('已等待');

    await vi.advanceTimersByTimeAsync(1000);
    expect(wrapper.text()).toContain('已等待 1 分 06 秒');
    wrapper.unmount();
  });

  it('减少动态效果时放慢而不是停止活动指示', () => {
    expect(progressSource).toContain('@media (prefers-reduced-motion: reduce)');
    expect(progressSource).toContain('--_used-spinner-duration: 6s');
    expect(progressSource).not.toMatch(/prefers-reduced-motion[\s\S]*animation:\s*none/);
  });

  it('成功路径高亮"核对设置"，回滚路径高亮"恢复"', async () => {
    const mountProgress = (phase: 'SUCCEEDED' | 'ROLLED_BACK' | 'RECOVERING') =>
      mount(DatasourceRestartProgress, {
        global: {
          stubs: { 'el-button': { template: '<button type="button"><slot /></button>' } },
        },
        props: { phase, startedAt: Date.now() },
      });

    const succeeded = mountProgress('SUCCEEDED');
    const succeededStages = succeeded.findAll('.restart-stages li');
    expect(succeededStages).toHaveLength(5);
    expect(succeededStages[3].classes()).toContain('active');
    expect(succeededStages[4].classes()).not.toContain('active');
    succeeded.unmount();

    for (const phase of ['ROLLED_BACK', 'RECOVERING'] as const) {
      const wrapper = mountProgress(phase);
      const stages = wrapper.findAll('.restart-stages li');
      expect(stages[4].classes()).toContain('active');
      expect(stages[3].classes()).not.toContain('active');
      wrapper.unmount();
    }
  });

  it('恢复失败或超时停止等待并提供重试连接与支持指引', async () => {
    const wrapper = mount(DatasourceRestartProgress, {
      global: {
        stubs: { 'el-button': { template: '<button type="button"><slot /></button>' } },
      },
      props: {
        phase: 'FAILED',
        reasonCode: 'WAIT_TIMEOUT',
        startedAt: Date.now() - 390_000,
      },
    });

    expect(wrapper.find('progress').exists()).toBe(false);
    expect(wrapper.text()).toContain('本次自动操作已停止等待');
    expect(wrapper.text()).toContain('重新打开应用');
    expect(wrapper.text()).toContain('联系支持人员');
    const retry = wrapper.find('[data-testid="restart-retry"]');
    expect(retry.exists()).toBe(true);
    await retry.trigger('click');
    expect(wrapper.emitted('retry')).toHaveLength(1);
    wrapper.unmount();
  });
});
