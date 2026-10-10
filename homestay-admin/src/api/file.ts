import request from "@/utils/request";

interface FileUploadResponse {
  success?: boolean; // Backend might use 'status'
  status?: string; // Check for 'success'
  data?: {
    fileName: string;
    url: string; // The URL we need
  };
  message?: string;
  // Other top-level fields from backend response
  fileName?: string;
  fileType?: string;
  fileSize?: number;
  downloadUrl?: string;
}

/**
 * 上传单个文件
 * @param file File 对象
 * @param type 文件类型 (e.g., 'homestay', 'avatar')
 */
export function uploadSingleFile(file: File, type: string = "common") {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("type", type);

  return request<FileUploadResponse>({
    // Use the defined interface
    url: "/api/files/upload",
    method: "post",
    data: formData,
    headers: {
      "Content-Type": "multipart/form-data", // Important for file uploads
    },
  }).then((response) => {
    // Adapt to the actual backend response structure
    const isSuccess = response && (response.success || response.status === "success");
    const fileUrl = response?.data?.url || response?.downloadUrl; // Check both possible locations

    if (isSuccess && fileUrl) {
      console.log("File uploaded successfully:", fileUrl);
      return fileUrl; // Return the URL
    } else {
      console.error("File upload failed or URL not found in response:", response);
      const errorMsg = response?.message || "文件上传失败";
      // Attempt to extract more specific error from nested data if available
      // const nestedMessage = (response?.data as any)?.message;
      // throw new Error(nestedMessage || errorMsg);
      throw new Error(errorMsg);
    }
  });
}

// Add other file-related API functions here if needed (e.g., deleteFile)
