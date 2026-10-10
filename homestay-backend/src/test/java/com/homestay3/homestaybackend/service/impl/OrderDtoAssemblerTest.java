package com.homestay3.homestaybackend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.Review;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.model.PaymentStatus;
import com.homestay3.homestaybackend.model.RefundType;
import com.homestay3.homestaybackend.repository.ReviewRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.SystemConfigService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderDtoAssemblerTest {

    @Mock
    private ReviewRepository reviews;
    @Mock
    private UserRepository users;
    @Mock
    private ObjectProvider<SystemConfigService> configProvider;
    @Mock
    private SystemConfigService config;

    private final ObjectMapper jsonMapper = new ObjectMapper().findAndRegisterModules();
    private OrderDtoAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new OrderDtoAssembler(reviews, users, configProvider);
        lenient().when(configProvider.getObject()).thenReturn(config);
        lenient().when(config.getConfigValue(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
    }

    @ParameterizedTest
    @EnumSource(Projection.class)
    void projectionsKeepFeeBasisAndReviewedJsonWithoutExposingCheckInSecrets(Projection projection) {
        Order order = order();
        order.setCheckInCode("private-code");
        order.setDoorPassword("private-password");
        order.setCheckedInAt(LocalDateTime.of(2026, 10, 10, 15, 0));
        when(config.getConfigValue("pricing.cleaning_fee", "0.1")).thenReturn("0.2");
        when(config.getConfigValue("pricing.service_fee", "0.15")).thenReturn("0.08");

        OrderDTO result = project(projection, order);
        JsonNode json = jsonMapper.valueToTree(result);

        assertThat(result.getCleaningFee()).isEqualByComparingTo("39.80");
        assertThat(result.getServiceFee()).isEqualByComparingTo("47.76");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("620.66");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("620.66");
        assertThat(result.getGuestName()).isEqualTo("guest-account");
        assertThat(result.getHostName()).isEqualTo("房东昵称");
        assertThat(result.getImageUrl()).isEqualTo(projection == Projection.LIFECYCLE ? "/cover.jpg" : null);
        assertThat(json.get("reviewed").isBoolean()).isTrue();
        assertThat(json.get("reviewed").booleanValue()).isFalse();
        assertThat(json.has("isReviewed")).isFalse();
        for (String secret : new String[]{"checkInCode", "doorPassword", "checkedInAt", "priceSnapshot"}) {
            assertThat(json.hasNonNull(secret)).as(secret).isFalse();
        }
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @EnumSource(value = Projection.class, names = {"LIFECYCLE", "ADMIN"})
    void completedOrderRetainsReviewAndRefundActors(Projection projection) {
        Order order = completedRefundOrder();
        Review review = Review.builder().id(9L).order(order).user(order.getGuest())
                .homestay(order.getHomestay()).rating(5).content("入住顺利")
                .response("欢迎再来").isPublic(true).build();
        when(reviews.existsByOrder(order)).thenReturn(true);
        when(reviews.findByOrder(order)).thenReturn(Optional.of(review));
        User initiator = new User();
        initiator.setUsername("requester-account");
        User processor = new User();
        processor.setUsername("processor-account");
        processor.setNickname("审核员昵称");
        when(users.findById(17L)).thenReturn(Optional.of(initiator));
        when(users.findById(18L)).thenReturn(Optional.of(processor));

        OrderDTO result = project(projection, order);

        assertThat(result.isReviewed()).isTrue();
        assertThat(jsonMapper.valueToTree(result).get("reviewed").booleanValue()).isTrue();
        assertThat(result.getReview().getContent()).isEqualTo("入住顺利");
        assertThat(result.getReview().getResponse()).isEqualTo("欢迎再来");
        assertThat(result.getReview().getOrderId()).isEqualTo(order.getId());
        assertThat(result.getRefundAmount()).isEqualByComparingTo("123.45");
        assertThat(result.getRefundInitiatedByName()).isEqualTo("requester-account");
        assertThat(result.getRefundProcessedByName()).isEqualTo("审核员昵称");
        assertThat(result.getCompletedAt()).isEqualTo(order.getCompletedAt());
    }

    @Test
    void paymentProjectionDoesNotQueryReviewsOrActorsAndKeepsItsCompactResponse() {
        OrderDTO result = assembler.toPaymentResult(completedRefundOrder());

        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED.name());
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.PAID.name());
        assertThat(result.getTotalAmount()).isEqualByComparingTo("620.66");
        assertThat(result.isReviewed()).isFalse();
        assertThat(result.getReview()).isNull();
        assertThat(result.getRefundAmount()).isNull();
        assertThat(result.getRefundInitiatedBy()).isNull();
        assertThat(result.getRefundProcessedByName()).isNull();
        assertThat(result.getCompletedAt()).isNull();
        verifyNoInteractions(reviews, users);
    }

    @ParameterizedTest
    @EnumSource(value = Projection.class, names = {"LIFECYCLE", "ADMIN"})
    void nonCompletedOrderKeepsReviewFlagWithoutLoadingReviewDetails(Projection projection) {
        Order order = order();
        when(reviews.existsByOrder(order)).thenReturn(true);

        OrderDTO result = project(projection, order);

        assertThat(result.isReviewed()).isTrue();
        assertThat(result.getReview()).isNull();
        verify(reviews, never()).findByOrder(any());
    }

    @ParameterizedTest
    @EnumSource(Projection.class)
    void absentAssociationsAndMalformedFeeConfigurationUseExistingFallbacks(Projection projection) {
        Order order = order();
        order.setHomestay(null);
        order.setGuest(null);
        when(config.getConfigValue("pricing.cleaning_fee", "0.1")).thenReturn("bad-rate");
        when(config.getConfigValue("pricing.service_fee", "0.15")).thenReturn("bad-rate");

        OrderDTO result = project(projection, order);

        assertThat(result.getHomestayTitle()).isNull();
        assertThat(result.getHostId()).isNull();
        assertThat(result.getGuestName()).isNull();
        assertThat(result.getCleaningFee()).isEqualByComparingTo("19.90");
        assertThat(result.getServiceFee()).isEqualByComparingTo("89.55");
        assertThat(result.getTotalAmount()).isEqualByComparingTo("620.66");
    }

    @Test
    void adminProjectionRetainsDeletedAssociationToleranceWithoutChangingOtherEntrypoints() {
        Order order = completedRefundOrder();
        Homestay deletedHomestay = mock(Homestay.class);
        User deletedGuest = mock(User.class);
        when(deletedHomestay.getId()).thenReturn(4L);
        when(deletedHomestay.getOwner()).thenThrow(new EntityNotFoundException("deleted homestay"));
        when(deletedHomestay.getTitle()).thenThrow(new EntityNotFoundException("deleted homestay"));
        when(deletedGuest.getNickname()).thenThrow(new EntityNotFoundException("deleted guest"));
        order.setHomestay(deletedHomestay);
        order.setGuest(deletedGuest);

        OrderDTO result = assembler.toAdminListItem(order);

        assertThat(result.getHomestayId()).isEqualTo(4L);
        assertThat(result.getHomestayTitle()).isNull();
        assertThat(result.getHostName()).isNull();
        assertThat(result.getGuestId()).isNull();
        assertThat(result.getTotalAmount()).isEqualByComparingTo("620.66");
        assertThat(result.getRefundAmount()).isEqualByComparingTo("123.45");
        assertThatThrownBy(() -> assembler.toLifecycleResult(order)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> assembler.toPaymentResult(order)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void nullOrderReturnsNullWithoutLookingUpRelatedData() {
        assertThat(assembler.toLifecycleResult(null)).isNull();
        assertThat(assembler.toAdminListItem(null)).isNull();
        assertThat(assembler.toPaymentResult(null)).isNull();
        verifyNoInteractions(reviews, users, config);
    }

    private OrderDTO project(Projection projection, Order order) {
        return switch (projection) {
            case LIFECYCLE -> assembler.toLifecycleResult(order);
            case ADMIN -> assembler.toAdminListItem(order);
            case PAYMENT -> assembler.toPaymentResult(order);
        };
    }

    private Order completedRefundOrder() {
        Order order = order();
        order.setStatus(OrderStatus.COMPLETED.name());
        order.setCompletedAt(LocalDateTime.of(2026, 10, 13, 11, 0));
        order.setRefundType(RefundType.USER_REQUESTED);
        order.setRefundReason("退款记录");
        order.setRefundAmount(new BigDecimal("123.45"));
        order.setRefundInitiatedBy(17L);
        order.setRefundProcessedBy(18L);
        return order;
    }

    private Order order() {
        User guest = new User();
        guest.setId(2L);
        guest.setUsername("guest-account");
        User host = new User();
        host.setId(3L);
        host.setNickname("房东昵称");
        Homestay homestay = new Homestay();
        homestay.setId(4L);
        homestay.setTitle("边界测试房源");
        homestay.setOwner(host);
        homestay.setCoverImage("/cover.jpg");
        return Order.builder().id(1L).orderNumber("DTO-CONTRACT-1").homestay(homestay).guest(guest)
                .checkInDate(LocalDate.of(2026, 10, 10)).checkOutDate(LocalDate.of(2026, 10, 13))
                .price(new BigDecimal("199.00")).nights(3).totalAmount(new BigDecimal("620.66"))
                .status(OrderStatus.CONFIRMED.name()).paymentStatus(PaymentStatus.PAID).build();
    }

    private enum Projection { LIFECYCLE, ADMIN, PAYMENT }
}
