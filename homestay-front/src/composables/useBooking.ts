import { ref, reactive, computed, type Ref, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { useUserStore } from "@/stores/user";
import { useBookingStore } from "@/stores/booking";
import {
  formatDateString,
  calculateNights,
  fetchCalculatePrice,
  type PriceCalculationResult
} from "@/utils/homestayUtils";
import type { BookingDates, HomestayDetail } from "@/types/homestay";

export function useBooking(
  homestay: Ref<HomestayDetail | null>,
  pricePerNight: Ref<number>
) {
  const router = useRouter();
  const route = useRoute();
  const userStore = useUserStore();
  const bookingStore = useBookingStore();

  // sessionStorage key for preserving booking dates across login redirect
  const PENDING_BOOKING_KEY = 'pending-booking-dates';

  const bookingDates = reactive<BookingDates>({
    checkIn: null,
    checkOut: null,
    guests: 1,
  });
  const bookingDateRange = ref<[Date, Date] | null>(null);

  // 从 sessionStorage 恢复上次未提交预订的日期（解决登录跳转丢失问题）
  const restorePendingBookingDates = () => {
    const stored = sessionStorage.getItem(PENDING_BOOKING_KEY);
    if (!stored) return;

    try {
      const data = JSON.parse(stored);
      // 仅当匹配的民宿 ID 时才恢复，防止切到其他民宿后遗留旧数据
      if (data.homestayId !== homestay.value?.id) {
        sessionStorage.removeItem(PENDING_BOOKING_KEY);
        return;
      }

      const checkIn = new Date(data.checkIn);
      const checkOut = new Date(data.checkOut);
      if (!isNaN(checkIn.getTime()) && !isNaN(checkOut.getTime())) {
        bookingDates.checkIn = checkIn;
        bookingDates.checkOut = checkOut;
        bookingDates.guests = data.guests || 1;
        bookingDateRange.value = [checkIn, checkOut];
        sessionStorage.removeItem(PENDING_BOOKING_KEY);
        console.log("已恢复登录前的预订日期:", bookingDates);
      }
    } catch {
      sessionStorage.removeItem(PENDING_BOOKING_KEY);
    }
  };

  // 登录跳转前保存当前选择的日期
  const savePendingBookingDates = () => {
    if (bookingDates.checkIn && bookingDates.checkOut) {
      sessionStorage.setItem(
        PENDING_BOOKING_KEY,
        JSON.stringify({
          checkIn: bookingDates.checkIn.toISOString(),
          checkOut: bookingDates.checkOut.toISOString(),
          guests: bookingDates.guests,
          homestayId: homestay.value?.id,
        })
      );
    }
  };

  // homestay 数据加载完成后尝试恢复（避免 homestayId 为 null 时误判）
  watch(
    () => homestay.value?.id,
    (newId) => {
      if (newId) {
        restorePendingBookingDates();
      }
    },
    { immediate: true }
  );

  const totalNights = computed(() => {
    return calculateNights(bookingDates.checkIn, bookingDates.checkOut);
  });

  const isCalculatingPrice = ref(false);
  const priceDetails = ref<PriceCalculationResult | null>(null);
  const quoteToken = ref<string | null>(null);
  const selectedCouponIds = ref<number[]>([]);
  let calcTimer: number | null = null;

  const recalculatePrice = async () => {
    if (!bookingDates.checkIn || !bookingDates.checkOut || !homestay.value?.id) {
      priceDetails.value = null;
      quoteToken.value = null;
      return;
    }
    if (bookingDates.checkOut.getTime() <= bookingDates.checkIn.getTime()) {
      isCalculatingPrice.value = false;
      priceDetails.value = null;
      quoteToken.value = null;
      return;
    }
    isCalculatingPrice.value = true;
    try {
      const result = await fetchCalculatePrice(
        homestay.value!.id!,
        pricePerNight.value,
        bookingDates.checkIn!,
        bookingDates.checkOut!,
        bookingDates.guests,
        selectedCouponIds.value
      );
      priceDetails.value = result;
      quoteToken.value = result.quoteToken || null;
    } catch(e) {
      console.error("算价失败", e);
      priceDetails.value = null;
      quoteToken.value = null;
    } finally {
      isCalculatingPrice.value = false;
    }
  };

  watch([() => bookingDates.checkIn, () => bookingDates.checkOut, () => bookingDates.guests], () => {
    if (bookingDates.checkIn && bookingDates.checkOut && homestay.value?.id) {
       if (calcTimer) clearTimeout(calcTimer);
       calcTimer = window.setTimeout(() => recalculatePrice(), 500);
    } else {
       priceDetails.value = null;
       quoteToken.value = null;
    }
  }, { immediate: true });

  const handleDateRangeChange = (dates: [Date, Date] | null) => {
    if (dates && dates.length === 2 && dates[0] && dates[1]) {
      if (dates[1].getTime() <= dates[0].getTime()) {
        ElMessage.warning("退房日期必须在入住日期之后");
        bookingDateRange.value = null;
        bookingDates.checkIn = null;
        bookingDates.checkOut = null;
      } else {
        bookingDates.checkIn = dates[0];
        bookingDates.checkOut = dates[1];
      }
    } else {
      bookingDates.checkIn = null;
      bookingDates.checkOut = null;
    }
  };

  const bookHomestay = async () => {
    // 检查登录状态
    if (!userStore.isAuthenticated) {
      // 保存当前选择的日期，登录返回后可恢复
      savePendingBookingDates();
      ElMessageBox.confirm("您需要登录才能预订，是否现在登录？", "提示", {
        confirmButtonText: "去登录",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
          router.push({ path: "/login", query: { redirect: route.fullPath } });
        })
        .catch(() => {
          ElMessage.info("取消预订");
        });
      return;
    }

    // 检查日期和民宿信息
    if (
      !bookingDates.checkIn ||
      !bookingDates.checkOut ||
      !homestay.value ||
      totalNights.value <= 0
    ) {
      ElMessage.warning("请先选择有效的入住和退房日期");
      return;
    }

    // 准备传递给确认页的数据
    const bookingDetails = {
      homestayId: homestay.value.id!,
      checkInDate: formatDateString(bookingDates.checkIn),
      checkOutDate: formatDateString(bookingDates.checkOut),
      guestCount: bookingDates.guests,
      totalPrice: priceDetails.value?.payableAmount || priceDetails.value?.totalPrice || 0,
      quoteToken: quoteToken.value,
      couponIds: selectedCouponIds.value,
      // 完整的订单数据
      homestayData: {
        id: homestay.value.id!,
        title: homestay.value.title,
        coverImage: homestay.value.coverImage || "",
        addressDetail: homestay.value.addressDetail || "",
        ownerId: homestay.value.ownerId || 0,
        ownerName: homestay.value.ownerName || "",
        autoConfirm: homestay.value.autoConfirm || false,
        price: pricePerNight.value,
      },
      nights: priceDetails.value?.nights || totalNights.value,
      cleaningFee: priceDetails.value?.cleaningFee || 0,
      serviceFee: priceDetails.value?.serviceFee || 0,
      roomOriginalAmount: priceDetails.value?.roomOriginalAmount || 0,
      activityDiscountAmount: priceDetails.value?.activityDiscountAmount || 0,
      couponDiscountAmount: priceDetails.value?.couponDiscountAmount || 0,
      appliedPromotions: priceDetails.value?.appliedPromotions || [],
      availableCoupons: priceDetails.value?.availableCoupons || [],
    };

    // 将详细数据存入 booking store（sessionStorage 持久化），URL 只携带核心参数
    bookingStore.setBookingDetails(bookingDetails);

    // 跳转到订单确认页，只传递必要的核心参数
    router.push({
      path: "/order/confirm",
      query: {
        homestayId: bookingDetails.homestayId.toString(),
        checkIn: bookingDetails.checkInDate,
        checkOut: bookingDetails.checkOutDate,
        guests: bookingDetails.guestCount.toString(),
      },
    });
  };

  const resetBooking = () => {
    bookingDates.checkIn = null;
    bookingDates.checkOut = null;
    bookingDates.guests = 1;
    bookingDateRange.value = null;
  };

  const updateBookingDates = (dates: [Date, Date] | null) => {
    bookingDateRange.value = dates;
    if (dates && dates.length === 2 && dates[0] && dates[1]) {
      bookingDates.checkIn = dates[0];
      bookingDates.checkOut = dates[1];
    } else {
      bookingDates.checkIn = null;
      bookingDates.checkOut = null;
    }
  };

  return {
    bookingDates,
    bookingDateRange,
    totalNights,
    priceDetails,
    isCalculatingPrice,
    quoteToken,
    selectedCouponIds,
    recalculatePrice,
    handleDateRangeChange,
    bookHomestay,
    resetBooking,
    updateBookingDates,
  };
}
