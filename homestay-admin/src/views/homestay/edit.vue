<template>
  <div class="homestay-edit-form">
    <el-page-header
      @back="goBack"
      :content="isEditMode ? `编辑房源 (ID: ${homestayId})` : '新增房源'"
    >
    </el-page-header>

    <el-card shadow="never" style="margin-top: 20px">
      <el-form
        ref="homestayFormRef"
        :model="homestayForm"
        :rules="formRules"
        label-width="100px"
        v-loading="loading"
      >
        <el-form-item label="房源名称" prop="title">
          <el-input v-model="homestayForm.title" placeholder="请输入房源名称"></el-input>
        </el-form-item>

        <el-form-item label="房源类型" prop="type">
          <el-select v-model="homestayForm.type" placeholder="请选择房源类型" clearable filterable>
            <el-option
              v-for="type in homestayTypes"
              :key="type.code"
              :label="type.name"
              :value="type.code"
            >
            </el-option>
            <template #empty v-if="homestayTypes.length === 0">
              <p style="text-align: center; color: #999">加载中或无可用类型...</p>
            </template>
          </el-select>
        </el-form-item>

        <el-form-item label="价格(元/晚)" prop="price">
          <el-input-number
            v-model="homestayForm.price"
            :precision="2"
            :step="10"
            :min="0"
            placeholder="请输入价格"
            controls-position="right"
            style="width: 200px"
          ></el-input-number>
        </el-form-item>

        <el-form-item label="所在地区" prop="provinceCode">
          <el-cascader
            v-model="selectedAreaCodes"
            :options="regionData"
            :props="{
              expandTrigger: 'hover',
              value: 'value',
              label: 'label',
              children: 'children',
            }"
            placeholder="请选择省/市/区"
            clearable
            style="width: 100%"
            @change="handleAreaChange"
          />
        </el-form-item>
        <el-form-item label="详细地址" prop="addressDetail">
          <el-input
            v-model="homestayForm.addressDetail"
            placeholder="请输入街道名称、门牌号等详细地址"
          ></el-input>
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            type="textarea"
            :rows="4"
            v-model="homestayForm.description"
            placeholder="请输入房源描述"
          ></el-input>
        </el-form-item>

        <el-form-item label="最大入住" prop="maxGuests">
          <el-input-number
            v-model="homestayForm.maxGuests"
            :min="1"
            placeholder="人数"
            controls-position="right"
          ></el-input-number>
        </el-form-item>
        <el-form-item label="最少入住" prop="minNights">
          <el-input-number
            v-model="homestayForm.minNights"
            :min="1"
            placeholder="晚数"
            controls-position="right"
          ></el-input-number>
        </el-form-item>

        <el-form-item label="设施" prop="selectedAmenities">
          <el-checkbox-group v-model="homestayForm.selectedAmenities">
            <el-checkbox
              v-for="amenity in allAmenities"
              :key="amenity.value"
              :label="amenity.value"
              border
              style="margin-right: 10px; margin-bottom: 10px"
            >
              {{ amenity.label }}
            </el-checkbox>
          </el-checkbox-group>
          <div v-if="allAmenities.length === 0 && !loading">暂无可选择的设施</div>
        </el-form-item>

        <el-form-item label="封面图" prop="coverImage">
          <el-upload
            ref="coverUploadRef"
            action=""
            :http-request="handleCoverUploadRequest"
            list-type="picture-card"
            :limit="1"
            :file-list="coverFileList"
            :before-upload="beforeImageUpload"
            :on-remove="handleCoverRemove"
            :on-preview="handlePictureCardPreview"
            :on-exceed="handleExceed"
            accept="image/jpeg, image/png, image/gif, image/webp"
          >
            <el-icon>
              <Plus />
            </el-icon>
            <template #tip>
              <div class="el-upload__tip">
                请上传封面图片 (JPG/PNG/GIF/WEBP, 不超过 2MB).
                <span v-if="!isEditMode"> 创建成功后将自动上传.</span>
                <span v-else> 编辑模式下会立即上传.</span>
              </div>
            </template>
          </el-upload>
        </el-form-item>

        <el-form-item label="房源图片" prop="images">
          <el-upload
            ref="multipleUploadRef"
            action=""
            :http-request="handleMultipleUploadRequest"
            list-type="picture-card"
            multiple
            :file-list="multipleFileList"
            :before-upload="beforeImageUpload"
            :on-remove="handleMultipleRemove"
            :on-preview="handlePictureCardPreview"
            accept="image/jpeg, image/png, image/gif, image/webp"
          >
            <el-icon>
              <Plus />
            </el-icon>
            <template #tip>
              <div class="el-upload__tip">
                可上传多张房源图片 (JPG/PNG/GIF/WEBP, 每张不超过 2MB).
                <span v-if="!isEditMode"> 创建成功后将自动上传.</span>
                <span v-else> 编辑模式下会立即上传 (待实现).</span>
              </div>
            </template>
          </el-upload>
        </el-form-item>

        <el-form-item label="推荐房源">
          <el-switch v-model="homestayForm.featured" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="submitForm" :loading="submitting">{{
            isEditMode ? "保存更新" : "创建房源"
          }}</el-button>
          <el-button @click="goBack">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-dialog v-model="dialogVisible" title="图片预览">
      <img w-full :src="dialogImageUrl" alt="Preview Image" style="width: 100%" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch, nextTick } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  ElMessage,
  FormInstance,
  FormRules,
  UploadProps,
  UploadUserFile,
  UploadRequestOptions,
  UploadInstance,
} from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { regionData } from "element-china-area-data";
import {
  getHomestayDetail,
  createHomestay,
  updateHomestay,
  getActiveHomestayTypes,
} from "@/api/homestay";
import { getAllAvailableAmenities } from "@/api/amenity";
import {
  uploadHomestayCoverImage,
  uploadHomestayMultipleImages,
  deleteHomestayImage,
} from "@/api/homestayImage";

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

interface HomestayTypeDTO {
  code: string;
  name: string;
}

// Interface for expected response on successful image upload (in edit mode)
interface ImageUploadResponse {
  id: number;
  imageUrl?: string;
  // Add other relevant fields if the API returns more
}

// Custom type for pending files with UID
type PendingFile = File & { uid: number };

const route = useRoute();
const router = useRouter();

const homestayFormRef = ref<FormInstance>();
const coverUploadRef = ref<UploadInstance>();
const multipleUploadRef = ref<UploadInstance>();
const loading = ref(false);
const submitting = ref(false);
const allAmenities = ref<AmenityDTO[]>([]);
const homestayTypes = ref<HomestayTypeDTO[]>([]);

const homestayId = ref<number | null>(route.params.id ? Number(route.params.id) : null);
const isEditMode = computed(() => homestayId.value !== null);

const pendingCoverFile = ref<File | null>(null); // Can remain File or be PendingFile if UID needed
const pendingMultipleFiles = ref<PendingFile[]>([]); // Use PendingFile type

const selectedAreaCodes = ref<string[]>([]);

const homestayForm = reactive<any>({
  id: undefined,
  title: "",
  type: "",
  price: 0,
  provinceCode: "",
  cityCode: "",
  districtCode: "",
  addressDetail: "",
  description: "",
  maxGuests: 1,
  minNights: 1,
  featured: false,
  images: [],
  amenities: [],
  selectedAmenities: [],
  coverImage: "",
  status: "PENDING",
});

const coverFileList = ref<UploadUserFile[]>([]);
const multipleFileList = ref<UploadUserFile[]>([]);

watch(
  () => homestayForm.coverImage,
  (newUrl) => {
    if (newUrl && isEditMode.value) {
      if (!coverFileList.value.some((file) => file.url === newUrl)) {
        coverFileList.value = [
          {
            name: newUrl.substring(newUrl.lastIndexOf("/") + 1) || "cover",
            url: newUrl,
            status: "success",
          },
        ];
      }
    } else if (!newUrl && !pendingCoverFile.value) {
      coverFileList.value = [];
    }
  },
  { immediate: true }
);

watch(
  () => homestayForm.images,
  (newImageUrls) => {
    // Check if it's an array of strings (from API) or objects (potentially updated locally)
    if (Array.isArray(newImageUrls) && isEditMode.value) {
      if (newImageUrls.every((item) => typeof item === "string")) {
        // Handle array of strings (URLs from initial load)
        multipleFileList.value = newImageUrls.map((url: string, index: number) => {
          const name = url.substring(url.lastIndexOf("/") + 1) || `image_${index}`;
          return {
            uid: Date.now() + index,
            name: name,
            status: "success", // Mark as success since it exists
            url: url,
            // Store URL in response, ID is unknown for these initially loaded string URLs
            response: { imageUrl: url },
          };
        });
      } else if (
        newImageUrls.every((item) => typeof item === "object" && item !== null && item.imageUrl)
      ) {
        // Handle array of objects (e.g., after upload or if API changes)
        multipleFileList.value = newImageUrls.map((img: any, index: number) => ({
          uid: img.id || Date.now() + index, // Use backend ID if available
          name:
            img.imageUrl?.substring(img.imageUrl.lastIndexOf("/") + 1) ||
            `image_${img.id || index}`,
          status: "success",
          url: img.imageUrl,
          response: { id: img.id, imageUrl: img.imageUrl } as ImageUploadResponse,
        }));
      }
      // Add more conditions if other formats are possible
    } else if (
      (!newImageUrls || newImageUrls.length === 0) &&
      pendingMultipleFiles.value.length === 0
    ) {
      // Clear list if data is empty and no pending files
      multipleFileList.value = [];
    }
  },
  { immediate: true, deep: true }
);

watch(selectedAreaCodes, (newVal) => {
  if (newVal && newVal.length === 3) {
    homestayForm.provinceCode = newVal[0];
    homestayForm.cityCode = newVal[1];
    homestayForm.districtCode = newVal[2];
  } else {
    homestayForm.provinceCode = "";
    homestayForm.cityCode = "";
    homestayForm.districtCode = "";
  }
  // 手动触发表单验证
  nextTick(() => {
    homestayFormRef.value?.validateField("provinceCode");
  });
});

const formRules = reactive<FormRules>({
  title: [{ required: true, message: "请输入房源名称", trigger: "blur" }],
  type: [{ required: true, message: "请选择房源类型", trigger: "change" }],
  price: [
    { required: true, message: "请输入价格", trigger: "blur" },
    { type: "number", message: "价格必须是数字值" },
  ],
  provinceCode: [{ required: true, message: "请选择所在地区", trigger: "change" }],
  addressDetail: [{ required: true, message: "请输入详细地址", trigger: "blur" }],
  maxGuests: [
    { required: true, message: "请输入最大入住人数", trigger: "blur" },
    { type: "integer", min: 1, message: "人数必须是正整数", trigger: "blur" },
  ],
  minNights: [
    { required: true, message: "请输入最少入住晚数", trigger: "blur" },
    { type: "integer", min: 1, message: "晚数必须是正整数", trigger: "blur" },
  ],
});

const handleCoverUploadRequest = async (options: UploadRequestOptions) => {
  const file = options.file;
  if (!beforeImageUpload(file)) {
    coverUploadRef.value?.handleRemove(file as any);
    return;
  }

  if (isEditMode.value && homestayId.value) {
    console.log("Edit Mode: Uploading cover image...");
    try {
      const result: ImageUploadResponse = await uploadHomestayCoverImage(homestayId.value, file);
      ElMessage.success("封面图上传成功");
      homestayForm.coverImage = result.imageUrl;
      // Manually update the file list entry after successful upload
      const currentFile = coverFileList.value.find((f) => f.uid === (file as any).uid);
      if (currentFile) {
        currentFile.status = "success";
        currentFile.response = result;
        currentFile.url = result.imageUrl; // Update URL from response
      } else {
        // Fallback: replace the list if find fails (e.g., UID mismatch)
        coverFileList.value = [
          {
            name: result.imageUrl?.substring(result.imageUrl.lastIndexOf("/") + 1) || "cover",
            url: result.imageUrl,
            status: "success",
            uid: (file as any).uid || Date.now(), // Try to keep original uid
            response: result,
          },
        ];
      }
      // Call onSuccess with just the response, as expected by types
      options.onSuccess(result);
    } catch (error: any) {
      console.error("Cover image upload failed:", error);
      ElMessage.error(`封面图上传失败: ${error.message || "未知错误"}`);
      options.onError(error);
    }
  } else {
    // Create mode remains the same
    console.log("Create Mode: Staging cover image...");
    pendingCoverFile.value = file;
    const previewFile: UploadUserFile = {
      name: file.name,
      url: URL.createObjectURL(file),
      status: "ready",
      uid: Date.now(),
      raw: file,
    };
    coverFileList.value = [previewFile];
  }
};

const handleMultipleUploadRequest = async (options: UploadRequestOptions) => {
  const file = options.file;
  if (!beforeImageUpload(file)) {
    multipleUploadRef.value?.handleRemove(file as any);
    return;
  }

  if (isEditMode.value && homestayId.value) {
    console.log("Edit Mode: Uploading multiple image...");
    try {
      const results: ImageUploadResponse[] = await uploadHomestayMultipleImages(homestayId.value, [
        file,
      ]);
      if (results && results.length > 0) {
        const uploadedImage = results[0];
        ElMessage.success(`图片 ${file.name} 上传成功`);
        if (!homestayForm.images) homestayForm.images = [];
        homestayForm.images.push(uploadedImage);

        // Manually update the file list item
        const fileIndex = multipleFileList.value.findIndex((f) => f.uid === (file as any).uid);
        if (fileIndex > -1) {
          multipleFileList.value[fileIndex].status = "success";
          multipleFileList.value[fileIndex].response = uploadedImage;
          multipleFileList.value[fileIndex].url = uploadedImage.imageUrl;
        } else {
          multipleFileList.value.push({
            uid: uploadedImage.id, // Using backend ID seems reasonable here
            name:
              uploadedImage.imageUrl?.substring(uploadedImage.imageUrl.lastIndexOf("/") + 1) ||
              file.name,
            status: "success",
            url: uploadedImage.imageUrl,
            response: uploadedImage,
          });
        }
        // Call onSuccess with just the response
        options.onSuccess(uploadedImage);
      } else {
        throw new Error("API did not return expected image details.");
      }
    } catch (error: any) {
      console.error(`Image ${file.name} upload failed:`, error);
      ElMessage.error(`图片 ${file.name} 上传失败: ${error.message || "未知错误"}`);
      options.onError(error);
    }
  } else {
    // Create mode remains the same
    console.log("Create Mode: Staging multiple image...");
    const fileWithUid = file as PendingFile;
    fileWithUid.uid = Date.now() + Math.random();
    pendingMultipleFiles.value.push(fileWithUid);
    const previewFile: UploadUserFile = {
      name: file.name,
      url: URL.createObjectURL(file),
      status: "ready",
      uid: fileWithUid.uid,
    };
    multipleFileList.value.push(previewFile);
  }
};

const beforeImageUpload: UploadProps["beforeUpload"] = (rawFile) => {
  const allowedTypes = ["image/jpeg", "image/png", "image/gif", "image/webp"];
  const maxSize = 2 * 1024 * 1024;

  if (!allowedTypes.includes(rawFile.type)) {
    ElMessage.error("图片格式无效! (仅支持 JPG/PNG/GIF/WEBP)");
    return false;
  }
  if (rawFile.size > maxSize) {
    ElMessage.error("图片大小不能超过 2MB!");
    return false;
  }
  return true;
};

const handleCoverRemove: UploadProps["onRemove"] = async (uploadFile, _uploadFiles) => {
  if (isEditMode.value && homestayId.value) {
    const imageIdToDelete = (uploadFile.response as ImageUploadResponse)?.id;
    // Also handle case where image was loaded initially (response might be null but url exists)
    const initialImageUrl =
      !uploadFile.response && uploadFile.url && uploadFile.status === "success"
        ? uploadFile.url
        : null;

    if (imageIdToDelete) {
      console.log("Edit Mode: Removing cover image ID:", imageIdToDelete);
      try {
        // Call the actual API
        await deleteHomestayImage(imageIdToDelete);
        ElMessage.success("封面图删除成功");
        homestayForm.coverImage = ""; // Clear form data
        coverFileList.value = []; // Clear visual list
      } catch (error: any) {
        console.error("Cover image delete failed:", error);
        ElMessage.error(`封面图删除失败: ${error.message || "未知错误"}`);
        // Re-add the file to the list visually if deletion failed
        coverFileList.value = [uploadFile];
        return; // Prevent clearing below if API failed
      }
    } else if (initialImageUrl && homestayForm.coverImage === initialImageUrl) {
      // If removing an initially loaded image without an ID in response (might need adjustment)
      console.warn(
        "Attempting to remove initial cover image based on URL. Backend logic might be needed if ID is required for deletion."
      );
      // Ideally, fetchHomestayDetail should populate response with ID
      // For now, just clear it visually and from form
      ElMessage.info("封面图已从列表移除 (请确认后端是否需要额外操作)");
      homestayForm.coverImage = "";
      coverFileList.value = [];
    } else if (uploadFile.status !== "ready") {
      console.warn("Cannot delete cover image: ID not found.");
    }
  } else {
    // Create mode logic remains the same (removing staged file)
    console.log("Create Mode: Removing staged cover image.");
    pendingCoverFile.value = null;
    if (uploadFile.url && uploadFile.url.startsWith("blob:")) {
      URL.revokeObjectURL(uploadFile.url);
    }
    coverFileList.value = [];
    homestayForm.coverImage = ""; // Also clear in case blob url was assigned
  }
};

const handleMultipleRemove: UploadProps["onRemove"] = async (uploadFile, uploadFiles) => {
  multipleFileList.value = uploadFiles;

  if (isEditMode.value && homestayId.value) {
    // ... (Edit mode API call logic seems okay)
    const imageIdToDelete = (uploadFile.response as ImageUploadResponse)?.id;
    if (imageIdToDelete) {
      console.log("Edit Mode: Removing image ID:", imageIdToDelete);
      try {
        await deleteHomestayImage(imageIdToDelete);
        ElMessage.success(`图片 ${uploadFile.name} 删除成功`);
        const index = homestayForm.images.findIndex(
          (img: ImageUploadResponse) => img.id === imageIdToDelete
        );
        if (index > -1) {
          homestayForm.images.splice(index, 1);
        }
      } catch (error: any) {
        console.error(`Image ${uploadFile.name} delete failed:`, error);
        ElMessage.error(`图片 ${uploadFile.name} 删除失败: ${error.message || "未知错误"}`);
        multipleFileList.value = [uploadFile, ...uploadFiles];
        return;
      }
    } else if (uploadFile.url && uploadFile.status === "success" && !uploadFile.response) {
      console.warn(
        "Attempting to remove initial image based on URL. Backend logic might be needed."
      );
      const index = homestayForm.images.findIndex(
        (img: ImageUploadResponse) => img.imageUrl === uploadFile.url
      );
      if (index > -1) {
        ElMessage.info(`图片 ${uploadFile.name} 已从列表移除 (请确认后端是否需要额外操作)`);
        homestayForm.images.splice(index, 1);
      } else {
        console.warn("Could not find image in form data based on URL.");
      }
    } else if (uploadFile.status !== "ready") {
      console.warn("Cannot delete image: ID not found in response.");
    }
  } else {
    // CREATE MODE: Find the corresponding UploadUserFile to revoke URL
    console.log("Create Mode: Removing staged image:", uploadFile.name, "UID:", uploadFile.uid);
    const index = pendingMultipleFiles.value.findIndex((f) => f.uid === uploadFile.uid);
    if (index > -1) {
      pendingMultipleFiles.value.splice(index, 1);
      // Find the corresponding file in multipleFileList to revoke its URL
      const fileInList = multipleFileList.value.find((f) => f.uid === uploadFile.uid);
      if (fileInList && fileInList.url && fileInList.url.startsWith("blob:")) {
        URL.revokeObjectURL(fileInList.url);
      }
      console.log("Pending files remaining:", pendingMultipleFiles.value.length);
    } else {
      console.warn(
        "Could not find corresponding pending file to remove using UID:",
        uploadFile.uid
      );
    }
    // Also ensure the file is removed from the visual list (el-upload passes the updated list)
    multipleFileList.value = uploadFiles;
  }
};

const handleExceed: UploadProps["onExceed"] = (files, _uploadFiles) => {
  ElMessage.warning(`封面图只能上传一张, 您选择了 ${files.length} 张, 本次上传已取消。`);
};

const dialogImageUrl = ref("");
const dialogVisible = ref(false);

const handlePictureCardPreview: UploadProps["onPreview"] = (uploadFile) => {
  dialogImageUrl.value = uploadFile.url!;
  dialogVisible.value = true;
};

const fetchAllAmenities = async () => {
  try {
    allAmenities.value = await getAllAvailableAmenities(true);
    console.log("Fetched all available amenities:", allAmenities.value);
  } catch (error) {
    console.error("获取可用设施列表失败:", error);
    ElMessage.error("获取可用设施列表失败");
  }
};

const fetchHomestayTypes = async () => {
  try {
    console.log("[fetchHomestayTypes] Fetching active homestay types...");
    const types = await getActiveHomestayTypes(); // 调用 API
    console.log("[fetchHomestayTypes] API response:", types);
    if (Array.isArray(types)) {
      homestayTypes.value = types; // 赋值
      console.log("[fetchHomestayTypes] Assigned homestayTypes.value:", homestayTypes.value);
    } else {
      console.error("[fetchHomestayTypes] Invalid data received from API:", types);
      homestayTypes.value = [];
    }
  } catch (error) {
    console.error("获取房源类型列表失败:", error);
    ElMessage.error("获取房源类型列表失败");
    homestayTypes.value = []; // 出错时清空
  }
};

const loadHomestayData = async () => {
  if (homestayId.value) {
    loading.value = true;
    try {
      const response = await getHomestayDetail(homestayId.value);
      const data = response; // 假设 adaptHomestayItem 已处理
      Object.assign(homestayForm, data);

      // --- 地址回显到 Cascader ---
      if (data.provinceCode && data.cityCode) {
        const codes = [data.provinceCode, data.cityCode];
        if (data.districtCode) {
          codes.push(data.districtCode);
        }
        selectedAreaCodes.value = codes;
        console.log("地址回显:", selectedAreaCodes.value);
      } else {
        selectedAreaCodes.value = []; // 清空以防万一
      }
      // --- 回显结束 ---

      // 处理设施回显 (假设 amenities 是字符串数组)
      if (Array.isArray(data.amenities)) {
        // 假设 API 返回的是设施代码/值字符串数组
        homestayForm.selectedAmenities = data.amenities;
      }
      // 处理图片回显已通过 watch 完成
    } catch (error) {
      console.error("加载房源详情失败:", error);
      ElMessage.error("加载房源详情失败");
      goBack();
    } finally {
      loading.value = false;
    }
  }
};

onMounted(async () => {
  loading.value = true;
  await Promise.all([fetchAllAmenities(), fetchHomestayTypes()]);

  if (isEditMode.value) {
    await loadHomestayData();
  } else {
    homestayForm.status = "PENDING";
    homestayForm.selectedAmenities = [];
    homestayForm.title = "";
    homestayForm.type = "";
    homestayForm.price = 0;
    homestayForm.coverImage = "";
    homestayForm.images = [];
    loading.value = false;
  }
});

const submitForm = async () => {
  if (!homestayFormRef.value) return;
  await homestayFormRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true;
      try {
        // 准备提交的数据 (已包含新地址字段)
        const dataToSubmit = { ...homestayForm };
        // 确保 selectedAmenities 转换成后端需要的 amenities 格式
        dataToSubmit.amenities = dataToSubmit.selectedAmenities;
        delete dataToSubmit.selectedAmenities;

        // 移除旧地址字段 (如果 homestayForm 还残留)
        delete dataToSubmit.province;
        delete dataToSubmit.city;
        delete dataToSubmit.district;
        delete dataToSubmit.address;

        if (isEditMode.value && homestayId.value) {
          // --- 更新房源 ---
          await updateHomestay(homestayId.value, dataToSubmit);
          ElMessage.success("房源更新成功");
        } else {
          // --- 创建房源 ---
          // 确保 status 正确设置
          dataToSubmit.status = "PENDING"; // 或者根据需要设置
          await createHomestay(dataToSubmit);
          ElMessage.success("房源创建成功");
        }
        // --- 图片上传处理 (根据需要调整) ---
        // ...
        goBack();
      } catch (error: any) {
        console.error("提交失败:", error);
        ElMessage.error(error.response?.data?.message || error.message || "操作失败");
      } finally {
        submitting.value = false;
      }
    } else {
      ElMessage.warning("请检查表单填写是否正确");
    }
  });
};

const goBack = () => {
  router.push("/homestays");
};

// --- 添加空的 handleAreaChange 函数以消除 Linter 错误 ---
const handleAreaChange = (value: string[]) => {
  console.log("Cascader area changed:", value);
  // 当前主要逻辑在 watch 中处理，这里可以留空或添加额外逻辑
};
</script>

<style scoped lang="scss">
.homestay-edit-form {
  padding: 20px;
}

.el-checkbox-group {
  display: flex;
  flex-wrap: wrap;
}
</style>
