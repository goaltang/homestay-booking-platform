/**
 * 地图搜索相关的纯工具函数
 * 从 useMapSearch.ts 中抽取，无状态依赖，便于独立测试与复用
 */

const DEFAULT_CITY_CENTERS: Record<string, { lat: number; lng: number }> = {
  '1101': { lat: 39.9042, lng: 116.4074 }, // 北京
  '3101': { lat: 31.2304, lng: 121.4737 }, // 上海
  '4403': { lat: 22.5431, lng: 114.0579 }, // 深圳
  '4401': { lat: 23.1291, lng: 113.2644 }, // 广州
  '4602': { lat: 20.0444, lng: 110.1989 }, // 三亚
  '5101': { lat: 30.5728, lng: 104.0668 }, // 成都
  '3301': { lat: 30.2741, lng: 120.1551 }, // 杭州
  '3201': { lat: 32.0603, lng: 118.7969 }, // 南京
  '5001': { lat: 29.5630, lng: 106.5516 }, // 重庆
  '4406': { lat: 22.5311, lng: 113.1248 }, // 珠海
  '6101': { lat: 34.3416, lng: 108.9398 }, // 西安
  '4201': { lat: 30.5928, lng: 114.3055 }, // 武汉
  '3205': { lat: 31.2989, lng: 120.5853 }, // 苏州
  '1201': { lat: 39.0842, lng: 117.2009 }, // 天津
  '3502': { lat: 24.4798, lng: 118.0894 }, // 厦门
  '3702': { lat: 36.0671, lng: 120.3826 }, // 青岛
  '4301': { lat: 28.2282, lng: 112.9388 }, // 长沙
  '5301': { lat: 25.0389, lng: 102.7183 }, // 昆明
  '2102': { lat: 38.9140, lng: 121.6147 }, // 大连
  '3302': { lat: 29.8683, lng: 121.5440 }, // 宁波
};

/** 根据城市码获取默认中心坐标 */
export const getCityCenter = (cityCode?: string) => {
  if (!cityCode) return undefined;

  return DEFAULT_CITY_CENTERS[cityCode]
    ?? (
      cityCode.length === 6 && cityCode.endsWith('00')
        ? DEFAULT_CITY_CENTERS[cityCode.slice(0, 4)]
        : undefined
    );
};

/** 安全地将未知值转换为数字，无法转换时返回 undefined */
export const toOptionalNumber = (value: unknown): number | undefined => {
  if (value === null || value === undefined || value === '') {
    return undefined;
  }

  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
};

/** 校验图片 URL 是否可信（仅允许 http/https 且域名可信） */
export const isValidImageUrl = (url: string): boolean => {
  if (!url) return false;
  try {
    const parsed = new URL(url);
    if (!['http:', 'https:'].includes(parsed.protocol)) return false;
    const trustedHosts = new Set(['localhost', '127.0.0.1', window.location.hostname]);
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
    if (apiBaseUrl) {
      try {
        trustedHosts.add(new URL(apiBaseUrl, window.location.origin).hostname);
      } catch {
        // ignore invalid configured base url
      }
    }

    if (!trustedHosts.has(parsed.hostname) && !url.startsWith('https://picsum.photos')) {
      return false;
    }
    return true;
  } catch {
    // 相对路径认为是安全的（会被加上 base URL）
    return true;
  }
};

/** 将图片路径补全为可访问的完整 URL */
export const getImageUrl = (imageUrl: string): string => {
  if (!imageUrl) return 'https://picsum.photos/300/200';
  if (imageUrl.startsWith('http')) return imageUrl;
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';
  return `${baseUrl}${imageUrl.startsWith('/') ? '' : '/'}${imageUrl}`;
};
