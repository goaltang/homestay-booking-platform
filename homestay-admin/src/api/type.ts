import request from "@/utils/request";

// 房源类型API
export const getAllTypesApi = () => {
  return request.get("/api/v1/homestay-types/all");
};

export const getTypeByIdApi = (id: number) => {
  return request.get(`/api/v1/homestay-types/${id}`);
};

export const createTypeApi = (data: any) => {
  return request.post("/api/v1/homestay-types", data);
};

export const updateTypeApi = (id: number, data: any) => {
  return request.put(`/api/v1/homestay-types/${id}`, data);
};

export const deleteTypeApi = (id: number) => {
  return request.delete(`/api/v1/homestay-types/${id}`);
};

// 类型分类API
export const getAllCategoriesApi = () => {
  return request.get("/api/v1/homestay-types/categories");
};

export const getCategoryByIdApi = (id: number) => {
  return request.get(`/api/v1/homestay-types/categories/${id}`);
};

export const createCategoryApi = (data: any) => {
  return request.post("/api/v1/homestay-types/categories", data);
};

export const updateCategoryApi = (id: number, data: any) => {
  return request.put(`/api/v1/homestay-types/categories/${id}`, data);
};

export const deleteCategoryApi = (id: number) => {
  return request.delete(`/api/v1/homestay-types/categories/${id}`);
};

// 获取按分类分组的房源类型
export const getTypesByCategoryApi = () => {
  return request.get("/api/v1/homestay-types/by-category");
};

// 获取指定分类下的房源类型
export const getTypesByCategoryIdApi = (categoryId: number) => {
  return request.get(`/api/v1/homestay-types/category/${categoryId}`);
};

export default {
  getAllTypesApi,
  getTypeByIdApi,
  createTypeApi,
  updateTypeApi,
  deleteTypeApi,
  getAllCategoriesApi,
  getCategoryByIdApi,
  createCategoryApi,
  updateCategoryApi,
  deleteCategoryApi,
  getTypesByCategoryApi,
  getTypesByCategoryIdApi,
};
