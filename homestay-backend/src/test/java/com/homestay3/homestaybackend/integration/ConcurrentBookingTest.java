package com.homestay3.homestaybackend.integration;

import com.homestay3.homestaybackend.dto.OrderDTO;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.HomestayAvailability;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.model.HomestayStatus;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.repository.HomestayAvailabilityRepository;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单并发超卖防护测试
 *
 * 测试目标：验证同一房源同一日期被多个线程同时预订时，只有 1 个订单能成功。
 *
 * 由于 H2 测试环境没有 Redis，本测试使用 @MockBean 替换 RedissonClient，
 * 让 Redis 分布式锁总是获取成功，从而重点测试数据库层面的并发控制
 *（existsOverlappingBooking 查询 + availability 唯一约束）。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "elasticsearch.enabled=false")
class ConcurrentBookingTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private HomestayRepository homestayRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private HomestayAvailabilityRepository availabilityRepository;

    private Homestay testHomestay;
    private User guestUser;
    private User hostUser;

    @BeforeEach
    void setUp() {
        createTestData();
    }

    /**
     * 创建测试数据：一个房东、一个客人、一个自动确认房源
     */
    private void createTestData() {
        long ts = System.currentTimeMillis();

        hostUser = new User();
        hostUser.setUsername("host_concurrent_" + ts);
        hostUser.setEmail("host_" + ts + "@test.com");
        hostUser.setPassword("password");
        hostUser.setRole("ROLE_HOST");
        hostUser = userRepository.save(hostUser);

        guestUser = new User();
        guestUser.setUsername("guest_concurrent_" + ts);
        guestUser.setEmail("guest_" + ts + "@test.com");
        guestUser.setPassword("password");
        guestUser.setRole("ROLE_USER");
        guestUser = userRepository.save(guestUser);

        testHomestay = new Homestay();
        testHomestay.setTitle("并发测试房源");
        testHomestay.setType("公寓");
        testHomestay.setPrice(new BigDecimal("200.00"));
        testHomestay.setMaxGuests(4);
        testHomestay.setMinNights(1);
        testHomestay.setStatus(HomestayStatus.ACTIVE);
        testHomestay.setOwner(hostUser);
        testHomestay.setAutoConfirm(true);
        testHomestay.setAddressDetail("测试地址");
        testHomestay.setProvinceCode("440000");
        testHomestay.setCityCode("440300");
        testHomestay.setProvinceText("广东省");
        testHomestay.setCityText("深圳市");
        testHomestay.setDistrictText("南山区");
        testHomestay = homestayRepository.save(testHomestay);
    }

    private void setMockAuthentication(User user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, Collections.singletonList(new SimpleGrantedAuthority(user.getRole())));
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    /**
     * 核心测试：10 个线程并发预订同一房源的同一日期，验证只有 1 个成功
     */
    @Test
    void testConcurrentBooking_shouldAllowOnlyOneSuccess() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Exception> unexpectedExceptions = Collections.synchronizedList(new ArrayList<>());

        LocalDate checkIn = LocalDate.now().plusDays(30);
        LocalDate checkOut = checkIn.plusDays(2);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    // SecurityContext 是 ThreadLocal，每个子线程需独立设置
                    setMockAuthentication(guestUser);

                    OrderDTO orderDTO = OrderDTO.builder()
                            .homestayId(testHomestay.getId())
                            .checkInDate(checkIn)
                            .checkOutDate(checkOut)
                            .guestCount(1)
                            .guestPhone("1380000000" + index)
                            .build();

                    orderService.createOrder(orderDTO);
                    successCount.incrementAndGet();

                } catch (IllegalArgumentException | IllegalStateException e) {
                    String msg = e.getMessage();
                    if (msg != null && (msg.contains("已被预订") || msg.contains("冲突") || msg.contains("库存不足"))) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedExceptions.add(e);
                    }
                } catch (RuntimeException e) {
                    String msg = e.getMessage();
                    if (msg != null && (msg.contains("已被预订") || msg.contains("冲突"))) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedExceptions.add(e);
                    }
                } catch (Exception e) {
                    unexpectedExceptions.add(e);
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        // 统一起跑
        startLatch.countDown();
        boolean completed = completeLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "所有线程应在 30 秒内完成");

        // 断言 1：只有 1 个订单成功
        assertEquals(1, successCount.get(),
                "并发预订同一日期，应只有 1 个订单成功，但实际成功了 " + successCount.get() + " 个。" +
                "如果大于 1，说明存在超卖漏洞。");

        // 断言 2：其余均为冲突
        assertEquals(threadCount - 1, conflictCount.get(),
                "其余 " + (threadCount - 1) + " 个请求应因日期冲突失败");

        // 断言 3：无未预期异常
        assertTrue(unexpectedExceptions.isEmpty(),
                "不应有未预期的异常: " + unexpectedExceptions);

        // 断言 4：数据库中确实只有 1 个订单
        long orderCount = orderRepository.countByHomestayId(testHomestay.getId());
        assertEquals(1, orderCount,
                "数据库中该房源的订单应为 1 个");

        // 断言 5：availability 表被正确标记为 BOOKED
        List<HomestayAvailability> records = availabilityRepository
                .findByHomestayIdAndDateBetween(testHomestay.getId(), checkIn, checkOut.minusDays(1));

        assertEquals(2, records.size(), "应有两条 availability 记录（两晚）");
        records.forEach(record ->
            assertEquals(HomestayAvailability.AvailabilityStatus.BOOKED, record.getStatus(),
                    "availability 状态应为 BOOKED")
        );
    }

    /**
     * 补充测试：串行连续预订同一日期（基准测试，验证单线程逻辑正确）
     */
    @Test
    void testSequentialBooking_shouldRejectSecondRequest() {
        setMockAuthentication(guestUser);

        LocalDate checkIn = LocalDate.now().plusDays(31);
        LocalDate checkOut = checkIn.plusDays(1);

        OrderDTO orderDTO = OrderDTO.builder()
                .homestayId(testHomestay.getId())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .guestCount(1)
                .guestPhone("13900000001")
                .build();

        // 第一次预订成功
        var firstOrder = orderService.createOrder(orderDTO);
        assertNotNull(firstOrder);
        assertEquals(OrderStatus.CONFIRMED.name(), firstOrder.getStatus());

        // 第二次预订同一日期应失败
        assertThrows(RuntimeException.class, () -> orderService.createOrder(orderDTO));
    }
}
