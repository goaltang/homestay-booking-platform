package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.HomestayAuditLog;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.ResourceNotFoundException;
import com.homestay3.homestaybackend.model.HomestayStatus;
import com.homestay3.homestaybackend.repository.HomestayAuditLogRepository;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.service.HomestayCommandService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomestayAdminServiceImplTest {

    @Mock
    private HomestayRepository homestayRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HomestayAuditLogRepository homestayAuditLogRepository;

    @Mock
    private HomestayDtoAssembler homestayDtoAssembler;

    @Mock
    private HomestayMutationSupport homestayMutationSupport;

    @Mock
    private HomestaySpecificationSupport homestaySpecificationSupport;

    @Mock
    private HomestayCommandService homestayCommandService;

    @InjectMocks
    private HomestayAdminServiceImpl homestayAdminService;

    private User adminUser;
    private User hostUser;
    private Homestay activeHomestay;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setRole("ROLE_ADMIN");

        hostUser = new User();
        hostUser.setId(2L);
        hostUser.setUsername("host");
        hostUser.setRole("ROLE_HOST");

        activeHomestay = new Homestay();
        activeHomestay.setId(1L);
        activeHomestay.setTitle("测试民宿");
        activeHomestay.setPrice(new BigDecimal("200"));
        activeHomestay.setStatus(HomestayStatus.ACTIVE);
        activeHomestay.setOwner(hostUser);
        activeHomestay.setCreatedAt(LocalDateTime.now());
        activeHomestay.setUpdatedAt(LocalDateTime.now());
    }

    private void mockSecurityContext() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(adminUser.getUsername());

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByUsername(adminUser.getUsername())).thenReturn(Optional.of(adminUser));
    }

    @Test
    void forceDelistHomestaySuccess() {
        mockSecurityContext();
        when(homestayRepository.findById(1L)).thenReturn(Optional.of(activeHomestay));
        when(homestayRepository.save(any(Homestay.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(homestayAuditLogRepository.save(any(HomestayAuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        homestayAdminService.forceDelistHomestay(1L, "发布虚假信息", "多次被用户举报", "FALSE_INFO");

        ArgumentCaptor<Homestay> homestayCaptor = ArgumentCaptor.forClass(Homestay.class);
        verify(homestayRepository).save(homestayCaptor.capture());

        Homestay savedHomestay = homestayCaptor.getValue();
        assertEquals(HomestayStatus.INACTIVE, savedHomestay.getStatus());
        assertNotNull(savedHomestay.getUpdatedAt());
    }

    @Test
    void forceDelistHomestaySuccessRecordsAuditLog() {
        mockSecurityContext();
        String reason = "存在安全隐患";
        String notes = "经核实确实存在问题";
        String violationType = "SAFETY_ISSUE";

        when(homestayRepository.findById(1L)).thenReturn(Optional.of(activeHomestay));
        when(homestayRepository.save(any(Homestay.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(homestayAuditLogRepository.save(any(HomestayAuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        homestayAdminService.forceDelistHomestay(1L, reason, notes, violationType);

        ArgumentCaptor<HomestayAuditLog> auditLogCaptor = ArgumentCaptor.forClass(HomestayAuditLog.class);
        verify(homestayAuditLogRepository).save(auditLogCaptor.capture());

        HomestayAuditLog savedLog = auditLogCaptor.getValue();
        assertEquals(activeHomestay, savedLog.getHomestay());
        assertEquals(adminUser, savedLog.getReviewer());
        assertEquals(HomestayStatus.ACTIVE, savedLog.getOldStatus());
        assertEquals(HomestayStatus.INACTIVE, savedLog.getNewStatus());
        assertEquals(HomestayAuditLog.AuditActionType.DEACTIVATE, savedLog.getActionType());
        assertEquals(reason, savedLog.getReviewReason());
        assertEquals(notes, savedLog.getReviewNotes());
    }

    @Test
    void forceDelistHomestayHomestayNotFound() {
        when(homestayRepository.findById(999L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () ->
                homestayAdminService.forceDelistHomestay(999L, "违规", null, "OTHER"));

        assertTrue(exception.getMessage().contains("房源不存在"));
    }

    @Test
    void forceDelistHomestayAdminNotFoundSkipsAuditLog() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("nonexistent");

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(homestayRepository.findById(1L)).thenReturn(Optional.of(activeHomestay));
        when(homestayRepository.save(any(Homestay.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                homestayAdminService.forceDelistHomestay(1L, "违规", null, "OTHER"));

        ArgumentCaptor<Homestay> homestayCaptor = ArgumentCaptor.forClass(Homestay.class);
        verify(homestayRepository).save(homestayCaptor.capture());
        assertEquals(HomestayStatus.INACTIVE, homestayCaptor.getValue().getStatus());
        verify(homestayAuditLogRepository, never()).save(any());
    }

    @Test
    void forceDelistHomestayAlreadyInactive() {
        mockSecurityContext();
        activeHomestay.setStatus(HomestayStatus.INACTIVE);

        when(homestayRepository.findById(1L)).thenReturn(Optional.of(activeHomestay));
        when(homestayRepository.save(any(Homestay.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(homestayAuditLogRepository.save(any(HomestayAuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        homestayAdminService.forceDelistHomestay(1L, "再次下架", null, "OTHER");

        ArgumentCaptor<Homestay> homestayCaptor = ArgumentCaptor.forClass(Homestay.class);
        verify(homestayRepository).save(homestayCaptor.capture());
        assertEquals(HomestayStatus.INACTIVE, homestayCaptor.getValue().getStatus());
        verify(homestayAuditLogRepository, times(1)).save(any());
    }

    @Test
    void forceDelistHomestayWithNullNotes() {
        mockSecurityContext();

        when(homestayRepository.findById(1L)).thenReturn(Optional.of(activeHomestay));
        when(homestayRepository.save(any(Homestay.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(homestayAuditLogRepository.save(any(HomestayAuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        homestayAdminService.forceDelistHomestay(1L, "违规内容", null, "PROHIBITED_CONTENT");

        ArgumentCaptor<HomestayAuditLog> auditLogCaptor = ArgumentCaptor.forClass(HomestayAuditLog.class);
        verify(homestayAuditLogRepository).save(auditLogCaptor.capture());

        HomestayAuditLog savedLog = auditLogCaptor.getValue();
        assertTrue(savedLog.getReviewNotes().contains("PROHIBITED_CONTENT"));
    }
}
