/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import path from "path";

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const isTest = mode === "test";

  return {
    plugins: [
      vue(),
      !isTest &&
        AutoImport({
          resolvers: [ElementPlusResolver()],
        }),
      !isTest &&
        Components({
          resolvers: [ElementPlusResolver()],
        }),
    ].filter(Boolean),
    resolve: {
      alias: {
        "@": fileURLToPath(new URL("./src", import.meta.url)),
      },
      extensions: [".mjs", ".js", ".ts", ".jsx", ".tsx", ".json", ".vue"],
    },
    optimizeDeps: {
      include: ["vue", "vue-router", "element-plus"],
      exclude: [],
    },
    build: {
      sourcemap: true,
      minify: "esbuild",
      cssCodeSplit: true,
      rollupOptions: {
        output: {
          manualChunks: {
            vendor: ["vue", "vue-router"],
          },
        },
      },
    },
    server: {
      host: true,
      port: 5173,
      open: false,
      strictPort: false,
      proxy: {
        "/api": {
          target: "http://127.0.0.1:8081",
          changeOrigin: true,
          secure: false,
        },
        "/uploads": {
          target: "http://127.0.0.1:8081",
          changeOrigin: true,
        },
        "/ws": {
          target: "ws://127.0.0.1:8081",
          ws: true,
          changeOrigin: true,
        },
      },
    },
    define: {
      global: "globalThis",
      "process.env": {
        NODE_ENV: JSON.stringify(process.env.NODE_ENV || "development"),
      },
    },
    test: {
      globals: false,
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
      include: ["src/**/*.spec.ts"],
      clearMocks: true,
      restoreMocks: true,
    },
  };
});
