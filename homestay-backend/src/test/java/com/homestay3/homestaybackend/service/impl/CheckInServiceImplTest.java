package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.CheckInCredentialDTO;
import com.homestay3.homestaybackend.dto.CheckInDTO;
import com.homestay3.homestaybackend.entity.CheckInRecord;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.AccessDeniedException;
import com.homestay3.homestaybackend.model.OrderStatus;
import com.homestay3.homestaybackend.repository.CheckInRecordRepository;
import com.homestay3.homestaybackend.repository.OrderRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.NotificationService;
import com.homestay3.homestaybackend.service.SystemConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CheckInServiceImpl 单元测试
 * 测试入住相关业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckInServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CheckInRecordRepository checkInRecordRepository;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private NotificationService notificationService;

    @Spy
    private OrderStatusUpdater orderStatusUpdater = new OrderStatusUpdater();

    @InjectMocks
    private CheckInServiceImpl checkInService;

    private User currentUser;
    private User hostUser;
    private Homestay homestay;
    private Order testOrder;
    private CheckInRecord checkInRecord;

    @BeforeEach
    void setUp() {
        // 设置当前用户
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("testuser");

        hostUser = new User();
        hostUser.setId(2L);
        hostUser.setUsername("hostuser");

        // 设置SecurityContext
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("testuser");
        SecurityContextHolder.setContext(securityContext);

        // mock userRepository
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(currentUser));

        // 创建测试民宿（房东为 hostUser，非当前用户）
        homestay = new Homestay();
        homestay.setId(100L);
        homestay.setOwner(hostUser);

        // 创建测试订单
        testOrder = new Order();
        testOrder.setId(1L);
        testOrder.setOrderNumber("ORDER202408080001");
        testOrder.setStatus(OrderStatus.READY_FOR_CHECKIN.name());
        testOrder.setHomestay(homestay);
        testOrder.setGuest(currentUser);
        testOrder.setCheckInDate(LocalDate.now());
        testOrder.setCheckOutDate(LocalDate.now().plusDays(3));

        // 创建测试入住记录
        checkInRecord = new CheckInRecord();
        checkInRecord.setId(10L);
        checkInRecord.setOrderId(1L);
        checkInRecord.setCheckInCode("123456");
        checkInRecord.setStatus("ACTIVE");
        checkInRecord.setValidFrom(LocalDateTime.now().minusHours(1));
        checkInRecord.setValidUntil(LocalDateTime.now().plusDays(1));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ========== selfCheckIn 测试 ==========

    @Test
    void selfCheckIn_Success() {
        // given
        when(checkInRecordRepository.findByCheckInCode("123456")).thenReturn(Optional.of(checkInRecord));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // when
        CheckInDTO result = checkInService.selfCheckIn("123456");

        // then
        assertNotNull(result);
        assertEquals("SELF_SERVICE", result.getOperatorType());
        // 订单被标记为已入住
        assertEquals(OrderStatus.CHECKED_IN.name(), testOrder.getStatus());
        assertNotNull(testOrder.getCheckedInAt());
        // 入住记录更新
        assertEquals("SELF_SERVICE", checkInRecord.getCheckInOperatorType());
        assertNull(checkInRecord.getCheckInOperatorId());
        assertNotNull(checkInRecord.getCheckedInAt());
        verify(orderRepository).save(testOrder);
        verify(checkInRecordRepository).save(checkInRecord);
    }

    @Test
    void selfCheckIn_InvalidCode() {
        // given
        when(checkInRecordRepository.findByCheckInCode("invalid")).thenReturn(Optional.empty());

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                checkInService.selfCheckIn("invalid"));
        assertTrue(exception.getMessage().contains("无效的入住码"));
    }

    @Test
    void selfCheckIn_ExpiredCode() {
        // given: 入住码已过期
        checkInRecord.setValidUntil(LocalDateTime.now().minusDays(1));
        when(checkInRecordRepository.findByCheckInCode("123456")).thenReturn(Optional.of(checkInRecord));

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                checkInService.selfCheckIn("123456"));
        assertTrue(exception.getMessage().contains("已过期"));
    }

    // ========== validateCheckInCode 测试 ==========

    @Test
    void validateCheckInCode_Valid() {
        // given
        when(checkInRecordRepository.findByCheckInCode("123456")).thenReturn(Optional.of(checkInRecord));

        // when & then
        assertTrue(checkInService.validateCheckInCode(1L, "123456"));
    }

    @Test
    void validateCheckInCode_OrderMismatch() {
        // given: 入住记录存在但订单ID不匹配
        when(checkInRecordRepository.findByCheckInCode("123456")).thenReturn(Optional.of(checkInRecord));

        // when & then
        assertFalse(checkInService.validateCheckInCode(99L, "123456"));
    }

    // ========== prepareCheckIn 测试 ==========

    @Test
    void prepareCheckIn_NotOwner() {
        // given: 当前用户不是房东
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        CheckInCredentialDTO credentialDTO = new CheckInCredentialDTO();

        // when & then
        assertThrows(AccessDeniedException.class, () ->
                checkInService.prepareCheckIn(1L, credentialDTO));
    }

    // ========== cancelPreparation 测试 ==========

    @Test
    void cancelPreparation_NotReadyStatus() {
        // given: 当前用户为房东，但订单状态不是 READY_FOR_CHECKIN
        Homestay myHomestay = new Homestay();
        myHomestay.setId(100L);
        myHomestay.setOwner(currentUser);
        testOrder.setHomestay(myHomestay);
        testOrder.setStatus(OrderStatus.PAID.name());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // when & then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                checkInService.cancelPreparation(1L));
        assertTrue(exception.getMessage().contains("不是准备入住状态"));
    }
}
