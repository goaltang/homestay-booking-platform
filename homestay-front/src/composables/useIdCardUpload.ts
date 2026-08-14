/**
 * 身份证照片上传逻辑（正面/背面）
 * 从 ProfileManage.vue 抽取：自定义上传（fetch + FormData）、上传前校验
 */
import { type Ref } from "vue";
import { ElMessage } from "element-plus";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8081";

interface IdCardForm {
  idCardFront?: string;
  idCardBack?: string;
}

export function useIdCardUpload(
  verifyForm: IdCardForm,
  idCardFrontFileList: Ref<any[]>,
  idCardBackFileList: Ref<any[]>
) {
  // 验证身份证照片上传前的检查
  const beforeIdCardUpload = (file: File) => {
    const isJPG = file.type === "image/jpeg";
    const isPNG = file.type === "image/png";
    const isLt10M = file.size / 1024 / 1024 < 10;

    if (!isJPG && !isPNG) {
      ElMessage.error("上传身份证照片只能是 JPG 或 PNG 格式!");
      return false;
    }
    if (!isLt10M) {
      ElMessage.error("上传身份证照片大小不能超过 10MB!");
      return false;
    }
    return true;
  };

  // 自定义处理上传，支持中文文件名并确保传递 type 参数
  const handleCustomUpload = (type: "idCardFront" | "idCardBack") => {
    return (options: any) => {
      const { file, onSuccess, onError } = options;

      if (!beforeIdCardUpload(file)) {
        return;
      }

      const formData = new FormData();
      formData.append("file", file);
      formData.append("type", type);

      ElMessage.info(
        `正在上传${type === "idCardFront" ? "身份证正面" : "身份证背面"}图片，请稍候...`
      );

      const fullUrl = `${API_BASE_URL}/api/host/upload-document`;

      fetch(fullUrl, {
        method: "POST",
        credentials: "include",
        body: formData,
      })
        .then((response) => {
          if (!response.ok) {
            throw new Error(`上传失败: ${response.status} ${response.statusText}`);
          }
          return response.json();
        })
        .then((result) => {
          let photoUrl = "";

          if (result && typeof result.url === "string" && result.url.trim() !== "") {
            photoUrl = result.url;
          } else if (
            result &&
            result.data &&
            typeof result.data.url === "string" &&
            result.data.url.trim() !== ""
          ) {
            photoUrl = result.data.url;
          }

          if (photoUrl) {
            ElMessage.success(
              `${type === "idCardFront" ? "身份证正面" : "身份证背面"}上传成功`
            );

            const fileData = {
              name: `${type === "idCardFront" ? "身份证正面" : "身份证背面"}.jpg`,
              url: photoUrl,
              status: "success",
              uid: Date.now(),
            };

            if (type === "idCardFront") {
              verifyForm.idCardFront = photoUrl;
              idCardFrontFileList.value = [fileData];
            } else {
              verifyForm.idCardBack = photoUrl;
              idCardBackFileList.value = [fileData];
            }

            onSuccess(fileData);
          } else {
            console.error("无法从响应中获取照片URL。响应:", result);
            ElMessage.error("上传失败: 无法从服务器响应中解析图片地址");
          }
        })
        .catch((error) => {
          console.error("上传失败:", error);
          onError(error);
        });

      // 阻止 el-upload 默认上传
      return false;
    };
  };

  return { beforeIdCardUpload, handleCustomUpload };
}
