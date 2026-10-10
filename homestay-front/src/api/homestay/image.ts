import request from "../../utils/request";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "";

/**
 * 上传房源图片
 */
export function uploadHomestayImage(
  file: File,
  imageType: string = "gallery",
  homestayId?: number
) {
  console.log(
    `开始上传房源${imageType === "cover" ? "封面" : "图片集"}图片:`,
    file.name,
    "大小:",
    (file.size / 1024).toFixed(2),
    "KB",
    homestayId ? `民宿ID: ${homestayId}` : ""
  );

  const formData = new FormData();
  formData.append("file", file);

  let url;

  if (homestayId) {
    url = "/api/homestay-images/upload";
    formData.append("homestayId", homestayId.toString());
  } else {
    url = "/api/files/upload";
    formData.append("type", "homestay");
    formData.append("imageType", imageType);
  }

  return request({
    url: `${API_BASE_URL}${url}`,
    method: "post",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data",
    },
    onUploadProgress: (progressEvent) => {
      const percentCompleted = Math.round(
        (progressEvent.loaded * 100) / (progressEvent.total || 1)
      );
      console.log(`上传进度: ${percentCompleted}%`);
    },
    timeout: 60000,
  })
    .then((response) => {
      console.log(`${imageType === "cover" ? "封面" : "图片集"}图片上传成功:`, response.data);

      if (response.data && response.data.data) {
        if (typeof response.data.data === "object") {
          if (!response.data.data.imageType) {
            response.data.data.imageType = imageType;
          }
        } else if (typeof response.data.data === "string") {
          const url = response.data.data;
          response.data.data = {
            url: url,
            imageType: imageType,
          };
        }
      } else if (response.data && response.data.status === "success") {
        if (!response.data.data) {
          response.data.data = {
            url: response.data.downloadUrl || "",
            imageType: imageType,
          };
        }
        response.data.success = true;
      }

      return response;
    })
    .catch((error) => {
      console.error(`${imageType === "cover" ? "封面" : "图片集"}图片上传失败:`, error);

      if (error.response) {
        console.error("服务器响应错误:", error.response.status, error.response.data);
      } else if (error.request) {
        console.error("未收到响应，请检查服务器是否运行或网络连接");
      } else {
        console.error("请求配置错误:", error.message);
      }

      throw error;
    });
}

/**
 * 批量上传民宿图片
 */
export function uploadHomestayImages(files: File[], homestayId: number) {
  const formData = new FormData();
  files.forEach((file) => {
    formData.append("files", file);
  });
  formData.append("homestayId", homestayId.toString());

  console.log(`开始批量上传民宿图片，民宿ID: ${homestayId}, 图片数量: ${files.length}`);

  return request({
    url: "/api/homestay-images/upload-multiple",
    method: "post",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data",
    },
    timeout: 120000,
  })
    .then((response) => {
      console.log(`批量上传民宿图片成功:`, response.data);
      return response;
    })
    .catch((error) => {
      console.error(`批量上传民宿图片失败:`, error);
      throw error;
    });
}

/**
 * 获取民宿图片
 */
export function getHomestayImages(homestayId: number) {
  return request({
    url: `/api/homestay-images/homestay/${homestayId}`,
    method: "get",
  })
    .then((response) => {
      console.log(`获取民宿图片成功，民宿ID: ${homestayId}`, response.data);
      return response;
    })
    .catch((error) => {
      console.error(`获取民宿图片失败，民宿ID: ${homestayId}`, error);
      throw error;
    });
}

/**
 * 删除民宿图片
 */
export function deleteHomestayImage(imageId: number) {
  return request({
    url: `/api/homestay-images/${imageId}`,
    method: "delete",
  })
    .then((response) => {
      console.log(`删除民宿图片成功，图片ID: ${imageId}`, response.data);
      return response;
    })
    .catch((error) => {
      console.error(`删除民宿图片失败，图片ID: ${imageId}`, error);
      throw error;
    });
}

/**
 * 删除民宿所有图片
 */
export function deleteAllHomestayImages(homestayId: number) {
  return request({
    url: `/api/homestay-images/homestay/${homestayId}`,
    method: "delete",
  })
    .then((response) => {
      console.log(`删除民宿所有图片成功，民宿ID: ${homestayId}`, response.data);
      return response;
    })
    .catch((error) => {
      console.error(`删除民宿所有图片失败，民宿ID: ${homestayId}`, error);
      throw error;
    });
}
