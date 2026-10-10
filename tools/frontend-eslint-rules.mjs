// 两个前端共用规则；依赖由各自子项目安装。
export default {
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
    // 历史调试日志先提示；敏感信息禁止写入日志。
    "no-console": "warn",
    "vue/require-default-prop": "off",
    "vue/max-attributes-per-line": "off",
    "vue/html-self-closing": "off",
    "vue/singleline-html-element-content-newline": "off",
  },
};
