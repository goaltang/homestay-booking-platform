/**
 * 头像上传逻辑
 * 从 ProfileManage.vue 抽取：上传前校验、统一文件上传 API、响应解析
 */
import { ElMessage } from "element-plus";
import { uploadHostAvatar } from "@/api/host";

interface AvatarForm {
  avatar?: string;
}

/** 从上传响应中解析头像 URL（兼容新旧格式） */
export function parseAvatarUrl(response: any): string {
  if (response?.data?.fileName) {
    return `/api/files/avatar/${response.data.fileName}`;
  }
  if (response?.fileName) {
    return `/api/files/avatar/${response.fileName}`;
  }
  if (response?.data?.url) {
    return response.data.url;
  }
  if (response?.url) {
    return response.url;
  }
  if (typeof response === "string") {
    return response;
  }
  return "";
}

export function useAvatarUpload(
  formData: AvatarForm,
  onAvatarUpdated: (url: string) => void
) {
  const handleAvatarSuccess = (response: any) => {
    const avatarUrl = parseAvatarUrl(response);

    if (avatarUrl) {
      formData.avatar = avatarUrl;
      ElMessage.success("头像上传成功");
      onAvatarUpdated(avatarUrl);
    } else {
      ElMessage.error("头像上传失败，返回格式不正确");
      console.error("无法解析头像URL:", response);
    }
  };

  const beforeAvatarUpload = (file: File) => {
    const isImage = file.type.startsWith("image/");
    const isLt10M = file.size / 1024 / 1024 < 10;

    if (!isImage) {
      ElMessage.error("上传头像图片只能是图片格式!");
      return false;
    }
    if (!isLt10M) {
      ElMessage.error("上传头像图片大小不能超过 10MB!");
      return false;
    }

    ElMessage.info("正在上传头像，请稍候...");

    const fd = new FormData();
    fd.append("file", file);

    uploadHostAvatar(fd)
      .then((response) => {
        handleAvatarSuccess(response);
      })
      .catch((error) => {
        console.error("头像上传失败:", error);
      });

    // 阻止 el-upload 默认上传行为
    return false;
  };

  return { handleAvatarSuccess, beforeAvatarUpload };
}
