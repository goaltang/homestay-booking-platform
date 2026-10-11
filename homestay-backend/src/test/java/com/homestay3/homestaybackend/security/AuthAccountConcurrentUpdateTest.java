package com.homestay3.homestaybackend.security;

import com.homestay3.homestaybackend.entity.User;
import com.homestay3.homestaybackend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:auth_concurrent_regression;DB_CLOSE_DELAY=-1;MODE=MySQL")
@ActiveProfiles("test")
class AuthAccountConcurrentUpdateTest {
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void managedProfileUpdateDoesNotRestoreConcurrentPasswordRoleOrEnabled() {
        String name = UUID.randomUUID().toString().substring(0, 16);
        User original = users.saveAndFlush(User.builder().username(name).email(name + "@test.invalid")
                .password("original-test-hash").role("ROLE_USER").enabled(true).resetToken("original-reset").build());
        TransactionTemplate profile = new TransactionTemplate(transactionManager);
        TransactionTemplate reset = new TransactionTemplate(transactionManager);
        reset.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            profile.executeWithoutResult(status -> {
                User stale = users.findById(original.getId()).orElseThrow();
                reset.executeWithoutResult(inner -> {
                    User current = users.findById(original.getId()).orElseThrow();
                    current.setPassword("changed-test-hash");
                    current.setResetToken(null);
                    current.setRole("ROLE_HOST");
                    current.setEnabled(false);
                    users.saveAndFlush(current);
                });
                stale.setFullName("updated profile");
                users.saveAndFlush(stale);
            });
            User current = users.findById(original.getId()).orElseThrow();
            assertThat(current.getFullName()).isEqualTo("updated profile");
            assertThat(current.getPassword()).isEqualTo("changed-test-hash");
            assertThat(current.getResetToken()).isNull();
            assertThat(current.getRole()).isEqualTo("ROLE_HOST");
            assertThat(current.isEnabled()).isFalse();
        } finally {
            users.deleteById(original.getId());
        }
    }
}
