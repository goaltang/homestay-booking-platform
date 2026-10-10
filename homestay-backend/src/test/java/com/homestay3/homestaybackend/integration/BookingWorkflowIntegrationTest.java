package com.homestay3.homestaybackend.integration;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.Review;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import com.homestay3.homestaybackend.model.HomestayStatus;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.model.PaymentStatus;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.ReviewRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.OrderService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
public class BookingWorkflowIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private HomestayRepository homestayRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private EntityManager entityManager;

    private Homestay autoConfirmHomestay;
    private Homestay manualConfirmHomestay;
    private User guestUser;
    private User hostUser;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    public void setUp() {
        long timestamp = System.currentTimeMillis();
        // 创建测试用户 (预订者)
        guestUser = new User();
        guestUser.setUsername("guest_" + timestamp);
        guestUser.setEmail("guest_" + timestamp + "@test.com");
        guestUser.setPassword("password");
        guestUser.setRole("ROLE_USER");
        guestUser = userRepository.save(guestUser);

        // 创建房东用户
        hostUser = new User();
        hostUser.setUsername("host_" + timestamp);
        hostUser.setEmail("host_" + timestamp + "@test.com");
        hostUser.setPassword("password");
        hostUser.setRole("ROLE_HOST");
        hostUser = userRepository.save(hostUser);

        // 创建自动确认房源
        autoConfirmHomestay = new Homestay();
        autoConfirmHomestay.setTitle("自动确认房源");
        autoConfirmHomestay.setType("公寓");
        autoConfirmHomestay.setPrice(new BigDecimal("100.00"));
        autoConfirmHomestay.setMaxGuests(4);
        autoConfirmHomestay.setMinNights(1);
        autoConfirmHomestay.setStatus(HomestayStatus.ACTIVE);
        autoConfirmHomestay.setOwner(hostUser);
        autoConfirmHomestay.setAutoConfirm(true);
        autoConfirmHomestay.setAddressDetail("测试地址");
        autoConfirmHomestay.setProvinceCode("440000");
        autoConfirmHomestay.setCityCode("440300");
        autoConfirmHomestay.setProvinceText("广东省");
        autoConfirmHomestay.setCityText("深圳市");
        autoConfirmHomestay.setDistrictText("南山区");
        autoConfirmHomestay = homestayRepository.save(autoConfirmHomestay);

        // 创建手动确认房源
        manualConfirmHomestay = new Homestay();
        manualConfirmHomestay.setTitle("手动确认房源");
        manualConfirmHomestay.setType("别墅");
        manualConfirmHomestay.setPrice(new BigDecimal("500.00"));
        manualConfirmHomestay.setMaxGuests(8);
        manualConfirmHomestay.setMinNights(2);
        manualConfirmHomestay.setStatus(HomestayStatus.ACTIVE);
        manualConfirmHomestay.setOwner(hostUser);
        manualConfirmHomestay.setAutoConfirm(false);
        manualConfirmHomestay.setAddressDetail("测试地址2");
        manualConfirmHomestay.setProvinceCode("440000");
        manualConfirmHomestay.setCityCode("440300");
        manualConfirmHomestay.setProvinceText("广东省");
        manualConfirmHomestay.setCityText("深圳市");
        manualConfirmHomestay.setDistrictText("福田区");
        manualConfirmHomestay = homestayRepository.save(manualConfirmHomestay);
    }

    private void setMockAuthentication(User user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, Collections.singletonList(new SimpleGrantedAuthority(user.getRole())));
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    @Test
    public void testAutoConfirmBookingWorkflow() {
        setMockAuthentication(guestUser);

        LocalDate checkIn = LocalDate.now().plusDays(5);
        LocalDate checkOut = checkIn.plusDays(2);

        // 1. 预览订单
        OrderDTO previewDTO = OrderDTO.builder()
                .homestayId(autoConfirmHomestay.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .guestCount(2)
                .build();
        OrderDTO preview = orderService.createOrderPreview(previewDTO);
        assertNotNull(preview);
        // 重新计算: base=200, cleaning=10, service=30, total=240.
        assertEquals(0, new BigDecimal("240.00").compareTo(preview.getTotalAmount()));

        // 2. 创建订单
        previewDTO.setGuestPhone("13800000000");
        OrderDTO createdOrder = orderService.createOrder(previewDTO);
        assertNotNull(createdOrder);
        assertEquals(OrderStatus.CONFIRMED.name(), createdOrder.getStatus());
        assertEquals(PaymentStatus.UNPAID.name(), createdOrder.getPaymentStatus());

        // 3. 支付订单
        OrderDTO paidOrder = orderService.payOrder(createdOrder.getId());
        assertEquals(OrderStatus.PAID.name(), paidOrder.getStatus());
        assertEquals(PaymentStatus.PAID.name(), paidOrder.getPaymentStatus());
    }

    @Test
    public void testOrderResponseContractsAcrossPaymentDetailAndAdminList() {
        setMockAuthentication(guestUser);
        autoConfirmHomestay.setCoverImage("/integration-cover.jpg");
        homestayRepository.save(autoConfirmHomestay);
        LocalDate checkIn = LocalDate.now().plusDays(25);
        OrderDTO created = orderService.createOrder(OrderDTO.builder()
                .homestayId(autoConfirmHomestay.getId()).checkInDate(checkIn).checkOutDate(checkIn.plusDays(2))
                .guestCount(2).guestPhone("13800000000").build());
        assertEquals("/integration-cover.jpg", created.getImageUrl());

        // 真支付服务的测试支付路径仍返回精简投影；不触发外部支付网关。
        OrderDTO paid = orderService.payOrder(created.getId());
        assertEquals(PaymentStatus.PAID.name(), paid.getPaymentStatus());
        assertNull(paid.getImageUrl());
        assertFalse(paid.isReviewed());
        assertNull(paid.getRefundAmount());
        assertEquals(0, created.getTotalAmount().compareTo(paid.getTotalAmount()));
        assertEquals(0, created.getCleaningFee().compareTo(paid.getCleaningFee()));
        assertEquals(0, created.getServiceFee().compareTo(paid.getServiceFee()));

        // 建立完成评价与历史退款信息，再清空一级缓存，覆盖数据库关联的实际读取。
        Order stored = orderRepository.findById(created.getId()).orElseThrow();
        stored.setStatus(OrderStatus.COMPLETED.name());
        stored.setCompletedAt(LocalDateTime.now());
        stored.setRefundAmount(new BigDecimal("12.34"));
        stored.setRefundInitiatedBy(guestUser.getId());
        stored.setRefundProcessedBy(hostUser.getId());
        stored.setCheckInCode("private-code");
        stored.setDoorPassword("private-password");
        orderRepository.save(stored);
        reviewRepository.save(Review.builder().order(stored).user(guestUser).homestay(autoConfirmHomestay)
                .rating(5).content("共享订单响应回归评价").build());
        entityManager.flush();
        entityManager.clear();

        OrderDTO detail = orderService.getOrderById(created.getId());
        assertTrue(detail.isReviewed());
        assertEquals("共享订单响应回归评价", detail.getReview().getContent());
        assertEquals("/integration-cover.jpg", detail.getImageUrl());
        assertEquals(0, new BigDecimal("12.34").compareTo(detail.getRefundAmount()));
        assertEquals(hostUser.getUsername(), detail.getRefundProcessedByName());
        assertNull(detail.getCheckInCode());
        assertNull(detail.getDoorPassword());

        User admin = new User();
        admin.setUsername("dto_admin_" + UUID.randomUUID());
        admin.setEmail(admin.getUsername() + "@test.com");
        admin.setPassword("password");
        admin.setRole("ROLE_ADMIN");
        admin = userRepository.save(admin);
        setMockAuthentication(admin);
        Page<OrderDTO> page = orderService.getAdminOrders(PageRequest.of(0, 10), created.getOrderNumber(),
                null, null, null, null, null, null, null, null, null, null);
        assertEquals(1, page.getTotalElements());
        OrderDTO listed = page.getContent().get(0);
        assertTrue(listed.isReviewed());
        assertEquals(detail.getReview().getContent(), listed.getReview().getContent());
        assertEquals(0, detail.getTotalAmount().compareTo(listed.getTotalAmount()));
        assertEquals(0, detail.getCleaningFee().compareTo(listed.getCleaningFee()));
        assertEquals(0, detail.getServiceFee().compareTo(listed.getServiceFee()));
        assertEquals(0, detail.getRefundAmount().compareTo(listed.getRefundAmount()));
        assertNull(listed.getImageUrl());

        // 无关客人依然无法通过详情服务绕过订单访问权限。
        User unrelatedGuest = new User();
        unrelatedGuest.setUsername("dto_outsider_" + UUID.randomUUID());
        unrelatedGuest.setEmail(unrelatedGuest.getUsername() + "@test.com");
        unrelatedGuest.setPassword("password");
        unrelatedGuest.setRole("ROLE_USER");
        unrelatedGuest = userRepository.save(unrelatedGuest);
        setMockAuthentication(unrelatedGuest);
        assertThrows(AccessDeniedException.class, () -> orderService.getOrderById(created.getId()));
    }

    @Test
    public void testManualConfirmBookingWorkflow() {
        // 1. 客人创建订单
        setMockAuthentication(guestUser);
        LocalDate checkIn = LocalDate.now().plusDays(10);
        LocalDate checkOut = checkIn.plusDays(3);

        OrderDTO orderDTO = OrderDTO.builder()
                .homestayId(manualConfirmHomestay.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .guestCount(4)
                .guestPhone("13900000000")
                .build();

        OrderDTO createdOrder = orderService.createOrder(orderDTO);
        assertEquals(OrderStatus.PENDING.name(), createdOrder.getStatus());

        // 2. 房东确认订单
        setMockAuthentication(hostUser);
        OrderDTO confirmedOrder = orderService.updateOrderStatus(createdOrder.getId(), OrderStatus.CONFIRMED.name());
        assertEquals(OrderStatus.CONFIRMED.name(), confirmedOrder.getStatus());

        // 3. 客人支付
        setMockAuthentication(guestUser);
        OrderDTO paidOrder = orderService.payOrder(createdOrder.getId());
        assertEquals(OrderStatus.PAID.name(), paidOrder.getStatus());
    }

    @Test
    public void testBookingConflict() {
        setMockAuthentication(guestUser);
        LocalDate checkIn = LocalDate.now().plusDays(15);
        LocalDate checkOut = checkIn.plusDays(2);

        OrderDTO orderDTO = OrderDTO.builder()
                .homestayId(autoConfirmHomestay.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .guestCount(1)
                .guestPhone("13700000000")
                .build();

        // 第一次预定成功
        orderService.createOrder(orderDTO);

        // 第二次预定相同日期应失败
        assertThrows(RuntimeException.class, () -> {
            orderService.createOrder(orderDTO);
        });
    }
}
