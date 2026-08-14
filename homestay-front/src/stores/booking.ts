import { defineStore } from "pinia";
import { ref } from "vue";

/** 预订确认页所需的完整数据（从详情页传递到订单确认页） */
export interface BookingDetails {
  homestayId: number;
  checkInDate: string;
  checkOutDate: string;
  guestCount: number;
  totalPrice: number;
  quoteToken: string | null;
  couponIds: number[];
  homestayData: {
    id: number;
    title: string;
    coverImage: string;
    addressDetail: string;
    ownerId: number;
    ownerName: string;
    autoConfirm: boolean;
    price: number;
  };
  nights: number;
  cleaningFee: number;
  serviceFee: number;
  roomOriginalAmount: number;
  activityDiscountAmount: number;
  couponDiscountAmount: number;
  appliedPromotions: unknown[];
  availableCoupons: unknown[];
  dailyPrices?: unknown[];
}

export const useBookingStore = defineStore(
  "booking",
  () => {
    const bookingDetails = ref<BookingDetails | null>(null);

    const setBookingDetails = (details: BookingDetails) => {
      bookingDetails.value = details;
    };

    const clearBookingDetails = () => {
      bookingDetails.value = null;
    };

    return { bookingDetails, setBookingDetails, clearBookingDetails };
  },
  {
    // 用 sessionStorage 持久化，刷新订单确认页后数据仍保留
    persist: {
      key: "booking-details",
      storage: sessionStorage,
      paths: ["bookingDetails"],
    },
  }
);
