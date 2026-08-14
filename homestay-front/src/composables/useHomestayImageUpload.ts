/**
 * 房源图片上传逻辑（封面 + 图库）
 * 从 HomestayForm.vue 抽取：上传、错误状态、重试、移除
 */
import { ref, type ComputedRef } from "vue";
import { ElMessage } from "element-plus";
import { uploadHomestayImage } from "@/api/homestay";

interface EditableForm {
  coverImage?: string;
  images?: string[];
}

export function useHomestayImageUpload(
  form: EditableForm,
  homestayId: ComputedRef<number>
) {
  const uploadingCover = ref(false);
  const uploadingGallery = ref(false);

  const uploadError = ref(false);
  const uploadErrorTitle = ref("");
  const uploadErrorMessage = ref("");
  const uploadErrorDetails = ref<unknown>(null);
  const showDebugInfo = ref(false);
  const lastUploadType = ref<"cover" | "gallery" | null>(null);
  const lastUploadFile = ref<File | null>(null);

  // 上传前校验
  const beforeUpload = (file: File) => {
    const isImage = file.type.startsWith("image/");
    if (!isImage) {
      ElMessage.error("只能上传图片文件!");
      return false;
    }

    const isLt5M = file.size / 1024 / 1024 < 5;
    if (!isLt5M) {
      ElMessage.error("图片大小不能超过5MB!");
      return false;
    }

    const extension = file.name.substring(file.name.lastIndexOf(".") + 1).toLowerCase();
    const validExtensions = ["jpg", "jpeg", "png", "gif", "webp"];
    if (!validExtensions.includes(extension)) {
      ElMessage.error(`仅支持以下格式: ${validExtensions.join(", ")}`);
      return false;
    }

    return true;
  };

  // 重置上传状态
  const resetUploadState = (type: "cover" | "gallery", fileInput?: HTMLInputElement) => {
    if (type === "cover") {
      uploadingCover.value = false;
    } else {
      uploadingGallery.value = false;
    }

    if (fileInput) {
      fileInput.value = "";
    }
  };

  // 自定义处理上传函数
  const handleCustomUpload = (type: "cover" | "gallery") => {
    return (options: any) => {
      const { file, onSuccess, onError } = options;

      if (type === "cover") {
        uploadingCover.value = true;
      } else {
        uploadingGallery.value = true;
      }

      lastUploadType.value = type;
      lastUploadFile.value = file;

      if (!beforeUpload(file)) {
        resetUploadState(type);
        return;
      }

      ElMessage.info(`正在上传${type === "cover" ? "封面" : "图片集"}图片，请稍候...`);

      const uploadPromise = homestayId.value
        ? uploadHomestayImage(file, type, Number(homestayId.value))
        : uploadHomestayImage(file, type);

      uploadPromise
        .then((response) => {
          if (response.data && (response.data.status === "success" || response.data.success)) {
            let imageUrl = "";

            if (response.data.data) {
              if (typeof response.data.data === "object") {
                imageUrl =
                  response.data.data.url ||
                  response.data.data.imageUrl ||
                  response.data.data.downloadUrl;
              } else if (typeof response.data.data === "string") {
                imageUrl = response.data.data;
              }
            } else if (response.data.downloadUrl) {
              imageUrl = response.data.downloadUrl;
            }

            if (!imageUrl) {
              console.error("无法获取上传图片URL:", response.data);
              ElMessage.error("图片上传成功，但无法获取URL");
              resetUploadState(type);
              if (onError) onError(new Error("无法获取上传图片URL"));
              return;
            }

            if (type === "cover") {
              form.coverImage = imageUrl;
              ElMessage.success("封面图片上传成功");
            } else {
              if (!form.images) {
                form.images = [];
              }
              form.images.push(imageUrl);
              ElMessage.success("图片上传成功");
            }

            if (onSuccess) onSuccess(response);
          } else {
            console.error("图片上传失败:", response);
            ElMessage.error("图片上传失败: " + (response.data?.message || "未知错误"));
            if (onError) onError(new Error("上传失败"));
          }
        })
        .catch((error) => {
          console.error("图片上传异常:", error);
          if (onError) onError(error);
        })
        .finally(() => {
          resetUploadState(type);
        });
    };
  };

  // 清除上传错误
  const clearUploadError = () => {
    uploadError.value = false;
    uploadErrorTitle.value = "";
    uploadErrorMessage.value = "";
    uploadErrorDetails.value = null;
  };

  // 重试上传
  const retryLastUpload = async () => {
    if (lastUploadType.value && lastUploadFile.value) {
      clearUploadError();
      const uploadHandler = handleCustomUpload(lastUploadType.value);
      uploadHandler({
        file: lastUploadFile.value,
        onSuccess: () => {
          lastUploadType.value = null;
          lastUploadFile.value = null;
        },
        onError: (error: any) => {
          console.error("重试上传失败:", error);
        },
      });
    } else {
      ElMessage.warning("没有可重试的上传任务");
    }
  };

  // 显示/隐藏调试信息
  const toggleDebugInfo = () => {
    showDebugInfo.value = !showDebugInfo.value;
  };

  // 移除图库图片
  const removeGalleryImage = (index: number) => {
    if (form.images && index >= 0 && index < form.images.length) {
      form.images.splice(index, 1);
      ElMessage.success("已移除图片");
    }
  };

  // 移除封面图片
  const removeCoverImage = () => {
    form.coverImage = "";
    ElMessage.success("已移除封面图片");
  };

  return {
    uploadingCover,
    uploadingGallery,
    uploadError,
    uploadErrorTitle,
    uploadErrorMessage,
    uploadErrorDetails,
    showDebugInfo,
    handleCustomUpload,
    retryLastUpload,
    clearUploadError,
    toggleDebugInfo,
    removeGalleryImage,
    removeCoverImage,
  };
}
