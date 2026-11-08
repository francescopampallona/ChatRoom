package com.francescopampallona.chatroom.ai;

import com.francescopampallona.chatroom.dto.request.LoginRequest;
import com.francescopampallona.chatroom.model.User;
import com.francescopampallona.chatroom.repository.UserRepository;
import com.francescopampallona.chatroom.security.JwtAuthenticationFilter;
import com.francescopampallona.chatroom.security.JwtStompAuthenticationInterceptor;
import com.francescopampallona.chatroom.service.AuthService;
import com.francescopampallona.chatroom.service.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentAuthenticationTest {
    private final User agent = User.builder().id(10L).username("aria.ai").agentKey("aria").passwordHash("unused").build();
    private final UserRepository users = mock(UserRepository.class);
    private final JwtService jwt = mock(JwtService.class);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void agentLoginIsRejectedBeforeCheckingPasswordOrIssuingToken() {
        when(users.findByUsername("aria.ai")).thenReturn(Optional.of(agent));
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AuthService auth = new AuthService(users, passwordEncoder, jwt);
        LoginRequest request = new LoginRequest();
        request.setUsername("aria.ai");
        request.setPassword("anything");
        assertThrows(BadCredentialsException.class, () -> auth.login(request));
        verifyNoInteractions(passwordEncoder, jwt);
    }

    @Test
    void evenASignedTokenCannotAuthenticateAnAgentOverHttp() throws Exception {
        when(users.findByUsername("aria.ai")).thenReturn(Optional.of(agent));
        when(jwt.extractUsername("token")).thenReturn("aria.ai");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        FilterChain chain = mock(FilterChain.class);
        new JwtAuthenticationFilter(jwt, users).doFilter(request, new MockHttpServletResponse(), chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(eq(request), any());
    }

    @Test
    void agentTokenIsAlsoRejectedByStompConnect() {
        when(users.findByUsername("aria.ai")).thenReturn(Optional.of(agent));
        when(jwt.extractUsername("token")).thenReturn("aria.ai");
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader("Authorization", "Bearer token");
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        JwtStompAuthenticationInterceptor interceptor = new JwtStompAuthenticationInterceptor(jwt, users);
        assertThrows(BadCredentialsException.class, () -> interceptor.preSend(message, null));
    }
}
