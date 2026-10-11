import { createApp } from "vue";
import { createPinia } from "pinia";
import piniaPluginPersistedstate from "pinia-plugin-persistedstate";
// Element Plus 组件通过 unplugin-vue-components + ElementPlusResolver 按需自动导入，
// 这里只保留全量 CSS（命令式 API 如 ElMessage 的样式依赖它）。
import "element-plus/dist/index.css";
import App from "./App.vue";
import router from "./router";
import "./assets/main.css";
import "./assets/input-styles.css";
import "./styles/design-system.css";
import { useUserStore } from "./stores/user";
const app = createApp(App);
const pinia = createPinia();
pinia.use(piniaPluginPersistedstate);

app.use(pinia);
const initialization = useUserStore().initializeSession();
app.use(router);

initialization.then(() => app.mount("#app"));
