import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { createI18n } from 'vue-i18n';
import { createRouter, createMemoryHistory } from 'vue-router';
import SystemLayout from './SystemLayout.vue';
import zhCN from '@/i18n/zh-CN.json';

const i18n = createI18n({ legacy: false, locale: 'zh-CN', messages: { 'zh-CN': zhCN } });

async function mountLayout(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/system/users', component: { template: '<div />' } },
      { path: '/system/general', component: { template: '<div />' } },
      { path: '/system/datasource', component: { template: '<div />' } },
      { path: '/system/danger', component: { template: '<div />' } },
    ],
  });
  await router.push(path);
  await router.isReady();
  return mount(SystemLayout, { global: { plugins: [ElementPlus, i18n, router] } });
}

describe('SystemLayout', () => {
  it('渲染一级平面菜单：通用/用户管理/数据源/危险区域', async () => {
    const wrapper = await mountLayout('/system/general');
    expect(wrapper.find('[data-testid="system-title"]').text()).toContain('系统');
    expect(wrapper.find('[data-testid="system-nav-general"]').text()).toContain('通用');
    expect(wrapper.find('[data-testid="system-nav-users"]').text()).toContain('用户管理');
    expect(wrapper.find('[data-testid="system-nav-datasource"]').text()).toContain('数据源');
    expect(wrapper.find('[data-testid="system-nav-danger"]').text()).toContain('危险区域');
  });

  it('当前路由对应菜单项激活', async () => {
    const wrapper = await mountLayout('/system/datasource');
    expect(wrapper.find('[data-testid="system-nav-datasource"]').classes()).toContain('is-active');
  });

  it('仅用户管理视图宽度自适应，其余页保持原宽度', async () => {
    const users = await mountLayout('/system/users');
    expect(users.find('.system-layout').classes()).toContain('is-users-view');
    const general = await mountLayout('/system/general');
    expect(general.find('.system-layout').classes()).not.toContain('is-users-view');
  });
});
