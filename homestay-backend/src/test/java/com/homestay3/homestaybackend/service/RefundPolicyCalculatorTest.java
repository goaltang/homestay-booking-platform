package com.homestay3.homestaybackend.service;

import com.homestay3.homestaybackend.dto.PricingResult;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.model.PaymentStatus;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundPolicyCalculatorTest {

    private static final LocalDate CHECK_IN_DATE = LocalDate.of(2026, 10, 15);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private PricingService pricingService;

    @ParameterizedTest
    @CsvSource({
            "1, 1, 86401, 24, 100, 全额退款",
            "1, 1, 86400, 24, 100, 全额退款",
            "1, 1, 86399, 23, 0, 不予退款",
            "2, 1, 172801, 48, 100, 全额退款",
            "2, 1, 172800, 48, 100, 全额退款",
            "2, 1, 172799, 47, 50, 退款50%",
            "2, 1, 86401, 24, 50, 退款50%",
            "2, 1, 86400, 24, 50, 退款50%",
            "2, 1, 86399, 23, 0, 不予退款",
            "3, 1, 259201, 72, 100, 全额退款",
            "3, 1, 259200, 72, 100, 全额退款",
            "3, 1, 259199, 71, 50, 退款50%",
            "1, 1, -172800, -48, 0, 不予退款",
            "2, 3, -172800, -48, 66.67, 扣除首晚房费",
            "3, 3, -259200, -72, 50, 退款50%",
            "1, 1, -1, 0, 0, 不予退款",
            "2, 3, 0, 0, 66.67, 扣除首晚房费"
    })
    void appliesPolicyAtSecondBoundariesAndAfterCheckIn(int policy, int nights, long secondsBeforeCheckIn,
                                                       long expectedHours, String expectedAmount, String explanation) {
        RefundPolicyCalculator.Quote quote = calculateAt(order(policy, nights), secondsBeforeCheckIn);

        assertThat(quote.amount()).isEqualByComparingTo(expectedAmount);
        assertThat(quote.policyType()).isEqualTo(policy);
        assertThat(quote.hoursBeforeCheckIn()).isEqualTo(expectedHours);
        assertThat(quote.description()).contains(explanation);
        if (quote.amount().signum() > 0) {
            assertThat(quote.description()).contains("¥" + quote.amount().toPlainString());
        }
    }

    @ParameterizedTest
    @CsvSource({"1, 24", "2, 48", "3, 72"})
    void describesExactFullRefundThresholdAsInclusive(int policy, int hours) {
        RefundPolicyCalculator.Quote quote = calculateAt(order(policy, 1), hours * 3600L);

        assertThat(quote.amount()).isEqualByComparingTo("100");
        assertThat(quote.description()).contains("至少" + hours + "小时");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, 4})
    void usesNormalPolicyForMissingOrUnknownPolicy(Integer policy) {
        RefundPolicyCalculator.Quote quote = calculateAt(order(policy, 1), 36 * 3600L);

        assertThat(quote.policyType()).isEqualTo(2);
        assertThat(quote.amount()).isEqualByComparingTo("50");
        assertThat(quote.description()).contains("普通政策", "至少24小时且不足48小时");
    }

    @Test
    void usesNormalPolicyWhenHomestayIsMissing() {
        Order order = order(1, 1);
        order.setHomestay(null);

        RefundPolicyCalculator.Quote quote = calculateAt(order, 36 * 3600L);

        assertThat(quote.policyType()).isEqualTo(2);
        assertThat(quote.amount()).isEqualByComparingTo("50");
    }

    @Test
    void usesNormalPolicyWhenDeletedHomestayProxyCannotBeLoaded() {
        Homestay deletedHomestay = mock(Homestay.class);
        when(deletedHomestay.getCancelPolicyType()).thenThrow(new EntityNotFoundException("deleted"));
        Order order = order(1, 1);
        order.setHomestay(deletedHomestay);

        RefundPolicyCalculator.Quote quote = calculateAt(order, 36 * 3600L);

        assertThat(quote.policyType()).isEqualTo(2);
        assertThat(quote.amount()).isEqualByComparingTo("50");
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true", "true, true"})
    void returnsMissingDataQuoteWithoutReadingSnapshot(boolean missingDate, boolean missingAmount) {
        Order order = order(3, 3);
        if (missingDate) {
            order.setCheckInDate(null);
        }
        if (missingAmount) {
            order.setTotalAmount(null);
        }

        RefundPolicyCalculator.Quote quote = calculateAt(order, 48 * 3600L);

        assertThat(quote).isEqualTo(new RefundPolicyCalculator.Quote(BigDecimal.ZERO,
                "无法计算退款金额（缺少入住日期或订单金额）", 2, null));
        verifyNoInteractions(pricingService);
    }

    @Test
    void usesSnapshotPayableAmountInsteadOfOrderTotal() {
        when(pricingService.getPriceSnapshot(10L))
                .thenReturn(PricingResult.builder().payableAmount(new BigDecimal("70.01")).build());

        RefundPolicyCalculator.Quote quote = calculateAt(order(2, 1), 36 * 3600L);

        assertThat(quote.amount()).isEqualByComparingTo("35.01");
        assertThat(quote.description()).contains("¥35.01");
        verify(pricingService).getPriceSnapshot(10L);
    }

    @Test
    void fallsBackToOrderTotalWhenSnapshotIsMissing() {
        RefundPolicyCalculator.Quote quote = calculateAt(order(1, 1), 48 * 3600L);

        assertThat(quote.amount()).isEqualByComparingTo("100");
        verify(pricingService).getPriceSnapshot(10L);
    }

    @Test
    void fallsBackToOrderTotalWhenSnapshotPayableAmountIsMissing() {
        when(pricingService.getPriceSnapshot(10L)).thenReturn(PricingResult.builder().build());

        RefundPolicyCalculator.Quote quote = calculateAt(order(1, 1), 48 * 3600L);

        assertThat(quote.amount()).isEqualByComparingTo("100");
    }

    @Test
    void fallsBackToOrderTotalWhenSnapshotReadFails() {
        when(pricingService.getPriceSnapshot(10L)).thenThrow(new IllegalStateException("snapshot unavailable"));

        RefundPolicyCalculator.Quote quote = calculateAt(order(1, 1), 48 * 3600L);

        assertThat(quote.amount()).isEqualByComparingTo("100");
    }

    @Test
    void skipsSnapshotForOrderWithoutId() {
        Order order = order(1, 1);
        order.setId(null);

        RefundPolicyCalculator.Quote quote = calculateAt(order, 48 * 3600L);

        assertThat(quote.amount()).isEqualByComparingTo("100");
        verifyNoInteractions(pricingService);
    }

    @Test
    void keepsZeroSnapshotAmountInsteadOfFallingBack() {
        when(pricingService.getPriceSnapshot(10L))
                .thenReturn(PricingResult.builder().payableAmount(BigDecimal.ZERO).build());

        RefundPolicyCalculator.Quote quote = calculateAt(order(3, 1), 72 * 3600L);

        assertThat(quote.amount()).isEqualByComparingTo("0");
        assertThat(quote.description()).contains("全额退款 ¥0");
    }

    @Test
    void acceptsZeroOrderAmount() {
        Order order = order(2, 3);
        order.setTotalAmount(BigDecimal.ZERO);

        RefundPolicyCalculator.Quote quote = calculateAt(order, 12 * 3600L);

        assertThat(quote.amount()).isEqualByComparingTo("0");
        assertThat(quote.hoursBeforeCheckIn()).isEqualTo(12L);
        assertThat(quote.description()).contains("扣除首晚房费");
    }

    @Test
    void deductsRoundedAverageFirstNightForThreeNights() {
        Order order = order(2, 3);
        order.setTotalAmount(new BigDecimal("10.00"));

        RefundPolicyCalculator.Quote quote = calculateAt(order, 12 * 3600L);

        assertThat(quote.amount()).isEqualByComparingTo("6.67");
        assertThat(quote.description()).contains("¥6.67");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 1})
    void treatsMissingOrNonpositiveNightsAsSingleNightForLateCancellation(Integer nights) {
        RefundPolicyCalculator.Quote quote = calculateAt(order(2, nights), 12 * 3600L);

        assertThat(quote.amount()).isEqualByComparingTo("0");
        assertThat(quote.description()).contains("仅1晚", "不予退款");
    }

    @Test
    void usesFixedFourteenOClockRatherThanCurrentHomestayTime() {
        Order order = order(1, 1);
        order.getHomestay().setCheckInTime("23:00");

        RefundPolicyCalculator.Quote quote = calculateAt(order, 23 * 3600L);

        assertThat(quote.hoursBeforeCheckIn()).isEqualTo(23L);
        assertThat(quote.amount()).isEqualByComparingTo("0");
    }

    @Test
    void doesNotModifyOrder() {
        Order order = order(2, 3);
        Order original = order(2, 3);

        calculateAt(order, 12 * 3600L);

        assertThat(order).usingRecursiveComparison().isEqualTo(original);
    }

    private RefundPolicyCalculator.Quote calculateAt(Order order, long secondsBeforeCheckIn) {
        LocalDateTime now = CHECK_IN_DATE.atTime(14, 0).minusSeconds(secondsBeforeCheckIn);
        Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        return new RefundPolicyCalculator(pricingService, clock).calculate(order);
    }

    private Order order(Integer policy, Integer nights) {
        Homestay homestay = new Homestay();
        homestay.setId(20L);
        homestay.setCancelPolicyType(policy);
        return Order.builder()
                .id(10L)
                .homestay(homestay)
                .checkInDate(CHECK_IN_DATE)
                .nights(nights)
                .totalAmount(new BigDecimal("100"))
                .status("PAID")
                .paymentStatus(PaymentStatus.PAID)
                .refundAmount(new BigDecimal("12.34"))
                .remark("保留原始备注")
                .build();
    }
}
