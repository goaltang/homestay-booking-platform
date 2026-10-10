import request from "@/utils/request"; // Assuming request utility exists

// Upload cover image
export function uploadHomestayCoverImage(homestayId: number, file: File): Promise<any> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("homestayId", String(homestayId));

  return request({
    // Adjust URL to match backend controller mapping
    url: `/api/homestay-images/upload`,
    method: "post",
    data: formData,
    headers: {
      // Let the browser set the Content-Type
    },
  });
}

// Upload multiple images
export function uploadHomestayMultipleImages(homestayId: number, files: File[]): Promise<any[]> {
  const formData = new FormData();
  files.forEach((file) => formData.append("files", file));
  formData.append("homestayId", String(homestayId));

  return request({
    // Adjust URL to match backend controller mapping
    url: `/api/homestay-images/upload-multiple`,
    method: "post",
    data: formData,
    headers: {
      // Let the browser set the Content-Type
    },
  });
}

// Delete an image
export function deleteHomestayImage(imageId: number): Promise<void> {
  return request({
    // Assuming admin uses the same endpoint
    url: `/api/homestay-images/${imageId}`,
    method: "delete",
  });
}
