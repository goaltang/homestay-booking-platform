import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      // directives: true —— 按需注册 v-loading 等指令（移除 app.use(ElementPlus) 后必须）
      resolvers: [ElementPlusResolver({ directives: true })],
    }),
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // Vue 与 Element Plus 共用依赖，放在同一块避免循环导入时提前访问 Vue。
          "ui-vendor": ["element-plus", "@element-plus/icons-vue", "vue", "vue-router", "pinia"],
          echarts: ["echarts", "zrender"],
          "china-area-data": ["element-china-area-data"],
        },
      },
    },
  },
  server: {
    port: 5174,
    proxy: {
      "/api": {
        // 后端固定 8081（8080 常被本机 Dify 占用）
        target: "http://localhost:8081",
        changeOrigin: true,
      },
    },
  },
});
