package com.musicapp;

import com.musicapp.auth.*;
import com.musicapp.common.BadRequestException;
import com.musicapp.common.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {
    private MockMvc mockMvc;
    private FakeAuthService authService;

    @BeforeEach
    void setUp () {
        authService = new FakeAuthService();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(
                        authService,
                        new HttpSessionSecurityContextRepository(),
                        new ChangeSessionIdAuthenticationStrategy()
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void registerReturnsCreatedListener () throws Exception {
        authService.registerResponse = authResponse();
        authService.loginResponse = authenticatedListener();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "musiclover42",
                                  "emailAddress": "musiclover42@example.com",
                                  "password": "password123",
                                  "gender": "Male",
                                  "dateOfBirth": "1995-03-15",
                                  "countryName": "United States"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listenerId").value(1))
                .andExpect(jsonPath("$.username").value("musiclover42"))
                .andExpect(jsonPath("$.emailAddress").value("musiclover42@example.com"))
                .andExpect(jsonPath("$.countryName").value("United States"))
                .andExpect(jsonPath("$.planName").value("free"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(request().sessionAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void registerValidationErrorsUseErrorResponseShape () throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "emailAddress": "not-an-email",
                                  "password": "short",
                                  "gender": "",
                                  "dateOfBirth": null,
                                  "countryName": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.messages").isArray())
                .andExpect(jsonPath("$.messages", hasItem(startsWith("username "))))
                .andExpect(jsonPath("$.messages", hasItem(startsWith("emailAddress "))))
                .andExpect(jsonPath("$.messages", hasItem(startsWith("password "))));
    }

    @Test
    void loginReturnsListener () throws Exception {
        authService.loginResponse = authenticatedListener();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "musiclover42",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listenerId").value(1))
                .andExpect(jsonPath("$.username").value("musiclover42"))
                .andExpect(jsonPath("$.emailAddress").value("musiclover42@example.com"))
                .andExpect(request().sessionAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void loginBadRequestUsesErrorResponseShape () throws Exception {
        authService.loginException = new BadRequestException("Invalid username or password.");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "musiclover42",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.messages[0]").value("Invalid username or password."));
    }

    private AuthResponse authResponse () {
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

    private AuthenticatedListener authenticatedListener () {
        return new AuthenticatedListener(
                authResponse(),
                new UsernamePasswordAuthenticationToken("musiclover42", null, List.of())
        );
    }

    static class FakeAuthService extends AuthService {
        private AuthResponse registerResponse;
        private AuthenticatedListener loginResponse;
        private RuntimeException loginException;

        FakeAuthService () {
            super(null, null, null, null);
        }

        @Override
        public AuthResponse register (RegisterRequest request) {
            return registerResponse;
        }

        @Override
        public AuthenticatedListener login (LoginRequest request) {
            if (loginException != null) {
                throw loginException;
            }
            return loginResponse;
        }
    }
}
