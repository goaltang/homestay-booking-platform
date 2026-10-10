import { ref } from "vue";
import {
  geocodeAddress,
  generateStaticMapUrl,
  addPrivacyOffset,
  searchNearbyPlaces,
} from "@/utils/mapService";
import type { HomestayDetail, MapData, NearbyPlace } from "@/types/homestay";

export function useMap() {
  const mapData = ref<MapData>({
    lat: 0,
    lng: 0,
    staticMapUrl: "",
    isLoading: false,
    hasLocation: false,
  });
  const nearbyPlaces = ref<NearbyPlace[]>([]);
  const showMapModal = ref(false);

  const setDefaultLocation = async (cityCode?: string) => {
    const defaultLocations: Record<string, { lat: number; lng: number; name: string }> = {
      "1101": { lat: 39.9042, lng: 116.4074, name: "北京市" },
      "3101": { lat: 31.2304, lng: 121.4737, name: "上海市" },
      "4403": { lat: 22.5431, lng: 114.0579, name: "深圳市" },
      "4401": { lat: 23.1291, lng: 113.2644, name: "广州市" },
      "4602": { lat: 20.0444, lng: 110.1989, name: "三亚市" },
    };

    const defaultLocation = cityCode ? defaultLocations[cityCode] : null;

    if (defaultLocation) {
      console.log("使用默认城市位置:", defaultLocation.name);
      mapData.value.lat = defaultLocation.lat;
      mapData.value.lng = defaultLocation.lng;
      mapData.value.hasLocation = true;

      const offsetLocation = addPrivacyOffset(defaultLocation.lat, defaultLocation.lng);
      mapData.value.staticMapUrl = generateStaticMapUrl(
        offsetLocation.lat,
        offsetLocation.lng,
        800,
        400,
        12
      );
    } else {
      console.log("使用北京作为默认位置");
      mapData.value.lat = 39.9042;
      mapData.value.lng = 116.4074;
      mapData.value.hasLocation = true;

      // 使用真实的高德地图静态图
      mapData.value.staticMapUrl = generateStaticMapUrl(39.9042, 116.4074, 800, 400, 12);
    }
  };

  const searchNearbyFacilities = async (lat: number, lng: number) => {
    try {
      console.log("搜索周边设施...");
      const facilities = await searchNearbyPlaces(lat, lng, ["地铁站", "商场", "医院"]);
      nearbyPlaces.value = facilities;
      console.log("周边设施:", facilities);
    } catch (error) {
      console.error("搜索周边设施失败:", error);
      nearbyPlaces.value = [
        { name: "地铁站", type: "交通", distance: 500, address: "步行约5分钟" },
        {
          name: "购物中心",
          type: "购物",
          distance: 800,
          address: "步行约8分钟",
        },
        { name: "医院", type: "医疗", distance: 1200, address: "步行约12分钟" },
      ];
    }
  };

  const initializeMap = async (homestay: HomestayDetail | null) => {
    if (!homestay) return;

    console.log("=== 初始化地图 ===");
    mapData.value.isLoading = true;

    try {
      let geocodeResult = null;
      if (homestay.provinceCode && homestay.cityCode) {
        console.log("开始地理编码...");
        geocodeResult = await geocodeAddress(
          homestay.provinceCode,
          homestay.cityCode,
          homestay.districtCode || "",
          homestay.addressDetail
        );
      } else {
        console.warn("缺少省市区代码，无法进行精确地理编码");
      }

      if (geocodeResult) {
        console.log("地理编码成功，原始坐标:", geocodeResult);

        const offsetLocation = addPrivacyOffset(geocodeResult.lat, geocodeResult.lng);
        console.log("添加隐私偏移后的坐标:", offsetLocation);

        mapData.value.lat = offsetLocation.lat;
        mapData.value.lng = offsetLocation.lng;
        mapData.value.hasLocation = true;

        const generatedMapUrl = generateStaticMapUrl(
          offsetLocation.lat,
          offsetLocation.lng,
          800,
          400,
          15
        );

        mapData.value.staticMapUrl = generatedMapUrl;

        console.log("生成的静态地图URL:", generatedMapUrl);
        console.log("mapData.value.staticMapUrl 设置后:", mapData.value.staticMapUrl);
        console.log("mapData.value.hasLocation 设置后:", mapData.value.hasLocation);
        await searchNearbyFacilities(offsetLocation.lat, offsetLocation.lng);
      } else {
        console.warn("地理编码失败，使用默认位置");
        await setDefaultLocation(homestay.cityCode);
      }
    } catch (error) {
      console.error("初始化地图失败:", error);
      await setDefaultLocation(homestay.cityCode);
    } finally {
      mapData.value.isLoading = false;
    }
  };

  const openMapModal = () => {
    if (mapData.value.hasLocation) {
      showMapModal.value = true;
      console.log("打开交互式地图功能待实现");
    } else {
      console.warn("地图数据加载中，请稍后再试");
    }
  };

  const onMapImageError = (event: Event) => {
    console.warn("地图图片加载失败，使用fallback图片");
    const img = event.target as HTMLImageElement;
    // 使用一个简单的占位图，而不是随机图片
    img.src =
      "data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODAwIiBoZWlnaHQ9IjQwMCIgdmlld0JveD0iMCAwIDgwMCA0MDAiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CjxyZWN0IHdpZHRoPSI4MDAiIGhlaWdodD0iNDAwIiBmaWxsPSIjRjVGNUY1Ii8+Cjx0ZXh0IHg9IjQwMCIgeT0iMjAwIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmaWxsPSIjOTk5IiBmb250LXNpemU9IjI0IiBmb250LWZhbWlseT0iQXJpYWwiPuWcsOWbvuWKoOi9veS4rTwvdGV4dD4KPHN2Zz4=";
  };

  const resetMap = () => {
    mapData.value = {
      lat: 0,
      lng: 0,
      staticMapUrl: "",
      isLoading: false,
      hasLocation: false,
    };
    nearbyPlaces.value = [];
    showMapModal.value = false;
  };

  return {
    mapData,
    nearbyPlaces,
    showMapModal,
    initializeMap,
    openMapModal,
    onMapImageError,
    resetMap,
  };
}
