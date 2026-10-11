package com.homestay3.homestaybackend.service.impl;

import com.homestay3.homestaybackend.dto.PasswordResetRequest;
import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.exception.CredentialValidationException;
import com.homestay3.homestaybackend.mapper.UserMapper;
import com.homestay3.homestaybackend.repository.CouponTemplateRepository;
import com.homestay3.homestaybackend.repository.UserRepository;
import com.homestay3.homestaybackend.security.JwtTokenProvider;
import com.homestay3.homestaybackend.service.CouponService;
import com.homestay3.homestaybackend.service.EmailService;
import com.homestay3.homestaybackend.service.NotificationService;
import com.homestay3.homestaybackend.service.ReferralService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 独立 H2 上验证真实事务和悲观锁，不启动 HTTP/邮件/业务数据库。 */
@DataJpaTest(properties = {"spring.datasource.url=jdbc:h2:mem:credential-reset;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.jpa.show-sql=false"})
@ActiveProfiles("test")
@Import({AuthServiceImpl.class, UserServiceImpl.class, PasswordResetTransactionTest.EncoderConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PasswordResetTransactionTest {
    @Autowired private AuthServiceImpl authService;
    @SpyBean private UserServiceImpl userService;
    @Autowired private UserRepository userRepository;
    @SpyBean private PasswordEncoder passwordEncoder;
    @MockBean private EmailService emailService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private AuthenticationManager authenticationManager;
    @MockBean private NotificationService notificationService;
    @MockBean private UserMapper userMapper;
    @MockBean private CouponTemplateRepository couponTemplateRepository;
    @MockBean private CouponService couponService;
    @MockBean private ReferralService referralService;

    private User user;

    @BeforeEach
    void createIsolatedUser() {
        user = new User();
        user.setUsername("reset_" + UUID.randomUUID().toString().substring(0, 12));
        user.setEmail(user.getUsername() + "@example.com");
        user.setRole("ROLE_USER");
        user.setPassword(passwordEncoder.encode("original-password"));
        user = userRepository.saveAndFlush(user);
    }

    @AfterEach
    void cleanOnlyThisTestUser() {
        userRepository.deleteById(user.getId());
    }

    @Test
    void resetIsConsumedAfterCommit() {
        String token = issueToken();
        authService.resetPassword(request(token, "new-password"));
        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches("new-password", updated.getPassword()));
        assertNull(updated.getResetToken());
        assertNull(updated.getResetTokenExpiry());
        assertThrows(CredentialValidationException.class,
                () -> authService.resetPassword(request(token, "replay-password")));
        assertTrue(passwordEncoder.matches("new-password", userRepository.findById(user.getId()).orElseThrow().getPassword()));
    }

    @Test
    void reissuingInvalidatesPreviousLinkAndMailFailureRollsBack() {
        String first = issueToken();
        clearInvocations(emailService);
        String second = issueToken();
        assertNotEquals(first, second);
        assertThrows(CredentialValidationException.class,
                () -> authService.resetPassword(request(first, "replay-password")));
        User issued = userRepository.findById(user.getId()).orElseThrow();
        doThrow(new RuntimeException("mail transport unavailable"))
                .when(emailService).sendPasswordResetEmail(anyString(), anyString());
        assertThrows(RuntimeException.class, () -> authService.forgotPassword(user.getEmail()));
        assertEquals(issued.getResetToken(), userRepository.findById(user.getId()).orElseThrow().getResetToken());
        authService.resetPassword(request(second, "new-password"));
    }

    @Test
    void expiredAndUnknownTokensCannotChangePassword() {
        String token = issueToken();
        User issued = userRepository.findById(user.getId()).orElseThrow();
        issued.setResetTokenExpiry(LocalDateTime.now().minusSeconds(1));
        userRepository.saveAndFlush(issued);
        assertThrows(CredentialValidationException.class,
                () -> authService.resetPassword(request(token, "new-password")));
        assertThrows(CredentialValidationException.class,
                () -> authService.resetPassword(request("z".repeat(43), "new-password")));
        assertTrue(passwordEncoder.matches("original-password", userRepository.findById(user.getId()).orElseThrow().getPassword()));
    }

    @Test
    void concurrentReplayCanCommitOnlyOnce() throws Exception {
        String token = issueToken();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> raceReset(token, "first-password", ready, start));
            var second = executor.submit(() -> raceReset(token, "second-password", ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            boolean firstSucceeded = first.get(15, TimeUnit.SECONDS);
            boolean secondSucceeded = second.get(15, TimeUnit.SECONDS);
            assertNotEquals(firstSucceeded, secondSucceeded, "同一令牌必须只有一次事务成功");
            User updated = userRepository.findById(user.getId()).orElseThrow();
            assertTrue(passwordEncoder.matches(firstSucceeded ? "first-password" : "second-password", updated.getPassword()));
            assertNull(updated.getResetToken());
            assertNull(updated.getResetTokenExpiry());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentLoginCannotRestoreConsumedTokenOrOldPassword() throws Exception {
        String token = issueToken();
        CountDownLatch loginHasLoadedUser = new CountDownLatch(1);
        CountDownLatch resetCommitted = new CountDownLatch(1);
        when(authenticationManager.authenticate(any())).thenAnswer(inv -> {
            loginHasLoadedUser.countDown();
            if (!resetCommitted.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("重置事务未提交");
            }
            return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    user.getUsername(), null, java.util.List.of());
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var login = executor.submit(() -> authService.login(
                    com.homestay3.homestaybackend.dto.AuthRequest.builder()
                            .username(user.getUsername()).password("original-password").build()));
            assertTrue(loginHasLoadedUser.await(5, TimeUnit.SECONDS));
            authService.resetPassword(request(token, "new-password"));
            resetCommitted.countDown();
            login.get(15, TimeUnit.SECONDS);
            User updated = userRepository.findById(user.getId()).orElseThrow();
            assertTrue(passwordEncoder.matches("new-password", updated.getPassword()));
            assertNull(updated.getResetToken());
            assertNull(updated.getResetTokenExpiry());
            assertNotNull(updated.getLastLogin());
            assertThrows(CredentialValidationException.class,
                    () -> authService.resetPassword(request(token, "replay-password")));
        } finally {
            resetCommitted.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void passwordChangeWaitingForResetCannotUseOldPassword() throws Exception {
        String token = issueToken();
        CountDownLatch resetHasLock = new CountDownLatch(1);
        CountDownLatch releaseReset = new CountDownLatch(1);
        CountDownLatch changeStarted = new CountDownLatch(1);
        doAnswer(inv -> {
            resetHasLock.countDown();
            if (!releaseReset.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("重置事务未释放");
            }
            return inv.callRealMethod();
        }).when(passwordEncoder).encode("reset-password");
        doAnswer(inv -> {
            changeStarted.countDown();
            return inv.callRealMethod();
        }).when(userService).changePassword(any(), eq(user.getUsername()));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var reset = executor.submit(() -> authService.resetPassword(request(token, "reset-password")));
            assertTrue(resetHasLock.await(5, TimeUnit.SECONDS));
            var change = executor.submit(() -> userService.changePassword(
                    com.homestay3.homestaybackend.dto.PasswordChangeRequest.builder()
                            .oldPassword("original-password").newPassword("change-password")
                            .confirmPassword("change-password").build(),
                    user.getUsername()));
            assertTrue(changeStarted.await(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class,
                    () -> change.get(200, TimeUnit.MILLISECONDS), "改密应等待重置释放用户行锁");
            releaseReset.countDown();
            reset.get(15, TimeUnit.SECONDS);
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> change.get(15, TimeUnit.SECONDS));
            assertInstanceOf(RuntimeException.class, failure.getCause());
            assertEquals("原密码不正确", failure.getCause().getMessage());
            User updated = userRepository.findById(user.getId()).orElseThrow();
            assertTrue(passwordEncoder.matches("reset-password", updated.getPassword()));
            assertFalse(passwordEncoder.matches("change-password", updated.getPassword()));
            assertNull(updated.getResetToken());
            assertNull(updated.getResetTokenExpiry());
            assertThrows(CredentialValidationException.class,
                    () -> authService.resetPassword(request(token, "replay-password")));
        } finally {
            releaseReset.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private boolean raceReset(String token, String password, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("重置并发测试未能同时启动");
        }
        try {
            authService.resetPassword(request(token, password));
            return true;
        } catch (CredentialValidationException e) {
            return false;
        }
    }

    private String issueToken() {
        authService.forgotPassword(user.getEmail());
        var link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(user.getEmail()), link.capture());
        return link.getValue().substring(link.getValue().indexOf("token=") + 6);
    }

    private static PasswordResetRequest request(String token, String password) {
        return PasswordResetRequest.builder().token(token).newPassword(password).build();
    }

    @TestConfiguration
    static class EncoderConfig {
        @Bean PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
