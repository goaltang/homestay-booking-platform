<template>
    <div class="favorites-container">
        <div class="favorites-header">
            <h1>我的收藏</h1>
        </div>

        <div v-if="loading" class="loading-container">
            <el-skeleton :rows="3" animated />
            <el-skeleton :rows="3" animated />
            <el-skeleton :rows="3" animated />
        </div>

        <div v-else-if="favorites.length === 0" class="empty-result">
            <el-empty description="您还没有收藏任何民宿">
                <el-button type="primary" @click="$router.push('/')">去浏览民宿</el-button>
            </el-empty>
        </div>

        <div v-else>
            <div class="favorites-toolbar">
                <el-button type="danger" plain size="small" @click="favoritesStore.clearFavorites">
                    <el-icon><Delete /></el-icon>
                    清空收藏
                </el-button>
            </div>

            <div class="homestay-grid">
                <HomestayCard v-for="homestay in favorites" :key="homestay.id" :homestay="homestay" :homestay-types="[]"
                    @card-click="viewHomestayDetails" />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Delete } from '@element-plus/icons-vue'
import { getUserFavorites } from '@/api/favorites'
import { useFavoritesStore } from '@/stores/favorites'
import HomestayCard from '@/components/homestay/HomestayCard.vue'

const router = useRouter()
const favoritesStore = useFavoritesStore()
const loading = ref(false)
const favorites = ref<any[]>([])

onMounted(async () => {
    await fetchFavorites()
})

// 当收藏ID列表变化时（如用户在卡片上取消收藏），自动过滤当前列表
watch(() => favoritesStore.favoriteIds, (ids) => {
    if (favorites.value.length > 0) {
        favorites.value = favorites.value.filter(h => ids.includes(h.id))
    }
}, { deep: true })

const fetchFavorites = async () => {
    loading.value = true
    try {
        const response = await getUserFavorites()
        if (response.data && response.data.success && Array.isArray(response.data.data)) {
            favorites.value = response.data.data
            favoritesStore.favoriteIds = response.data.data.map((h: any) => h.id)
        } else {
            favorites.value = []
        }
    } catch (error) {
        console.error('获取收藏民宿失败:', error)
        favorites.value = []
    } finally {
        loading.value = false
    }
}

const viewHomestayDetails = (homestay: any) => {
    router.push(`/homestays/${homestay.id}`)
}
</script>

<style scoped>
.favorites-container {
    max-width: 1200px;
    margin: 0 auto;
    padding: 20px;
}

.favorites-header {
    margin-bottom: 24px;
}

.favorites-toolbar {
    display: flex;
    justify-content: flex-end;
    margin-bottom: 16px;
}

.loading-container {
    padding: 24px;
}

.empty-result {
    padding: 48px 0;
    text-align: center;
}

.homestay-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 24px;
}



@media (max-width: 768px) {
    .homestay-grid {
        grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    }
}
</style>