package com.example.login.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Thông tin tóm tắt người dùng trong phản hồi đăng nhập")
public class UserSummary {

    @Schema(description = "ID người dùng", example = "1")
    private final Long id;

    @Schema(description = "Tên đăng nhập", example = "user1")
    private final String username;

    @Schema(description = "Địa chỉ email", example = "thu@gmail.com")
    private final String email;

    @Schema(description = "Vai trò", example = "USER")
    private final String role;

    @Schema(description = "Trạng thái kích hoạt", example = "true")
    private final boolean enabled;

    public UserSummary(Long id, String username, String email, String role, boolean enabled) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.role = role;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
