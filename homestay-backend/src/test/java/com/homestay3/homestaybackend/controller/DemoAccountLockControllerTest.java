package com.homestay3.homestaybackend.controller;

import com.homestay3.homestaybackend.exception.GlobalExceptionHandler;
import com.homestay3.homestaybackend.security.AuthCookieService;
import com.homestay3.homestaybackend.security.DemoAccountGuard;
import com.homestay3.homestaybackend.service.HostService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 演示账号锁：房东资料接口外层 try/catch 会吞异常，必须确认返回 403 而不是 500。 */
@ExtendWith(MockitoExtension.class)
class DemoAccountLockControllerTest {

    @Mock
    private HostService hostService;

    @Mock
    private AuthCookieService authCookieService;

    private MockMvc mockMvcFor(String username) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "n/a", List.of(new SimpleGrantedAuthority("ROLE_LANDLORD"))));
        SecurityContextHolder.setContext(context);
        HostController controller = new HostController(
                hostService, authCookieService, new DemoAccountGuard("host_0097"));
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new MockEnvironment()))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lockedHostCannotUpdateProfile() throws Exception {
        mockMvcFor("host_0097")
                .perform(put("/api/host/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"10000000000\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("演示账号不能修改密码和个人资料"));
        verifyNoInteractions(hostService);
    }

    @Test
    void otherHostCanStillUpdateProfile() throws Exception {
        when(hostService.updateHostProfile(eq("host_0001"), any())).thenReturn(Map.of("ok", true));
        mockMvcFor("host_0001")
                .perform(put("/api/host/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"10000000000\"}"))
                .andExpect(status().isOk());
        verify(hostService).updateHostProfile(eq("host_0001"), any());
    }
}
