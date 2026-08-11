import { ref } from 'vue'

export interface PaginationApiParams {
  page: number
  size: number
}

/**
 * 分页状态组合式函数
 *
 * 封装 el-pagination 的页码状态与后端 0-based 参数构建：
 * - pageIndex/pageSize：UI 状态（pageIndex 为 1-based）
 * - currentChange/sizeChange：直接绑定 el-pagination 事件
 * - reset：搜索/清空时重置回第 1 页
 * - buildParams：构建 API 参数（后端 page 为 0-based，返回 { page: pageIndex - 1, size }）
 *
 * @example
 * const { pageIndex, pageSize, currentChange, sizeChange, reset, buildParams } = usePagination(20)
 */
export function usePagination(initialSize = 20) {
  const pageIndex = ref(1)
  const pageSize = ref(initialSize)

  /** 页码变化（el-pagination @current-change） */
  const currentChange = (val: number) => {
    pageIndex.value = val
  }

  /** 每页条数变化（el-pagination @size-change）：重置回第 1 页 */
  const sizeChange = (val: number) => {
    pageSize.value = val
    pageIndex.value = 1
  }

  /** 重置页码为 1 */
  const reset = () => {
    pageIndex.value = 1
  }

  /** 构建后端 API 参数（page 0-based） */
  const buildParams = (): PaginationApiParams => ({
    page: pageIndex.value - 1,
    size: pageSize.value,
  })

  return {
    pageIndex,
    pageSize,
    currentChange,
    sizeChange,
    reset,
    buildParams,
  }
}

export default usePagination
