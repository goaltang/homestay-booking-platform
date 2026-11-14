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
        // 按包路径分组：@vue/* 子包必须与 vue 同组，否则会被并入 element-plus，
        // 形成 vue-core ⇄ element-plus 循环依赖，生产包白屏（"Cannot access … before initialization"）
        manualChunks(id) {
          if (!id.includes("node_modules")) return;
          if (/node_modules[\\/](@vue[\\/]|vue[\\/]|vue-router[\\/]|pinia[\\/]|vue-demi[\\/])/.test(id)) return "vue-core";
          if (/node_modules[\\/](element-plus|@element-plus)[\\/]/.test(id)) return "element-plus";
          if (/node_modules[\\/](echarts|zrender)[\\/]/.test(id)) return "echarts";
          if (id.includes("element-china-area-data")) return "china-area-data";
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
