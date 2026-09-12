import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';
import { resolve } from 'path';

const __dirname = import.meta.dirname;

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.spec.ts'],
  },
  resolve: {
    alias: [
      // public 目录静态资源在测试中无实际文件可载，统一指向空模块桩
      { find: /^\/?favicon\.svg$/, replacement: resolve(__dirname, 'src/test-stubs/static-asset.ts') },
      { find: '@', replacement: resolve(__dirname, 'src') },
    ],
  },
});