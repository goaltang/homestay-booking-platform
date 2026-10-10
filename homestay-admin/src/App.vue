<template>
  <router-view />
</template>

<script setup lang="ts">
import { onMounted } from "vue";
import { useUserStore } from "@/stores/user";

const userStore = useUserStore();

onMounted(() => {
  // 应用加载时，从localStorage恢复用户信息
  userStore.loadUserInfo();
});
</script>

<style>
html,
body {
  margin: 0;
  padding: 0;
  width: 100%;
  height: 100vh;
  background-color: #f5f7fa;
}

#app {
  width: 100vw;
  height: 100vh;
  font-family:
    -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}
</style>
