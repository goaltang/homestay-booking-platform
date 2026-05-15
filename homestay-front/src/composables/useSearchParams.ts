import { reactive, watch, computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'

export interface SearchParams {
  keyword: string
  selectedRegion: string[]
  checkIn: string | null
  checkOut: string | null
  guestCount: number
  propertyType?: string | null
  minPrice?: number | null
  maxPrice?: number | null
  amenities?: string[]
  minRating?: number | null
}

interface UseSearchParamsOptions {
  /**
   * Route path for search page, used to build shareable URLs
   */
  searchPath?: string
  /**
   * Whether to sync to URL automatically (default: true)
   */
  autoSync?: boolean
}

/**
 * Composable for managing search parameters with URL persistence.
 * Provides bidirectional sync between searchParams state and URL query params.
 */
export function useSearchParams(options: UseSearchParamsOptions = {}) {
  const router = useRouter()
  const route = useRoute()

  const { searchPath = '/homestays', autoSync = true } = options

  // Initialize search params from URL or defaults
  const searchParams = reactive<SearchParams>({
    keyword: (route.query.keyword as string) || '',
    selectedRegion: route.query.region
      ? (route.query.region as string).split(',')
      : [],
    checkIn: (route.query.checkIn as string) || null,
    checkOut: (route.query.checkOut as string) || null,
    guestCount: route.query.guestCount
      ? Number(route.query.guestCount)
      : 1,
    propertyType: (route.query.type as string) || null,
    minPrice: route.query.minPrice ? Number(route.query.minPrice) : null,
    maxPrice: route.query.maxPrice ? Number(route.query.maxPrice) : null,
    amenities: route.query.amenities
      ? (route.query.amenities as string).split(',')
      : [],
    minRating: route.query.minRating
      ? Number(route.query.minRating)
      : null
  })

  // Build query object from search params
  const buildQuery = (): Record<string, string | undefined> => {
    return {
      keyword: searchParams.keyword || undefined,
      region:
        searchParams.selectedRegion.length > 0
          ? searchParams.selectedRegion.join(',')
          : undefined,
      checkIn: searchParams.checkIn || undefined,
      checkOut: searchParams.checkOut || undefined,
      guestCount:
        searchParams.guestCount > 1
          ? String(searchParams.guestCount)
          : undefined,
      type: searchParams.propertyType || undefined,
      minPrice: searchParams.minPrice ? String(searchParams.minPrice) : undefined,
      maxPrice: searchParams.maxPrice ? String(searchParams.maxPrice) : undefined,
      amenities:
        searchParams.amenities && searchParams.amenities.length > 0
          ? searchParams.amenities.join(',')
          : undefined,
      minRating: searchParams.minRating
        ? String(searchParams.minRating)
        : undefined,
      search: 'true'
    }
  }

  // Sync search params to URL
  const syncToUrl = () => {
    if (!autoSync) return

    const query = buildQuery()

    router.replace({
      path: searchPath,
      query
    })
  }

  // Watch for changes and sync to URL
  const isInternalUpdate = ref(false)
  watch(
    () => ({ ...searchParams }),
    () => {
      if (isInternalUpdate.value) return
      syncToUrl()
    },
    { deep: true }
  )

  // Load params from URL on mount
  const loadFromUrl = () => {
    isInternalUpdate.value = true

    searchParams.keyword = (route.query.keyword as string) || ''
    searchParams.selectedRegion = route.query.region
      ? (route.query.region as string).split(',')
      : []
    searchParams.checkIn = (route.query.checkIn as string) || null
    searchParams.checkOut = (route.query.checkOut as string) || null
    searchParams.guestCount = route.query.guestCount
      ? Number(route.query.guestCount)
      : 1
    searchParams.propertyType = (route.query.type as string) || null
    searchParams.minPrice = route.query.minPrice
      ? Number(route.query.minPrice)
      : null
    searchParams.maxPrice = route.query.maxPrice
      ? Number(route.query.maxPrice)
      : null
    searchParams.amenities = route.query.amenities
      ? (route.query.amenities as string).split(',')
      : []
    searchParams.minRating = route.query.minRating
      ? Number(route.query.minRating)
      : null

    isInternalUpdate.value = false
  }

  // Update single param and sync
  const updateParam = <K extends keyof SearchParams>(
    key: K,
    value: SearchParams[K]
  ) => {
    isInternalUpdate.value = true
    searchParams[key] = value
    isInternalUpdate.value = false
    syncToUrl()
  }

  // Update multiple params at once
  const updateParams = (updates: Partial<SearchParams>) => {
    isInternalUpdate.value = true
    Object.assign(searchParams, updates)
    isInternalUpdate.value = false
    syncToUrl()
  }

  // Reset all params to defaults
  const resetParams = () => {
    isInternalUpdate.value = true
    searchParams.keyword = ''
    searchParams.selectedRegion = []
    searchParams.checkIn = null
    searchParams.checkOut = null
    searchParams.guestCount = 1
    searchParams.propertyType = null
    searchParams.minPrice = null
    searchParams.maxPrice = null
    searchParams.amenities = []
    searchParams.minRating = null
    isInternalUpdate.value = false
    syncToUrl()
  }

  // Check if any search params are set (excluding defaults)
  const hasActiveSearch = computed(() => {
    return (
      searchParams.keyword !== '' ||
      searchParams.selectedRegion.length > 0 ||
      searchParams.checkIn !== null ||
      searchParams.checkOut !== null ||
      searchParams.guestCount > 1 ||
      searchParams.propertyType !== null ||
      searchParams.minPrice !== null ||
      searchParams.maxPrice !== null ||
      (searchParams.amenities && searchParams.amenities.length > 0) ||
      searchParams.minRating !== null
    )
  })

  // Watch route query changes to sync from URL
  watch(
    () => route.query,
    (newQuery) => {
      if (Object.keys(newQuery).length === 0) return

      isInternalUpdate.value = true

      if (newQuery.keyword !== undefined) {
        searchParams.keyword = newQuery.keyword as string
      }
      if (newQuery.region !== undefined) {
        searchParams.selectedRegion = (newQuery.region as string).split(',')
      } else {
        searchParams.selectedRegion = []
      }
      if (newQuery.checkIn !== undefined) {
        searchParams.checkIn = newQuery.checkIn as string
      } else {
        searchParams.checkIn = null
      }
      if (newQuery.checkOut !== undefined) {
        searchParams.checkOut = newQuery.checkOut as string
      } else {
        searchParams.checkOut = null
      }
      if (newQuery.guestCount !== undefined) {
        searchParams.guestCount = Number(newQuery.guestCount)
      } else {
        searchParams.guestCount = 1
      }

      isInternalUpdate.value = false
    },
    { deep: true }
  )

  return {
    searchParams,
    hasActiveSearch,
    updateParam,
    updateParams,
    resetParams,
    loadFromUrl,
    syncToUrl,
    buildQuery
  }
}
