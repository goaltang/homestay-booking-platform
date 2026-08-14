import { defineConfigWithVueTs, vueTsConfigs } from "@vue/eslint-config-typescript";
import pluginVue from "eslint-plugin-vue";
import eslintConfigPrettier from "eslint-config-prettier";

export default defineConfigWithVueTs(
  {
    name: "app/files-to-lint",
    files: ["**/*.{ts,mts,tsx,vue}"],
  },
  {
    name: "app/files-to-ignore",
    ignores: ["**/dist/**", "**/node_modules/**", "**/*.d.ts"],
  },
  pluginVue.configs["flat/essential"],
  vueTsConfigs.recommended,
  eslintConfigPrettier,
  {
    name: "app/custom-rules",
    rules: {
      // 项目存在较多单名单词组件（如 Login.vue），关闭该规则
      "vue/multi-word-component-names": "off",
      // 历史遗留 any 较多，先降级为警告
      "@typescript-eslint/no-explicit-any": "warn",
      // 未使用变量（vue-tsc 已开启 noUnusedLocals，这里保持一致并允许下划线前缀）
      "@typescript-eslint/no-unused-vars": [
        "warn",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
      ],
      // 生产构建已通过 esbuild drop console，这里仅提示
      "no-console": "warn",
      "vue/require-default-prop": "off",
      "vue/max-attributes-per-line": "off",
      "vue/html-self-closing": "off",
      "vue/singleline-html-element-content-newline": "off",
    },
  }
);
