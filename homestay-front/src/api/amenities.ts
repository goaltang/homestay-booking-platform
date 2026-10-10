import request from "../utils/request";
/**
 * 获取所有可用的设施和服务列表
 */
export const getAmenitiesApi = (params?: any) => {
  return request({
    url: "/api/amenities",
    method: "get",
    params,
  });
};

/**
 * 获取所有设施列表(按分类)
 */
export const getAmenitiesByCategoryApi = () => {
  return request({
    url: "/api/amenities/by-categories",
    method: "get",
  });
};

export const getAllAmenitiesByCategories = getAmenitiesByCategoryApi;

/**
 * 一键添加所有设施到房源
 * @param homestayId 房源ID
 * @param categoryCode 可选的分类编码
 */
export const addAllAmenitiesToHomestayApi = (homestayId: number, categoryCode?: string) => {
  let url = `/api/amenities/add-all-to-homestay/${homestayId}`;
  if (categoryCode) {
    url += `?categoryCode=${categoryCode}`;
  }
  return request({
    url,
    method: "post",
  });
};

/**
 * 从房源移除所有设施
 * @param homestayId 房源ID
 * @param categoryCode 可选的分类编码
 */
export const removeAllAmenitiesFromHomestayApi = (homestayId: number, categoryCode?: string) => {
  let url = `/api/amenities/remove-all-from-homestay/${homestayId}`;
  if (categoryCode) {
    url += `?categoryCode=${categoryCode}`;
  }
  return request({
    url,
    method: "delete",
  });
};

/**
 * 添加单个设施到房源
 * @param homestayId 房源ID
 * @param amenityValue 设施编码
 */
export const addAmenityToHomestayApi = (homestayId: number, amenityValue: string) => {
  return request({
    url: `/api/amenities/add-to-homestay/${homestayId}/${amenityValue}`,
    method: "post",
  });
};

/**
 * 从房源移除单个设施
 * @param homestayId 房源ID
 * @param amenityValue 设施编码
 */
export const removeAmenityFromHomestayApi = (homestayId: number, amenityValue: string) => {
  return request({
    url: `/api/amenities/remove-from-homestay/${homestayId}/${amenityValue}`,
    method: "delete",
  });
};

// 创建设施
export const createAmenityApi = (data: any) => {
  return request({
    url: "/api/amenities",
    method: "post",
    data,
  });
};

// 更新设施
export const updateAmenityApi = (id: number, data: any) => {
  return request({
    url: `/api/amenities/${id}`,
    method: "put",
    data,
  });
};

// 删除设施
export const deleteAmenityApi = (id: number) => {
  return request({
    url: `/api/amenities/${id}`,
    method: "delete",
  });
};

/**
 * 获取指定房源的设施列表
 * @param homestayId 房源ID
 */
export const getHomestayAmenitiesApi = (homestayId: number) => {
  console.log(`尝试获取房源设施列表，房源ID: ${homestayId}`);

  // 首先尝试amenities控制器的端点，这个路径通常更可靠
  return request({
    url: `/api/amenities/homestay/${homestayId}`,
    method: "get",
  }).catch((error) => {
    console.error(`通过amenities控制器获取房源设施列表失败`, error);

    // 如果第一种方法失败，尝试第二个路径
    console.log(`尝试使用homestays控制器获取房源设施列表`);
    return request({
      url: `/api/homestays/${homestayId}/amenities`,
      method: "get",
    }).catch((homeError) => {
      console.error(`通过homestays控制器获取房源设施列表失败`, homeError);

      // 最后尝试v1版本API
      console.log(`尝试使用v1 API获取房源设施列表`);
      return request({
        url: `/api/v1/homestays/${homestayId}/amenities`,
        method: "get",
      });
    });
  });
};
