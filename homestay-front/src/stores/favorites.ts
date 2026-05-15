import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useRouter } from "vue-router";
import { useUserStore } from "./user";
import {
  toggleFavorite as toggleFavoriteApi,
  getUserFavoriteIds,
  clearUserFavorites as clearUserFavoritesApi,
  getUserFavoriteCount,
} from "@/api/favorites";

export const useFavoritesStore = defineStore("favorites", () => {
  const router = useRouter();

  // 状态
  const favoriteIds = ref<number[]>([]);
  const loading = ref(false);
  const synced = ref(false); // 标记是否已与服务器同步

  // 计算属性
  const favoritesCount = computed(() => favoriteIds.value.length);

  // 方法
  const loadFavorites = async () => {
    const userStore = useUserStore();

    try {
      // 如果用户已登录，优先从服务器加载
      if (userStore.isAuthenticated) {
        await loadFromServer();
      } else {
        // 未登录时清空收藏数据，不应该显示任何收藏信息
        clearLocalFavorites();
      }
    } catch (error) {
      console.error("加载收藏数据失败:", error);
      // 如果用户已登录但加载失败，降级到localStorage
      if (userStore.isAuthenticated) {
        loadFromLocalStorage();
      } else {
        // 未登录用户出错时也清空收藏
        clearLocalFavorites();
      }
    }
  };

  const loadFromLocalStorage = () => {
    try {
      const saved = localStorage.getItem("favorites");
      if (saved) {
        favoriteIds.value = JSON.parse(saved);
      }
    } catch (error) {
      console.error("从localStorage加载收藏数据失败:", error);
      favoriteIds.value = [];
    }
  };

  const clearLocalFavorites = () => {
    favoriteIds.value = [];
    synced.value = false;
    localStorage.removeItem("favorites");
    console.log("已清空本地收藏数据");
  };

  const loadFromServer = async () => {
    try {
      loading.value = true;
      const response = await getUserFavoriteIds();
      if (response.data && response.data.success) {
        favoriteIds.value = response.data.data || [];
        synced.value = true;
        // 同步到localStorage作为备份
        saveFavorites();
        console.log("从服务器加载收藏数据成功:", favoriteIds.value);
      }
    } catch (error) {
      console.error("从服务器加载收藏数据失败:", error);
      throw error;
    } finally {
      loading.value = false;
    }
  };

  const saveFavorites = () => {
    try {
      localStorage.setItem("favorites", JSON.stringify(favoriteIds.value));
    } catch (error) {
      console.error("保存收藏数据失败:", error);
    }
  };

  const isFavorite = (id: number): boolean => {
    return favoriteIds.value.includes(id);
  };

  // 检查登录状态
  const checkAuthAndPrompt = async (): Promise<boolean> => {
    const userStore = useUserStore();

    if (!userStore.isAuthenticated) {
      try {
        await ElMessageBox.confirm("请先登录后再进行收藏操作", "需要登录", {
          confirmButtonText: "去登录",
          cancelButtonText: "取消",
          type: "info",
        });

        // 用户点击了确认，跳转到登录页
        router.push("/login");
        return false;
      } catch {
        // 用户点击了取消
        return false;
      }
    }

    return true;
  };

  const addToFavorites = async (id: number) => {
    if (!(await checkAuthAndPrompt())) {
      return false;
    }

    const userStore = useUserStore();

    try {
      if (userStore.isAuthenticated) {
        // 使用服务器API
        const response = await toggleFavoriteApi(id);
        if (response.data && response.data.success) {
          const result = response.data.data;
          if (result.action === "added") {
            favoriteIds.value.push(id);
            saveFavorites();
            ElMessage.success("已添加到收藏");
            return true;
          }
        }
      } else {
        // 离线模式
        if (!isFavorite(id)) {
          favoriteIds.value.push(id);
          saveFavorites();
          ElMessage.success("已添加到收藏");
          return true;
        }
      }
    } catch (error) {
      console.error("添加收藏失败:", error);
      ElMessage.error("添加收藏失败，请稍后重试");
    }
    return false;
  };

  const removeFromFavorites = async (id: number) => {
    if (!(await checkAuthAndPrompt())) {
      return false;
    }

    const userStore = useUserStore();

    try {
      if (userStore.isAuthenticated) {
        // 使用服务器API
        const response = await toggleFavoriteApi(id);
        if (response.data && response.data.success) {
          const result = response.data.data;
          if (result.action === "removed") {
            const index = favoriteIds.value.indexOf(id);
            if (index > -1) {
              favoriteIds.value.splice(index, 1);
              saveFavorites();
              ElMessage.success("已从收藏中移除");
              return true;
            }
          }
        }
      } else {
        // 离线模式
        const index = favoriteIds.value.indexOf(id);
        if (index > -1) {
          favoriteIds.value.splice(index, 1);
          saveFavorites();
          ElMessage.success("已从收藏中移除");
          return true;
        }
      }
    } catch (error) {
      console.error("取消收藏失败:", error);
      ElMessage.error("取消收藏失败，请稍后重试");
    }
    return false;
  };

  const toggleFavorite = async (id: number) => {
    if (!(await checkAuthAndPrompt())) {
      return;
    }

    const userStore = useUserStore();

    try {
      if (userStore.isAuthenticated) {
        // 使用服务器API
        const response = await toggleFavoriteApi(id);
        if (response.data && response.data.success) {
          const result = response.data.data;
          if (result.action === "added") {
            favoriteIds.value.push(id);
            ElMessage.success("已添加到收藏");
          } else if (result.action === "removed") {
            const index = favoriteIds.value.indexOf(id);
            if (index > -1) {
              favoriteIds.value.splice(index, 1);
            }
            ElMessage.success("已从收藏中移除");
          }
          saveFavorites();
        }
      } else {
        // 离线模式
        if (isFavorite(id)) {
          await removeFromFavorites(id);
        } else {
          await addToFavorites(id);
        }
      }
    } catch (error) {
      console.error("切换收藏状态失败:", error);
      ElMessage.error("操作失败，请稍后重试");
    }
  };

  const clearFavorites = async () => {
    if (!(await checkAuthAndPrompt())) {
      return;
    }

    try {
      await ElMessageBox.confirm(
        "确定要清空所有收藏吗？此操作不可撤销。",
        "确认清空",
        {
          confirmButtonText: "确定",
          cancelButtonText: "取消",
          type: "warning",
        }
      );

      const userStore = useUserStore();

      if (userStore.isAuthenticated) {
        // 使用服务器API
        const response = await clearUserFavoritesApi();
        if (response.data && response.data.success) {
          favoriteIds.value = [];
          saveFavorites();
          ElMessage.success("已清空收藏");
        }
      } else {
        // 离线模式
        favoriteIds.value = [];
        saveFavorites();
        ElMessage.success("已清空收藏");
      }
    } catch (error) {
      if (error !== "cancel") {
        console.error("清空收藏失败:", error);
        ElMessage.error("清空收藏失败，请稍后重试");
      }
    }
  };

  // 同步收藏数据（用户登录后调用）
  const syncFavorites = async () => {
    const userStore = useUserStore();
    if (!userStore.isAuthenticated) {
      return;
    }

    try {
      await loadFromServer();
      console.log("收藏数据同步成功");
    } catch (error) {
      console.error("收藏数据同步失败:", error);
    }
  };

  // 获取收藏数量（从服务器）
  const refreshFavoriteCount = async () => {
    const userStore = useUserStore();
    if (!userStore.isAuthenticated) {
      return;
    }

    try {
      const response = await getUserFavoriteCount();
      if (response.data && response.data.success) {
        // 可以用于验证本地数据的准确性
        const serverCount = response.data.data;
        if (serverCount !== favoriteIds.value.length) {
          console.warn("本地收藏数量与服务器不一致，重新同步");
          await loadFromServer();
        }
      }
    } catch (error) {
      console.error("获取收藏数量失败:", error);
    }
  };

  // 初始化时加载收藏数据
  loadFavorites();

  return {
    favoriteIds,
    favoritesCount,
    loading,
    synced,
    loadFavorites,
    loadFromLocalStorage,
    loadFromServer,
    saveFavorites,
    clearLocalFavorites,
    isFavorite,
    addToFavorites,
    removeFromFavorites,
    toggleFavorite,
    clearFavorites,
    syncFavorites,
    refreshFavoriteCount,
    checkAuthAndPrompt,
  };
});
