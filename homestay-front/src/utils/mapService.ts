// 高德地图服务工具类
// 需要先申请高德地图API Key: https://console.amap.com/

// 导入地区数据转换工具
import { codeToText } from "element-china-area-data";
import { AMAP_CONFIG } from "@/utils/amapConfig";
import request from "./request";

// 共享高德地图API配置
// 如果没有API Key，使用模拟模式
const USE_MOCK_DATA = false; // 强制使用真实API，已有有效的API Key

// 周边设施类型
interface NearbyPlace {
  name: string;
  type: string;
  distance: number;
  address: string;
}

export interface AmapPoiSuggestion {
  id: string;
  name: string;
  address: string;
  district?: string;
  cityName?: string;
  provinceName?: string;
  latitude?: number;
  longitude?: number;
}

interface SearchAmapPoiSuggestionsOptions {
  city?: string;
  cityLimit?: boolean;
  limit?: number;
}

export const searchAmapPoiSuggestions = async (
  keyword: string,
  options?: SearchAmapPoiSuggestionsOptions
): Promise<AmapPoiSuggestion[]> => {
  const normalizedKeyword = keyword.trim();
  if (!normalizedKeyword) {
    return [];
  }

  if (USE_MOCK_DATA) {
    const mockSuggestions: AmapPoiSuggestion[] = [
      {
        id: "mock-1",
        name: "国贸中心",
        address: "北京市朝阳区建国门外大街1号",
        district: "朝阳区",
        cityName: "北京市",
        provinceName: "北京市",
        latitude: 39.914492,
        longitude: 116.459089,
      },
      {
        id: "mock-2",
        name: "前门大街",
        address: "北京市东城区前门大街",
        district: "东城区",
        cityName: "北京市",
        provinceName: "北京市",
        latitude: 39.899359,
        longitude: 116.404244,
      },
    ];

    return mockSuggestions
      .filter((item) => item.name.includes(normalizedKeyword))
      .slice(0, options?.limit ?? 8);
  }

  try {
    const response = await request.get("/api/map/poi-suggestions", {
      params: {
        keyword: normalizedKeyword,
        city: options?.city,
        limit: options?.limit ?? 10,
      },
    });
    return (response.data || []).map((item: any) => ({
      id: item.id || "",
      name: item.name || "",
      address: item.address || "",
      district: item.district || "",
      cityName: item.cityName || "",
      provinceName: item.provinceName || "",
      latitude: item.latitude,
      longitude: item.longitude,
    }));
  } catch (e) {
    console.error("[MapService] POI search failed:", e);
    return [];
  }
};

/**
 * 根据地址获取经纬度。
 * 兼容旧签名：支持 (provinceCode, cityCode, districtCode, addressDetail) 和 (address) 两种调用方式。
 */
export const geocodeAddress = async (
  addressOrProvince: string,
  cityCode?: string,
  districtCode?: string,
  addressDetail?: string
): Promise<{ lat: number; lng: number; formattedAddress: string } | null> => {
  let address: string;

  // 如果存在额外的行政区划参数，按旧签名处理，拼接完整地址
  if (cityCode || districtCode || addressDetail) {
    const addressParts: string[] = [];

    if (addressOrProvince && codeToText[addressOrProvince]) {
      addressParts.push(codeToText[addressOrProvince]);
    }

    if (cityCode && codeToText[cityCode]) {
      const cityName = codeToText[cityCode];
      if (!addressParts.includes(cityName)) {
        addressParts.push(cityName);
      }
    }

    if (districtCode && codeToText[districtCode]) {
      addressParts.push(codeToText[districtCode]);
    }

    if (addressDetail && addressDetail.trim()) {
      addressParts.push(addressDetail.trim());
    }

    address = addressParts.join("");
  } else {
    address = addressOrProvince.trim();
  }

  if (!address) {
    return null;
  }

  // 模拟模式：返回一些预设坐标
  if (USE_MOCK_DATA) {
    console.log("使用模拟地理编码数据");

    // 根据城市代码返回大概的坐标
    const mockLocations: Record<string, { lat: number; lng: number; name: string }> = {
      "1101": { lat: 39.9042, lng: 116.4074, name: "北京市" },
      "3101": { lat: 31.2304, lng: 121.4737, name: "上海市" },
      "4403": { lat: 22.5431, lng: 114.0579, name: "深圳市" },
      "4401": { lat: 23.1291, lng: 113.2644, name: "广州市" },
      "4602": { lat: 20.0444, lng: 110.1989, name: "三亚市" },
      "5101": { lat: 30.5728, lng: 104.0668, name: "成都市" },
      "3301": { lat: 30.2741, lng: 120.1551, name: "杭州市" },
    };

    const mockLocation = mockLocations[addressOrProvince] || mockLocations["1101"]; // 默认北京

    return {
      lat: mockLocation.lat + (Math.random() - 0.5) * 0.1, // 添加随机偏移
      lng: mockLocation.lng + (Math.random() - 0.5) * 0.1,
      formattedAddress: address,
    };
  }

  try {
    const response = await request.get("/api/map/geocode", {
      params: { address },
    });
    if (response.data && response.data.latitude != null && response.data.longitude != null) {
      return {
        lat: response.data.latitude,
        lng: response.data.longitude,
        formattedAddress: address,
      };
    }
    return null;
  } catch (e) {
    console.error("[MapService] Geocode failed:", e);
    return null;
  }
};

/**
 * 生成静态地图图片URL
 */
export const generateStaticMapUrl = (
  lat: number,
  lng: number,
  width: number = 800,
  height: number = 400,
  zoom: number = 15
): string => {
  // 模拟模式：返回占位图
  if (USE_MOCK_DATA) {
    console.log("使用模拟静态地图");
    return `https://picsum.photos/${width}/${height}?random=map-${Math.floor(
      Math.random() * 1000
    )}`;
  }

  // 高德地图静态图API
  // 文档: https://lbs.amap.com/api/webservice/guide/api/staticmaps

  const markers = `${lng},${lat}`; // 经度,纬度

  const url =
    `https://restapi.amap.com/v3/staticmap?` +
    `location=${lng},${lat}&` +
    `zoom=${zoom}&` +
    `size=${width}*${height}&` +
    `markers=mid,0xFF0000,A:${markers}&` +
    `key=${AMAP_CONFIG.webServiceKey}`;

  console.log("生成的静态地图URL:", url);
  return url;
};

/**
 * 为保护隐私，给坐标添加随机偏移
 */
export const addPrivacyOffset = (lat: number, lng: number): { lat: number; lng: number } => {
  // 添加小范围随机偏移（约100-500米）
  const offsetRange = 0.005; // 大约500米的偏移范围
  const latOffset = (Math.random() - 0.5) * offsetRange;
  const lngOffset = (Math.random() - 0.5) * offsetRange;

  return {
    lat: lat + latOffset,
    lng: lng + lngOffset,
  };
};

/**
 * 搜索周边设施
 */
export const searchNearbyPlaces = async (
  lat: number,
  lng: number,
  types: string[] = ["地铁站", "商场", "医院", "学校"]
): Promise<NearbyPlace[]> => {
  // 模拟模式：返回模拟数据
  if (USE_MOCK_DATA) {
    console.log("使用模拟周边设施数据");

    const mockPlaces: NearbyPlace[] = [
      {
        name: "地铁1号线某某站",
        type: "地铁站",
        distance: 450,
        address: "步行约5分钟",
      },
      {
        name: "某某购物中心",
        type: "商场",
        distance: 750,
        address: "步行约8分钟",
      },
      {
        name: "某某三甲医院",
        type: "医院",
        distance: 1200,
        address: "步行约12分钟",
      },
      { name: "某某小学", type: "学校", distance: 600, address: "步行约6分钟" },
    ];

    // 根据传入的types过滤
    return mockPlaces.filter((place) => types.includes(place.type)).slice(0, 3);
  }

  try {
    const places: NearbyPlace[] = [];

    for (const type of types) {
      const response = await fetch(
        `https://restapi.amap.com/v3/place/around?` +
          `key=${AMAP_CONFIG.webServiceKey}&` +
          `location=${lng},${lat}&` +
          `keywords=${encodeURIComponent(type)}&` +
          `radius=2000&` +
          `types=&` +
          `sortrule=distance&` +
          `output=JSON`
      );

      const data = await response.json();

      if (data.status === "1" && data.pois && data.pois.length > 0) {
        // 取最近的一个
        const poi = data.pois[0];
        places.push({
          name: poi.name,
          type: type,
          distance: parseInt(poi.distance),
          address: poi.address,
        });
      }
    }

    return places;
  } catch (error) {
    console.error("搜索周边设施失败:", error);
    // 返回备用模拟数据
    return [
      {
        name: "附近地铁站",
        type: "地铁站",
        distance: 500,
        address: "步行约5分钟",
      },
      { name: "附近商场", type: "商场", distance: 800, address: "步行约8分钟" },
      {
        name: "附近医院",
        type: "医院",
        distance: 1200,
        address: "步行约12分钟",
      },
    ];
  }
};

/**
 * 计算距离（米）
 */
export const calculateDistance = (
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number
): number => {
  const R = 6371000; // 地球半径（米）
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLng / 2) *
      Math.sin(dLng / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
};

/**
 * 格式化距离显示
 */
export const formatDistance = (distance: number): string => {
  if (distance < 1000) {
    return `${Math.round(distance)}米`;
  } else {
    return `${(distance / 1000).toFixed(1)}公里`;
  }
};

// 预设的周边设施类型
export const FACILITY_TYPES = {
  TRANSPORT: { key: "地铁站|公交站", name: "交通" },
  SHOPPING: { key: "商场|超市", name: "购物" },
  MEDICAL: { key: "医院|药店", name: "医疗" },
  EDUCATION: { key: "学校|幼儿园", name: "教育" },
  FOOD: { key: "餐厅|美食", name: "餐饮" },
  ENTERTAINMENT: { key: "电影院|KTV", name: "娱乐" },
};
