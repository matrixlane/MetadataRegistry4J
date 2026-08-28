import { createApp } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';

import App from './App.vue';
import router from './router';
import i18n from './i18n';

import './styles/layout.scss';

// 函数式组件（ElMessageBox.confirm / ElMessage / ElNotification）的 CSS
// 不会被 unplugin-vue-components 自动注入（只在模板里使用 <el-xxx> 时才注入），
// 这里手动按需引入，否则 ElMessageBox 弹窗会因缺 CSS 而撑满/错位。
import 'element-plus/es/components/message-box/style/css';
import 'element-plus/es/components/message/style/css';
import 'element-plus/es/components/notification/style/css';

const app = createApp(App);

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

app.use(createPinia());
app.use(router);
app.use(ElementPlus);
app.use(i18n);

app.mount('#app');
