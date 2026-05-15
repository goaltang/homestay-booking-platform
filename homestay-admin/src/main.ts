import { createApp } from "vue";
import ElementPlus from "element-plus";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";
import "element-plus/dist/index.css";
import App from "./App.vue";
import router from "./router";
import { createPinia } from "pinia";
import { useUserStore } from "@/stores/user";
import "./style.css";

const app = createApp(App);
const pinia = createPinia();

// 权限指令 — 基于用户角色控制元素显示
app.directive("permiss", {
  mounted(el, binding) {
    const userStore = useUserStore();
    const requiredRoles = binding.value;

    if (!requiredRoles) return;

    const roles = Array.isArray(requiredRoles) ? requiredRoles : [requiredRoles];
    const userRole = userStore.userInfo?.role || '';
    const hasPermission = roles.some(
      (role: string) => userRole.toUpperCase() === role.toUpperCase()
    );

    if (!hasPermission) {
      el.parentNode?.removeChild(el);
    }
  },
});

// 全局错误处理
app.config.errorHandler = (err, instance, info) => {
  console.error("全局错误:", err);
  console.error("错误来源:", instance);
  console.error("错误信息:", info);
};

// 捕获未处理的Promise异常
window.addEventListener("unhandledrejection", (event) => {
  console.error("未处理的Promise拒绝:", event.reason);
});

// 注册 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

app.use(ElementPlus);
app.use(router);
app.use(pinia);

app.mount("#app");
