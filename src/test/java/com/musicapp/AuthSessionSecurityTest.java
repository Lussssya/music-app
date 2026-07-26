package com.musicapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicapp.auth.AuthController;
import com.musicapp.auth.AuthResponse;
import com.musicapp.auth.AuthService;
import com.musicapp.auth.AuthenticatedListener;
import com.musicapp.auth.CsrfTokenResponse;
import com.musicapp.auth.ListenerUserDetailsService;
import com.musicapp.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthSessionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private ListenerUserDetailsService listenerUserDetailsService;

    @BeforeEach
    void setUp () {
        final AuthResponse user = user();
        when(authService.login(any())).thenReturn(new AuthenticatedListener(
                user,
                new UsernamePasswordAuthenticationToken(
                        user.username(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_LISTENER"))
                )
        ));
        when(authService.getCurrentUser(user.username())).thenReturn(user);
    }

    @Test
    void csrfEndpointCreatesATokenInTheServerSessionForTheSpa () throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token", not(blankOrNullString())))
                .andExpect(request().sessionAttribute(
                        HttpSessionCsrfTokenRepository.class.getName() + ".CSRF_TOKEN",
                        notNullValue()
                ));
    }

    @Test
    void loginRequiresCsrfAndStoresAuthenticationInSession () throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()))
                .andExpect(status().isForbidden());

        final MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        final CsrfTokenResponse csrfToken = objectMapper.readValue(
                csrfResult.getResponse().getContentAsByteArray(),
                CsrfTokenResponse.class
        );
        final MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header(csrfToken.headerName(), csrfToken.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("musiclover42"))
                .andExpect(request().sessionAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        notNullValue()
                ));
    }

    @Test
    void basicCredentialsNoLongerAuthenticateProtectedRequests () throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Basic bXVzaWNsb3ZlcjQyOnBhc3Nzd29yZDEyMw=="))
                .andExpect(status().isUnauthorized());
    }

    private String loginJson () {
        return """
                {
                  "username": "musiclover42",
                  "password": "password123"
                }
                """;
    }

    private AuthResponse user () {
        return new AuthResponse(
                1L,
                "musiclover42",
                "musiclover42@example.com",
                "United States",
                "Male",
                LocalDate.of(1995, 3, 15),
                "free",
                BigDecimal.ZERO
        );
    }
}
