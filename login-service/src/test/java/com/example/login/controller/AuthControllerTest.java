package com.example.login.controller;

import com.example.login.config.SecurityConfig;
import com.example.login.dto.request.RegisterRequest;
import com.example.login.dto.response.UserResponse;
import com.example.login.exception.DuplicateAccountException;
import com.example.login.exception.GlobalExceptionHandler;
import com.example.login.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.login.config.CustomAccessDeniedHandler;
import com.example.login.config.CustomAuthenticationEntryPoint;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    // SecurityConfig requires JwtDecoder, CustomAuthenticationEntryPoint, and
    // CustomAccessDeniedHandler via its constructor; mock all three so the slice
    // test context loads without instantiating JwtConfig or reading RSA key files.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @MockitoBean
    private CustomAccessDeniedHandler customAccessDeniedHandler;

    @Test
    void register_ShouldReturn201Created_WhenValidRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("user1", "thu@gmail.com", "password");
        UserResponse response = new UserResponse(
                1L,
                "user1",
                "thu@gmail.com",
                "USER",
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("user1"))
                .andExpect(jsonPath("$.email").value("thu@gmail.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_ShouldReturn400_WhenEmailIsInvalid() throws Exception {
        RegisterRequest request = new RegisterRequest("user1", "not-an-email", "password");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.email").exists());
    }

    @Test
    void register_ShouldReturn400_WhenUsernameIsTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest("ab", "thu@gmail.com", "password");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.username").exists());
    }

    @Test
    void register_ShouldReturn400_WhenPasswordIsTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest("user1", "thu@gmail.com", "123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    void register_ShouldReturn409_WhenAccountAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest("user1", "thu@gmail.com", "password");

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateAccountException("Account already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Account already exists"));
    }

    @Test
    void register_ShouldReturn400_WhenBodyIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request body is missing or contains invalid JSON"));
    }
}
