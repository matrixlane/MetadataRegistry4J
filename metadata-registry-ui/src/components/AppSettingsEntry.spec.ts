import { describe, it, expect, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { createI18n } from 'vue-i18n';
import { createRouter, createMemoryHistory, type Router } from 'vue-router';
import AppSettingsEntry from './AppSettingsEntry.vue';
import zhCN from '@/i18n/zh-CN.json';

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } });

let router: Router;

function mountEntry() {
  router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/app-settings', component: { template: '<div />' } }],
  });
  return mount(AppSettingsEntry, {
    global: { plugins: [ElementPlus, i18n, router] },
  });
}

describe('AppSettingsEntry', () => {
  it('渲染应用设置入口（AI 辅助图标）', () => {
    const wrapper = mountEntry();
    expect(wrapper.find('[data-testid="app-settings-entry-btn"]').exists()).toBe(true);
  });

  it('点击入口导航到 /app-settings', async () => {
    const wrapper = mountEntry();
    await wrapper.find('[data-testid="app-settings-entry-btn"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.path).toBe('/app-settings');
  });
});
