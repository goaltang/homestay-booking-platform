/**
 * 房源相关 API 统一导出
 *
 * 按功能模块拆分：
 * - core: 基础的 CRUD 操作
 * - search: 搜索功能
 * - audit: 审核相关
 * - image: 图片管理
 * - batch: 批量操作
 * - meta: 类型、位置等元数据
 */

export * from "./core";
export * from "./search";
export * from "./audit";
export * from "./image";
export * from "./batch";
export * from "./meta";
export * from "./group";
export type {
  AmenityCategoryOption,
  HomestaySearchRequest,
  HomestayType,
  PropertyTypeInfo,
} from "../../types/homestay";

// 向后兼容的别名
export { getOwnerHomestays as getHomestaysByOwner } from "./core";
