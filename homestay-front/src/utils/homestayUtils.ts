import { codeToText } from "element-china-area-data";
import type { HomestayDetail } from "@/types/homestay";
import { differenceInCalendarDays } from "date-fns";

// 日期格式化工具
export const formatDateString = (date: Date | string | null): string => {
  if (!date) return "";
  try {
    const d = typeof date === "string" ? new Date(date) : date;
    if (!(d instanceof Date) || isNaN(d.getTime())) {
      console.error("Invalid date object for formatting:", date);
      return "";
    }
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  } catch (e) {
    console.error("Error formatting date:", date, e);
    return "";
  }
};

export const formatDisplayDate = (date: Date | string | null): string => {
  if (!date) return "日期无效";
  try {
    const d = typeof date === "string" ? new Date(date) : date;
    if (!(d instanceof Date) || isNaN(d.getTime())) return "日期无效";
    const year = d.getFullYear();
    const month = d.getMonth() + 1;
    const day = d.getDate();
    return `${year}年${month}月${day}日`;
  } catch (e) {
    return "日期格式错误";
  }
};

// 位置格式化工具
export const formatLocation = (homestay: HomestayDetail | null): string => {
  if (!homestay) return "位置待更新";

  const parts = [];

  // 优先使用 code 映射，失败时回退到后端返回的 text 字段
  if (homestay.provinceCode && codeToText[homestay.provinceCode]) {
    parts.push(codeToText[homestay.provinceCode]);
  } else if (homestay.provinceText) {
    parts.push(homestay.provinceText);
  }

  if (homestay.cityCode && codeToText[homestay.cityCode]) {
    const cityName = codeToText[homestay.cityCode];
    if (!parts.includes(cityName)) {
      parts.push(cityName);
    }
  } else if (homestay.cityText) {
    if (!parts.includes(homestay.cityText)) {
      parts.push(homestay.cityText);
    }
  }

  if (homestay.districtCode && codeToText[homestay.districtCode]) {
    parts.push(codeToText[homestay.districtCode]);
  } else if (homestay.districtText) {
    parts.push(homestay.districtText);
  }

  if (parts.length > 0) {
    return parts.join(" · ");
  }

  return homestay.addressDetail || "位置待更新";
};

// 价格计算工具
export const parsePrice = (homestay: HomestayDetail | null): number => {
  if (homestay) {
    let priceValue: number | undefined;

    if (typeof homestay.price !== "undefined" && homestay.price !== null) {
      priceValue = Number(homestay.price);
    } else if (
      typeof (homestay as any).pricePerNight !== "undefined" &&
      (homestay as any).pricePerNight !== null
    ) {
      priceValue = Number((homestay as any).pricePerNight);
    }

    if (priceValue !== undefined && !isNaN(priceValue)) {
      return priceValue;
    }
  }
  return 0;
};

export const calculateNights = (checkIn: Date | null, checkOut: Date | null): number => {
  if (!checkIn || !checkOut) return 0;
  const days = differenceInCalendarDays(checkOut, checkIn);
  return days > 0 ? days : 0;
};

// 图片处理工具
export const processImages = (homestay: HomestayDetail | null): string[] => {
  if (!homestay) return [];

  const rawImageUrls =
    homestay.images && Array.isArray(homestay.images)
      ? (homestay.images
          .map((img: string | { url: string }) => (typeof img === "string" ? img : img?.url))
          .filter(Boolean) as string[])
      : [];

  const coverImageUrl = homestay.coverImage;
  let finalImages: string[] = [];

  if (coverImageUrl) {
    finalImages.push(coverImageUrl);
    rawImageUrls.forEach((imgUrl) => {
      if (imgUrl !== coverImageUrl) {
        finalImages.push(imgUrl);
      }
    });
  } else {
    finalImages = rawImageUrls;
  }

  return finalImages;
};

// 房源类型格式化（code → 中文展示）
export const formatPropertyType = (type?: string): string => {
  const typeMap: Record<string, string> = {
    // 标准房源类型 code
    ENTIRE: "整套公寓",
    PRIVATE: "独立房间",
    LOFT: "复式住宅",
    VILLA: "别墅",
    STUDIO: "开间/单间",
    TOWNHOUSE: "联排别墅",
    COURTYARD: "四合院/院子",
    HOTEL: "酒店公寓",
    // 兼容旧中文值（迁移前的数据）
    整套公寓: "整套公寓",
    整套房子: "整套公寓",
    复式住宅: "复式住宅",
    Loft: "复式住宅",
    独立房间: "独立房间",
    单间: "独立房间",
    别墅: "别墅",
    洋房: "别墅",
    四合院: "四合院/院子",
    家庭旅馆: "四合院/院子",
    院子: "四合院/院子",
    酒店公寓: "酒店公寓",
    // 旧版英文 code
    TRADITIONAL: "传统民居",
    APARTMENT: "公寓",
    UNIQUE_STAY: "特色房源",
    BOUTIQUE_HOTEL: "精品酒店",
    CABIN: "小木屋",
  };

  if (type && typeMap[type]) {
    return typeMap[type];
  }
  return type || "特色住宿";
};

export interface AppliedPricingRule {
  ruleId?: number;
  ruleName: string;
  ruleType: string;
  adjustmentType: string;
  adjustmentValue: number;
  deltaAmount: number;
}

export interface DailyPriceItem {
  date: string;
  basePrice: number;
  price: number;
  finalPrice: number;
  isWeekend: boolean;
  isHoliday: boolean;
  holidayName?: string;
  appliedRules?: AppliedPricingRule[];
}

// 模拟异步的后端算价接口，代替前端硬编码计算费率
export interface PriceCalculationResult {
  basePrice: number;
  cleaningFee: number;
  serviceFee: number;
  totalPrice: number;
  nights: number;
  quoteToken?: string;
  roomOriginalAmount?: number;
  activityDiscountAmount?: number;
  couponDiscountAmount?: number;
  payableAmount?: number;
  dailyPrices?: DailyPriceItem[];
  availableCoupons?: any[];
  appliedPromotions?: any[];
  appliedPricingRules?: AppliedPricingRule[];
}

export const fetchCalculatePrice = async (
  homestayId: number,
  pricePerNight: number, // 保留为了兼容签名，实际后端会读取数据库的
  checkIn: Date,
  checkOut: Date,
  guests: number,
  couponIds?: number[]
): Promise<PriceCalculationResult> => {
  try {
    // 优先使用统一报价接口
    const { getPricingQuote } = await import("@/api/promotion");
    const res = await getPricingQuote({
      homestayId,
      checkInDate: formatDateString(checkIn),
      checkOutDate: formatDateString(checkOut),
      guestCount: guests,
      couponIds,
    });
    const result = res.data || res;
    return {
      basePrice: result.roomOriginalAmount || result.basePrice || 0,
      cleaningFee: result.cleaningFee || 0,
      serviceFee: result.serviceFee || 0,
      totalPrice: result.payableAmount || result.totalPrice || 0,
      nights: result.nights || calculateNights(checkIn, checkOut),
      quoteToken: result.quoteToken,
      roomOriginalAmount: result.roomOriginalAmount,
      activityDiscountAmount: result.activityDiscountAmount,
      couponDiscountAmount: result.couponDiscountAmount,
      payableAmount: result.payableAmount,
      dailyPrices: result.dailyPrices,
      availableCoupons: result.availableCoupons,
      appliedPromotions: result.appliedPromotions,
    };
  } catch (err) {
    console.error("统一报价失败，降级为本地估算", err);
    // 降级为本地计算，保证应用可用性
    const nights = calculateNights(checkIn, checkOut);
    const basePrice = pricePerNight * nights;
    return {
      nights,
      basePrice,
      cleaningFee: 0,
      serviceFee: 0,
      totalPrice: basePrice,
    };
  }
};
