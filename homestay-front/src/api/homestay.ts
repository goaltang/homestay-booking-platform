/**
 * @deprecated 此文件已被拆分，请使用新的模块化导入方式
 *
 * 旧用法:
 *   import { getHomestays, createHomestay } from '@/api/homestay'
 *
 * 新用法（推荐）:
 *   import { getHomestays, createHomestay } from '@/api/homestay'
 *   或按需导入:
 *   import { getHomestays } from '@/api/homestay/core'
 *   import { searchHomestays } from '@/api/homestay/search'
 */

// 为了向后兼容，重新导出所有 API
export * from "./homestay/index";

// 类型定义已移至 types/homestay.ts
// 请从那里导入类型
