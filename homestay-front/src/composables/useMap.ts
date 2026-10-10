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
  const nearbyError = ref("");
  const showMapModal = ref(false);

  const searchNearbyFacilities = async (lat: number, lng: number) => {
    try {
      console.log("搜索周边设施...");
      const facilities = await searchNearbyPlaces(lat, lng, ["地铁站", "商场", "医院"]);
      nearbyPlaces.value = facilities;
      nearbyError.value = "";
      console.log("周边设施:", facilities);
    } catch (error) {
      console.error("搜索周边设施失败:", error);
      nearbyError.value = "周边设施加载失败，请稍后重试";
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
        console.warn("无法获取房源位置");
      }
    } catch (error) {
      console.error("初始化地图失败:", error);
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

  const onMapImageError = () => {
    mapData.value.hasLocation = false;
    mapData.value.staticMapUrl = "";
    showMapModal.value = false;
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
    nearbyError.value = "";
    showMapModal.value = false;
  };

  return {
    mapData,
    nearbyPlaces,
    nearbyError,
    showMapModal,
    initializeMap,
    openMapModal,
    onMapImageError,
    resetMap,
  };
}
