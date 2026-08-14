/**
 * HomestayForm 纯函数（从 HomestayForm.vue 抽取）
 */

interface HomestayTypeItem {
  code?: string;
  value?: string;
  name?: string;
  label?: string;
}

/** 根据类型代码查类型名称 */
export function getHomestayTypeText(
  typeCode: string | undefined,
  homestayTypes: HomestayTypeItem[]
): string {
  if (!typeCode) return "未知类型";

  const foundType = homestayTypes.find((t) => t.code === typeCode || t.value === typeCode);

  if (foundType) {
    return foundType.name || foundType.label || typeCode;
  }

  console.warn(`未能在 homestayTypes 列表中找到类型代码: ${typeCode}`);
  return "未知类型";
}

/** 处理设施数据，标准化为 { value, label } 数组 */
export function processAmenities(
  amenitiesData: any[]
): { value: string; label?: string }[] {
  if (!amenitiesData || !Array.isArray(amenitiesData)) {
    console.warn("设施数据无效，返回空数组");
    return [];
  }

  return amenitiesData
    .map((item) => {
      if (typeof item === "string") {
        return { value: item.trim() };
      }

      if (item && typeof item === "object") {
        const value = item.value || item.code || "";
        if (!value) {
          console.warn("跳过无效的设施项:", item);
          return null;
        }
        return {
          value: String(value).trim(),
          label: item.label || item.name || value,
        };
      }

      console.warn("无法处理的设施项:", item);
      return null;
    })
    .filter((item): item is { value: string; label?: string } => item !== null);
}

/** 格式化"上次保存时间"文本 */
export function formatLastSavedText(lastSaved: Date | null): string {
  if (!lastSaved) return "未保存";

  const now = new Date();
  const diff = now.getTime() - lastSaved.getTime();

  if (diff < 60 * 1000) {
    return "刚刚保存";
  }

  if (diff < 60 * 60 * 1000) {
    const minutes = Math.floor(diff / (60 * 1000));
    return `${minutes}分钟前保存`;
  }

  return `${lastSaved.toLocaleTimeString()}保存`;
}

/** 计算表单完成度百分比 */
export function calculateFormCompletion(form: Record<string, unknown>): number {
  let completedFields = 0;
  let totalFields = 0;

  const requiredFields = [
    "title",
    "type",
    "price",
    "provinceCode",
    "addressDetail",
    "maxGuests",
    "minNights",
    "coverImage",
    "description",
  ];

  for (const field of requiredFields) {
    totalFields++;
    if (form[field]) {
      completedFields++;
    }
  }

  if (Array.isArray(form.amenities) && (form.amenities as unknown[]).length > 0) {
    completedFields++;
  }
  totalFields++;

  if (Array.isArray(form.images) && (form.images as unknown[]).length > 0) {
    completedFields++;
  }
  totalFields++;

  return Math.round((completedFields / totalFields) * 100);
}

/** 标准化房源表单数据（价格/数字/设施/默认值） */
export function normalizeHomestayData(data: Record<string, any>): Record<string, any> {
  const processed = data;

  // 处理价格
  if (processed.price && typeof processed.price === "string") {
    processed.price = String(parseFloat(processed.price));
  }

  // 处理最大/最小入住
  if (processed.maxGuests) {
    processed.maxGuests = Number(processed.maxGuests);
  }
  if (processed.minNights) {
    processed.minNights = Number(processed.minNights);
  }

  // 处理设施数据（提取 value 字符串数组）
  if (Array.isArray(processed.amenities)) {
    processed.amenities = processed.amenities
      .map((amenity: any) => {
        if (typeof amenity === "string") return amenity;
        if (typeof amenity === "object" && amenity !== null) return amenity.value || "";
        return "";
      })
      .filter(Boolean);
  } else {
    processed.amenities = [];
  }

  // 确保必要字段有默认值，用于草稿保存
  if (!processed.title) processed.title = "";
  if (!processed.type) processed.type = "";
  if (!processed.description) processed.description = "";
  if (!processed.status) processed.status = "DRAFT";

  return processed;
}
