import request from "@/utils/request";
// Assuming AmenityDTO type needs to be defined or imported correctly
// import type { AmenityDTO } from "@/types";

// Temporary type definition if not available in @/types
interface AmenityDTO {
  value: string;
  label: string;
  description?: string;
  icon?: string;
  active?: boolean;
  usageCount?: number;
  categoryCode?: string;
  categoryName?: string;
  categoryIcon?: string;
}

interface AmenityApiResponse {
  success: boolean;
  data: {
    content: AmenityDTO[];
    totalElements: number;
    // other pagination fields if needed
  };
  message?: string;
}

/**
 * 获取所有可用的设施列表 (用于选择器)
 * @param onlyActive 是否只获取激活状态的设施，默认为 true
 */
export function getAllAvailableAmenities(onlyActive: boolean = true) {
  return request<AmenityApiResponse>({
    url: "/api/amenities", // Use the correct API base path
    method: "get",
    params: {
      onlyActive,
      page: 0, // Start from page 0
      size: 1000, // Request a large size to get all items
    },
  }).then((response) => {
    // Extract the list from the nested structure
    if (response && response.success && response.data && Array.isArray(response.data.content)) {
      return response.data.content; // Return only the array of amenities
    } else {
      console.error("Failed to fetch amenities or invalid response structure:", response);
      return []; // Return empty array on failure
    }
  });
}

// You can add other amenity-related API functions here later (create, update, delete, categories etc.)
