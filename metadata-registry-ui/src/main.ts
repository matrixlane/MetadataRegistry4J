import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';

import App from './App.vue';
import router from './router';
import i18n from './i18n';

import './styles/layout.scss';

// 函数式组件（ElMessageBox.confirm / ElMessage / ElNotification）的 CSS
// 不会被 unplugin-vue-components 自动注入（只在模板里使用 <el-xxx> 时才注入），
// 这里手动按需引入，否则 ElMessageBox 弹窗会因缺 CSS 而撑满/错位。
// 注意必须引 style/index（sass 源）：style/css 是预编译默认主题，会带入
// base.css 里的 :root{--el-color-primary:#409eff}，覆盖 element-variables.scss 的主题色。
import 'element-plus/es/components/message-box/style/index';
import 'element-plus/es/components/message/style/index';
import 'element-plus/es/components/notification/style/index';

const app = createApp(App);

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

app.use(createPinia());
app.use(router);
// 全局中文语言包：未显式设置 placeholder 的 el-select 等组件默认文案（如 "Select"）
// 随之本地化为「请选择」，与界面语言一致。
app.use(ElementPlus, { locale: zhCn });
app.use(i18n);

app.mount('#app');
