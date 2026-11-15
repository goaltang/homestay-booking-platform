/**
 * 高德地图 JSAPI 加载单例
 *
 * 确保 AMapLoader.load() 只被调用一次，多个消费方（useMapSearch、mapService 等）
 * 共享同一个加载 Promise。插件列表在此集中管理。
 */

import AMapLoader from "@amap/amap-jsapi-loader";
import { AMAP_CONFIG, applyAmapSecurityConfig } from "@/utils/amapConfig";

// eslint-disable-next-line @typescript-eslint/no-explicit-any
let loadPromise: Promise<any> | null = null;

/**
 * 确保高德 JSAPI 已加载并返回全局 AMap 对象。
 *
 * - 首次调用时触发 `AMapLoader.load()`，后续调用复用同一 Promise；加载失败后下次调用会重新加载。
 * - 安全配置（`_AMapSecurityConfig`）在首次加载前自动注入。
 *
 * @returns 全局 AMap 命名空间对象
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const ensureAMapLoaded = (): Promise<any> => {
  if (loadPromise) {
    return loadPromise;
  }

  applyAmapSecurityConfig();

  loadPromise = AMapLoader.load({
    key: AMAP_CONFIG.apiKey,
    version: AMAP_CONFIG.version,
    plugins: AMAP_CONFIG.plugins,
  }).catch((error: unknown) => {
    // 失败不缓存，否则“重新加载地图”永远拿到同一个失败结果
    loadPromise = null;
    throw error;
  });

  return loadPromise;
};
