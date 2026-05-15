import { createApp } from "vue";
import { createPinia } from "pinia";
import piniaPluginPersistedstate from "pinia-plugin-persistedstate";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";
import App from "./App.vue";
import router from "./router";
import "./assets/main.css";
import "./assets/input-styles.css";
import "./styles/design-system.css";
import { useUserStore } from "./stores/user";
import { extractErrorMessage } from "./types/error";

const app = createApp(App);
const pinia = createPinia();
pinia.use(piniaPluginPersistedstate);

app.use(pinia);
app.use(router);
app.use(ElementPlus, {
  locale: zhCn,
});

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

// 初始化用户信息
const initializeApp = async () => {
  const userStore = useUserStore();

  // 如果有token，尝试获取用户信息
  if (userStore.token) {
    try {
      console.log("应用启动时获取用户信息");
      await userStore.fetchUserInfo();
      console.log("用户信息获取成功:", userStore.userInfo);

      // 检查头像信息是否存在
      if (userStore.userInfo && !userStore.userInfo.avatar) {
        console.warn("用户信息中缺少头像，使用默认头像");
        // 设置默认头像
        const seed = userStore.userInfo.username || "default" + Date.now();
        const defaultAvatar = `https://api.dicebear.com/7.x/avataaars/svg?seed=${seed}`;
        userStore.userInfo.avatar = defaultAvatar;
        // 保存更新后的用户信息
        localStorage.setItem("homestay_user", JSON.stringify(userStore.userInfo));
      }
    } catch (error) {
      console.error("获取用户信息失败，可能需要重新登录:", extractErrorMessage(error));
      const apiError = error as { response?: { status: number } };
      // 如果获取用户信息失败，清除token
      if (apiError.response?.status === 401) {
        userStore.logout();
        router.push("/login");
      }
    }
  }

  // 挂载应用
  app.mount("#app");
};

// 启动应用
initializeApp();
