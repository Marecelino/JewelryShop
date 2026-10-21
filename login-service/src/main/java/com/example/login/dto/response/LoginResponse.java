package com.example.login.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Phản hồi đăng nhập thành công chứa access token JWT")
public class LoginResponse {

    @Schema(description = "JWT Access Token được ký bằng RS256", example = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
    private final String accessToken;

    @Schema(description = "Loại token", example = "Bearer")
    private final String tokenType;

    @Schema(description = "Thời điểm hết hạn của token", example = "2026-10-20T08:15:00Z")
    private final Instant expiresAt;

    @Schema(description = "Thông tin tài khoản đã xác thực")
    private final UserSummary user;

    public LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserSummary user) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UserSummary getUser() {
        return user;
    }
}