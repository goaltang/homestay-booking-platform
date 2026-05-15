import { defineStore } from 'pinia'
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

// 搜索错误类型
export interface SearchError {
  type: 'network' | 'timeout' | 'server' | 'unknown'
  message: string
  code?: string | number
}

// 搜索参数接口
export interface SearchState {
  keyword: string
  selectedRegion: string[]
  checkIn: string | null
  checkOut: string | null
  guestCount: number
  propertyType: string | null
  minPrice: number | null
  maxPrice: number | null
  amenities: string[]
  minRating: number | null
  sortBy: string
  sortDirection: string
  groupId: number | null
}

// 默认搜索参数
const defaultSearchState: SearchState = {
  keyword: '',
  selectedRegion: [],
  checkIn: null,
  checkOut: null,
  guestCount: 1,
  propertyType: null,
  minPrice: null,
  maxPrice: null,
  amenities: [],
  minRating: null,
  sortBy: 'id',
  sortDirection: 'desc',
  groupId: null
}

// 搜索超时时间（毫秒）
const SEARCH_TIMEOUT = 15000
const DEFAULT_PAGE_SIZE = 12

const getQueryValue = (value: unknown): string | undefined => {
  if (Array.isArray(value)) return value[0] ? String(value[0]) : undefined
  return value !== undefined && value !== null ? String(value) : undefined
}

const getQueryNumber = (value: unknown, fallback: number): number => {
  const parsed = Number(getQueryValue(value))
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

export const useSearchStore = defineStore('search', () => {
  const router = useRouter()
  const route = useRoute()

  // 搜索参数状态
  const searchParams = reactive<SearchState>({ ...defaultSearchState })

  // 分页状态
  const currentPage = ref(1)
  const pageSize = ref(DEFAULT_PAGE_SIZE)
  const total = ref(0)

  // 搜索结果
  const searchResults = ref<any[]>([])
  const loading = ref(false)

  // 错误状态
  const searchError = ref<SearchError | null>(null)
  const hasError = computed(() => searchError.value !== null)

  // 计算属性：是否有搜索条件（与 HomestayListView 的 hasSearchConditions 逻辑对齐）
  const hasSearchConditions = () => {
    return (
      searchParams.keyword.trim() !== '' ||
      searchParams.selectedRegion.length > 0 ||
      searchParams.checkIn !== null ||
      searchParams.checkOut !== null ||
      searchParams.guestCount > 1 ||
      searchParams.propertyType !== null ||
      searchParams.minPrice !== null ||
      searchParams.maxPrice !== null ||
      searchParams.amenities.length > 0 ||
      searchParams.minRating !== null ||
      searchParams.groupId !== null
    )
  }

  // 计算属性：是否为搜索模式
  const isSearchMode = computed(() => hasSearchConditions())

  // 计算属性：是否有活跃搜索
  const hasActiveSearch = computed(() => isSearchMode.value)

  // 清除错误
  const clearError = () => {
    searchError.value = null
  }

  // 设置错误
  const setError = (error: SearchError | null) => {
    searchError.value = error
  }

  // 解析错误类型
  const parseError = (error: unknown): SearchError => {
    const err = error as Record<string, unknown>

    // 网络错误（请求未发出或连接失败）
    if (!err.response) {
      const msg = typeof err.message === 'string' ? err.message : ''
      const code = typeof err.code === 'string' ? err.code : ''
      if (code === 'ECONNABORTED' || msg.includes('timeout')) {
        return {
          type: 'timeout',
          message: '搜索请求超时，请稍后重试'
        }
      }
      return {
        type: 'network',
        message: '网络连接失败，请检查网络后重试'
      }
    }

    const response = err.response as Record<string, unknown>

    // 服务器错误（返回了错误状态码）
    const status = typeof response.status === 'number' ? response.status : 0
    if (status >= 500) {
      return {
        type: 'server',
        message: '服务器繁忙，请稍后重试',
        code: status
      }
    }

    if (status === 429) {
      return {
        type: 'server',
        message: '请求过于频繁，请稍后再试',
        code: status
      }
    }

    // 客户端错误
    if (status >= 400) {
      const data = response.data as Record<string, unknown> | undefined
      const errorMsg = typeof data?.message === 'string' ? data.message : '搜索失败'
      return {
        type: 'unknown',
        message: errorMsg,
        code: status
      }
    }

    // 未知错误
    return {
      type: 'unknown',
      message: typeof err.message === 'string' ? err.message : '搜索失败，请稍后重试'
    }
  }

  // 从 URL 同步参数
  const syncFromUrl = () => {
    if (!route.query) return

    searchParams.keyword = getQueryValue(route.query.keyword) || ''

    if (route.query.region) {
      searchParams.selectedRegion = (getQueryValue(route.query.region) || '')
        .split(',')
        .filter(Boolean)
    } else {
      searchParams.selectedRegion = []
    }

    searchParams.checkIn = getQueryValue(route.query.checkIn) || null
    searchParams.checkOut = getQueryValue(route.query.checkOut) || null
    searchParams.guestCount = getQueryNumber(route.query.guestCount, 1)

    const queryType = getQueryValue(route.query.type)
    searchParams.propertyType = getQueryValue(route.query.search) === 'true' && queryType && queryType !== 'popular'
      ? queryType
      : null

    searchParams.minPrice = route.query.minPrice ? Number(getQueryValue(route.query.minPrice)) : null
    searchParams.maxPrice = route.query.maxPrice ? Number(getQueryValue(route.query.maxPrice)) : null

    if (route.query.amenities) {
      searchParams.amenities = (getQueryValue(route.query.amenities) || '')
        .split(',')
        .filter(Boolean)
    } else {
      searchParams.amenities = []
    }

    searchParams.minRating = route.query.minRating ? Number(getQueryValue(route.query.minRating)) : null
    searchParams.groupId = route.query.groupId ? Number(getQueryValue(route.query.groupId)) : null
    searchParams.sortBy = getQueryValue(route.query.sortBy) || defaultSearchState.sortBy
    searchParams.sortDirection = getQueryValue(route.query.sortDirection) || defaultSearchState.sortDirection

    currentPage.value = getQueryNumber(route.query.page, 1)
    pageSize.value = getQueryNumber(route.query.size, DEFAULT_PAGE_SIZE)
  }

  // 同步参数到 URL
  const syncToUrl = (options: { includePage?: boolean } = {}) => {
    const query: Record<string, string | undefined> = {
      keyword: searchParams.keyword.trim() || undefined,
      region: searchParams.selectedRegion.length > 0
        ? searchParams.selectedRegion.join(',')
        : undefined,
      checkIn: searchParams.checkIn || undefined,
      checkOut: searchParams.checkOut || undefined,
      guestCount: searchParams.guestCount > 1
        ? String(searchParams.guestCount)
        : undefined,
      type: searchParams.propertyType || undefined,
      minPrice: searchParams.minPrice ? String(searchParams.minPrice) : undefined,
      maxPrice: searchParams.maxPrice ? String(searchParams.maxPrice) : undefined,
      amenities: searchParams.amenities.length > 0
        ? searchParams.amenities.join(',')
        : undefined,
      minRating: searchParams.minRating ? String(searchParams.minRating) : undefined,
      groupId: searchParams.groupId ? String(searchParams.groupId) : undefined,
      sortBy: searchParams.sortBy !== defaultSearchState.sortBy ? searchParams.sortBy : undefined,
      sortDirection: searchParams.sortDirection !== defaultSearchState.sortDirection
        ? searchParams.sortDirection
        : undefined
    }

    if (options.includePage) {
      query.page = currentPage.value > 1 ? String(currentPage.value) : undefined
      query.size = pageSize.value !== DEFAULT_PAGE_SIZE ? String(pageSize.value) : undefined
    }

    // 如果是搜索模式，添加 search 标记
    if (isSearchMode.value) {
      query.search = 'true'
    }

    router.replace({ path: '/homestays', query })
  }

  // 更新单个参数
  const updateParam = <K extends keyof SearchState>(key: K, value: SearchState[K]) => {
    searchParams[key] = value
  }

  // 批量更新参数
  const updateParams = (updates: Partial<SearchState>) => {
    Object.assign(searchParams, updates)
  }

  // 更新分页
  const updatePagination = (page?: number, size?: number) => {
    if (page !== undefined) currentPage.value = page
    if (size !== undefined) pageSize.value = size
  }

  // 设置结果
  const setResults = (results: any[], totalCount: number) => {
    searchResults.value = results
    total.value = totalCount
  }

  // 设置加载状态
  const setLoading = (loadingState: boolean) => {
    loading.value = loadingState
  }

  // 重置搜索参数
  const resetSearch = () => {
    Object.assign(searchParams, defaultSearchState)
    currentPage.value = 1
    total.value = 0
    searchResults.value = []
    searchError.value = null
  }

  // 设置完整搜索状态（用于同步组件状态）
  const setSearchState = (state: Partial<SearchState>) => {
    Object.assign(searchParams, state)
  }

  // 初始化：从 URL 恢复状态
  const initFromRoute = () => {
    syncFromUrl()
  }

  return {
    // 状态
    searchParams,
    currentPage,
    pageSize,
    total,
    searchResults,
    loading,
    searchError,
    hasError,

    // 计算属性
    isSearchMode,
    hasActiveSearch,

    // 方法
    hasSearchConditions,

    // 方法
    clearError,
    setError,
    parseError,
    syncFromUrl,
    syncToUrl,
    updateParam,
    updateParams,
    updatePagination,
    setResults,
    setLoading,
    resetSearch,
    setSearchState,
    initFromRoute,

    // 常量
    SEARCH_TIMEOUT
  }
})
