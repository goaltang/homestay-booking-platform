package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.AuthRequest;
import com.homestay3.homestaybackend.dto.AuthResponse;
import com.homestay3.homestaybackend.dto.RegisterRequest;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.model.UserRole;
import com.homestay3.homestaybackend.model.VerificationStatus;
import com.homestay3.homestaybackend.dto.UserDTO;
import com.homestay3.homestaybackend.mapper.UserMapper;
import com.homestay3.homestaybackend.repository.AdminRepository;
import com.homestay3.homestaybackend.repository.CouponTemplateRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.security.JwtTokenProvider;
import com.homestay3.homestaybackend.service.CouponService;
import com.homestay3.homestaybackend.service.EmailService;
import com.homestay3.homestaybackend.service.NotificationService;
import com.homestay3.homestaybackend.service.ReferralService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthService 单元测试
 * 测试认证核心业务逻辑：注册、登录、密码重置等
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    
    @Mock
    private AuthenticationManager authenticationManager;
    
    @Mock
    private EmailService emailService;
    
    @Mock
    private NotificationService notificationService;
    
    @Mock
    private AdminRepository adminRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    @Mock
    private CouponService couponService;

    @Mock
    private ReferralService referralService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;
    private RegisterRequest registerRequest;
    private AuthRequest authRequest;

    @BeforeEach
    void setUp() {
        // mock userMapper
        when(userMapper.toDTO(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            UserDTO dto = new UserDTO();
            dto.setId(u.getId());
            dto.setUsername(u.getUsername());
            dto.setEmail(u.getEmail());
            dto.setPhone(u.getPhone());
            dto.setRole(u.getRole());
            return dto;
        });

        // 设置测试用户
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encodedPassword");
        testUser.setPhone("13800138000");
        testUser.setRole(UserRole.ROLE_USER.name());
        testUser.setEnabled(true);
        testUser.setVerificationStatus(VerificationStatus.UNVERIFIED);
        
        // 设置注册请求
        registerRequest = RegisterRequest.builder()
                .username("newuser")
                .email("newuser@example.com")
                .password("password123")
                .phone("13900139000")
                .role("USER")
                .build();
        
        // 设置登录请求
        authRequest = AuthRequest.builder()
                .username("testuser")
                .password("password123")
                .build();

        // 通用 mock
        when(jwtTokenProvider.generateToken(any(String.class), any(Long.class), any(String.class))).thenReturn("test-jwt-token");
        when(jwtTokenProvider.generateToken(any(Authentication.class))).thenReturn("test-jwt-token");
    }

    @Test
    void register_Success() {
        // 准备
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(jwtTokenProvider.generateToken(any(Authentication.class))).thenReturn("test-jwt-token");
        
        // 执行
        AuthResponse response = authService.register(registerRequest);
        
        // 验证
        assertNotNull(response);
        assertNotNull(response.getToken());
        assertEquals("test-jwt-token", response.getToken());
        assertNotNull(response.getUser());
        assertEquals("newuser", response.getUser().getUsername());
        
        verify(userRepository, times(1)).saveAndFlush(any(User.class));
        verify(notificationService, times(1)).createNotification(any(), any(), any(), any(), any(), any());
    }

    @Test
    void register_UsernameAlreadyExists() {
        // 准备
        when(userRepository.existsByUsername("newuser")).thenReturn(true);
        
        // 执行和验证
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authService.register(registerRequest);
        });
        
        assertEquals("用户名已存在", exception.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_EmailAlreadyExists() {
        // 准备
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(true);
        
        // 执行和验证
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authService.register(registerRequest);
        });
        
        assertEquals("邮箱已存在", exception.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void login_Success() {
        // 准备
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        
        // 执行
        AuthResponse response = authService.login(authRequest);
        
        // 验证
        assertNotNull(response);
        assertEquals("test-jwt-token", response.getToken());
        assertNotNull(response.getUser());
        assertEquals("testuser", response.getUser().getUsername());
        
        verify(jwtTokenProvider).generateToken(authentication);
        verify(userRepository, times(1)).updateLastLogin(eq(testUser.getId()), any(java.time.LocalDateTime.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_UserNotFound() {
        // 准备
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.empty());

        // 执行和验证
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authService.login(authRequest);
        });

        assertTrue(exception.getMessage().contains("用户不存在"));
    }

    @Test
    void login_BadCredentials() {
        // 准备
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));
        
        // 执行和验证
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            authService.login(authRequest);
        });
        
        assertEquals("密码错误，请重新输入", exception.getMessage());
    }

    @Test
    void getUserInfo_Success() {
        // 准备
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        
        // 执行
        AuthResponse response = authService.getUserInfo("testuser");
        
        // 验证
        assertNotNull(response);
        assertNotNull(response.getUser());
        assertEquals("testuser", response.getUser().getUsername());
        assertEquals("test@example.com", response.getUser().getEmail());
    }

    @Test
    void getUserInfo_UserNotFound() {
        // 准备
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());
        
        // 执行和验证
        assertThrows(RuntimeException.class, () -> {
            authService.getUserInfo("nonexistent");
        });
    }

    @Test
    void isUsernameExists_True() {
        // 准备
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);
        
        // 执行
        boolean exists = authService.isUsernameExists("existinguser");
        
        // 验证
        assertTrue(exists);
    }

    @Test
    void isUsernameExists_False() {
        // 准备
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        
        // 执行
        boolean exists = authService.isUsernameExists("newuser");
        
        // 验证
        assertFalse(exists);
    }

    @Test
    void isEmailExists_True() {
        // 准备
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);
        
        // 执行
        boolean exists = authService.isEmailExists("existing@example.com");
        
        // 验证
        assertTrue(exists);
    }

    @Test
    void isEmailExists_False() {
        // 准备
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        
        // 执行
        boolean exists = authService.isEmailExists("new@example.com");
        
        // 验证
        assertFalse(exists);
    }

    @Test
    void isUsernameExists_EmptyUsername() {
        // 执行
        boolean exists = authService.isUsernameExists("");
        
        // 验证
        assertFalse(exists);
        verify(userRepository, never()).existsByUsername(any());
    }

    @Test
    void isUsernameExists_NullUsername() {
        // 执行
        boolean exists = authService.isUsernameExists(null);
        
        // 验证
        assertFalse(exists);
        verify(userRepository, never()).existsByUsername(any());
    }

    @Test
    void updateUserAvatar_Success() {
        // 准备
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        
        // 执行
        assertDoesNotThrow(() -> {
            authService.updateUserAvatar("testuser", "http://example.com/avatar.jpg");
        });
        
        // 验证
        verify(userRepository, times(1)).save(any(User.class));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ADMIN", "ROLE_ADMIN", " admin ", "role_admin", "UNKNOWN", "ROLE_ROLE_ADMIN", "LANDLORD", "ROLE_LANDLORD"})
    void register_RejectsNonPublicRolesBeforeWriting(String role) {
        registerRequest.setRole(role);
        assertThrows(com.homestay3.homestaybackend.exception.CredentialValidationException.class,
                () -> authService.register(registerRequest));
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(passwordEncoder, authenticationManager);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"USER", "ROLE_USER", " user ", "HOST", "role_host", ""})
    @org.junit.jupiter.params.provider.NullSource
    void register_NormalizesPublicRoles(String role) {
        registerRequest.setRole(role);
        when(passwordEncoder.encode(any())).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(2L);
            return user;
        });
        AuthResponse response = authService.register(registerRequest);
        assertEquals(role != null && role.toUpperCase(java.util.Locale.ROOT).contains("HOST")
                ? "ROLE_HOST" : "ROLE_USER", response.getUser().getRole());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ADMIN", "ROLE_ADMIN", " admin ", "role_admin"})
    void login_RejectsAdministratorAliases(String role) {
        testUser.setRole(role);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        assertThrows(com.homestay3.homestaybackend.exception.LoginException.class,
                () -> authService.login(authRequest));
        verifyNoInteractions(authenticationManager);
        verify(userRepository, never()).save(any());
    }

    @Test
    void forgotPassword_SendsIndependentTokenAndStoresOnlyDigest() {
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(testUser));
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:5173");
        authService.forgotPassword("test@example.com");
        var link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq("test@example.com"), link.capture());
        String token = link.getValue().substring(link.getValue().indexOf("token=") + 6);
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(digest(token), testUser.getResetToken());
        assertNotEquals(token, testUser.getResetToken());
        assertTrue(testUser.getResetTokenExpiry().isAfter(java.time.LocalDateTime.now().plusHours(23)));
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void forgotPassword_UnknownEmailDoesNotRevealAccountExistence() {
        assertDoesNotThrow(() -> authService.forgotPassword("missing@example.com"));
        verifyNoInteractions(emailService, jwtTokenProvider);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void resetPassword_ConsumesTokenAndClearsExpiry() {
        String token = "a".repeat(43);
        testUser.setResetToken(digest(token));
        testUser.setResetTokenExpiry(java.time.LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByResetToken(digest(token))).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        authService.resetPassword(resetRequest(token));
        assertEquals("new-hash", testUser.getPassword());
        assertNull(testUser.getResetToken());
        assertNull(testUser.getResetTokenExpiry());
        assertThrows(com.homestay3.homestaybackend.exception.CredentialValidationException.class,
                () -> authService.resetPassword(resetRequest(token)));
        verify(userRepository, times(1)).saveAndFlush(testUser);
        verifyNoInteractions(jwtTokenProvider);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {-1, 0})
    void resetPassword_RejectsExpiredToken(int minutes) {
        String token = "a".repeat(43);
        testUser.setResetToken(digest(token));
        testUser.setResetTokenExpiry(java.time.LocalDateTime.now().plusMinutes(minutes));
        when(userRepository.findByResetToken(digest(token))).thenReturn(Optional.of(testUser));
        assertThrows(com.homestay3.homestaybackend.exception.CredentialValidationException.class,
                () -> authService.resetPassword(resetRequest(token)));
        verifyNoInteractions(passwordEncoder, jwtTokenProvider);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void resetPassword_RejectsMissingExpiryAndUnknownToken() {
        String token = "a".repeat(43);
        when(userRepository.findByResetToken(digest(token))).thenReturn(Optional.of(testUser));
        assertThrows(com.homestay3.homestaybackend.exception.CredentialValidationException.class,
                () -> authService.resetPassword(resetRequest(token)));
        when(userRepository.findByResetToken(digest(token))).thenReturn(Optional.empty());
        assertThrows(com.homestay3.homestaybackend.exception.CredentialValidationException.class,
                () -> authService.resetPassword(resetRequest(token)));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void resetPassword_RejectsOrdinaryJwtWithoutParsingIt() {
        assertThrows(com.homestay3.homestaybackend.exception.CredentialValidationException.class,
                () -> authService.resetPassword(resetRequest("eyJhbGciOiJIUzI1NiJ9.payload.signature")));
        verifyNoInteractions(jwtTokenProvider, passwordEncoder, userRepository);
    }

    @Test
    void resetPassword_RejectsWeakPasswordBeforeConsumingToken() {
        var request = resetRequest("a".repeat(43));
        request.setNewPassword("123");
        assertThrows(com.homestay3.homestaybackend.exception.CredentialValidationException.class,
                () -> authService.resetPassword(request));
        verifyNoInteractions(jwtTokenProvider, passwordEncoder, userRepository);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"LANDLORD", "ROLE_LANDLORD"})
    void login_AllowsLegacyHostAliasWithAuthenticatedSnapshot(String role) {
        testUser.setRole(role);
        Authentication authentication = mock(Authentication.class);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        authService.login(authRequest);
        verify(jwtTokenProvider).generateToken(authentication);
    }

    @Test
    void credentialRequestsDoNotExposePasswordsOrResetTokenInToString() {
        assertFalse(registerRequest.toString().contains(registerRequest.getPassword()));
        var reset = resetRequest("a".repeat(43));
        assertFalse(reset.toString().contains(reset.getToken()));
        assertFalse(reset.toString().contains(reset.getNewPassword()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"UNKNOWN", ""})
    @org.junit.jupiter.params.provider.NullSource
    void login_RejectsInvalidStoredRoles(String role) {
        testUser.setRole(role);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        assertThrows(com.homestay3.homestaybackend.exception.LoginException.class,
                () -> authService.login(authRequest));
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void login_DisabledAndLockedAreAuthenticationFailures() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new org.springframework.security.authentication.DisabledException("disabled"));
        assertThrows(com.homestay3.homestaybackend.exception.LoginException.class,
                () -> authService.login(authRequest));
        doThrow(new org.springframework.security.authentication.LockedException("locked"))
                .when(authenticationManager).authenticate(any());
        assertThrows(com.homestay3.homestaybackend.exception.LoginException.class,
                () -> authService.login(authRequest));
        verify(userRepository, never()).save(any());
    }

    private static com.homestay3.homestaybackend.dto.PasswordResetRequest resetRequest(String token) {
        return com.homestay3.homestaybackend.dto.PasswordResetRequest.builder()
                .token(token).newPassword("new-password").build();
    }

    private static String digest(String token) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
