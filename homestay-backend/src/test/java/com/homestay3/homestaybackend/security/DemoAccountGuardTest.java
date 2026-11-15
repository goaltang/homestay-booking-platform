package com.homestay3.homestaybackend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoAccountGuardTest {

    @Test
    void emptyConfigLocksNobody() {
        DemoAccountGuard guard = new DemoAccountGuard("");
        assertThat(guard.isLocked("user_4424")).isFalse();
        assertThat(guard.isLocked(null)).isFalse();
        assertThatCode(() -> guard.assertModifiable("user_4424")).doesNotThrowAnyException();
    }

    @Test
    void blankEntriesFromUnsetEnvVarsAreIgnored() {
        // prod 配置为 ${DEMO_GUEST_USERNAME:},${DEMO_HOST_USERNAME:}，只设一个时会出现空项
        DemoAccountGuard guard = new DemoAccountGuard(" user_4424 , ,");
        assertThat(guard.isLocked("user_4424")).isTrue();
        assertThat(guard.isLocked("")).isFalse();
        assertThat(guard.isLocked(" ")).isFalse();
    }

    @Test
    void lockedUsernameIsRejectedOthersAllowed() {
        DemoAccountGuard guard = new DemoAccountGuard("user_4424,host_0097");
        assertThatThrownBy(() -> guard.assertModifiable("host_0097"))
                .isInstanceOf(SecurityException.class)
                .hasMessage(DemoAccountGuard.LOCKED_MESSAGE);
        assertThatCode(() -> guard.assertModifiable("host_0001")).doesNotThrowAnyException();
    }
}
