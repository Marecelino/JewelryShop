package com.example.login.service;

import com.example.login.dto.request.LoginRequest;
import com.example.login.dto.request.RegisterRequest;
import com.example.login.dto.response.LoginResponse;
import com.example.login.dto.response.UserResponse;
import com.example.login.entity.User;
import com.example.login.exception.DuplicateAccountException;
import com.example.login.exception.InvalidCredentialsException;
import com.example.login.exception.UserNotFoundException;
import com.example.login.mapper.UserMapper;
import com.example.login.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        // 1. Chuẩn hóa username và email
        String normalizedUsername = request.getUsername() != null ? request.getUsername().trim() : null;
        String normalizedEmail = request.getEmail() != null ? request.getEmail().trim().toLowerCase(Locale.ROOT) : null;

        // 2. Kiểm tra tồn tại sớm
        if (normalizedUsername != null && userRepository.existsByUsername(normalizedUsername)) {
            throw new DuplicateAccountException("Account already exists");
        }

        if (normalizedEmail != null && userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateAccountException("Account already exists");
        }

        // 3. Hash mật khẩu (BCrypt 12 rounds)
        String passwordHash = passwordEncoder.encode(request.getPassword());

        // 4. Khởi tạo User và gán role mặc định USER tại server
        User user = new User();
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordHash);
        user.setRole("USER");
        user.setEnabled(true);

        // 5. Lưu vào database, bắt lỗi unique constraint trong tình huống race condition
        try {
            User savedUser = userRepository.saveAndFlush(user);
            return userMapper.toResponse(savedUser);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueViolation(ex)) {
                throw new DuplicateAccountException("Account already exists");
            }
            throw ex;
        }
    }

    private boolean isUniqueViolation(DataIntegrityViolationException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase(Locale.ROOT) : "";
        if (ex.getCause() != null && ex.getCause().getMessage() != null) {
            msg += " " + ex.getCause().getMessage().toLowerCase(Locale.ROOT);
        }
        return msg.contains("uq_users_")
                || msg.contains("unique")
                || msg.contains("duplicate key")
                || msg.contains("2627")
                || msg.contains("2601");
    }

    public LoginResponse login(LoginRequest request) {

        String normalizedEmail = request.getEmail() != null
                ? request.getEmail().trim().toLowerCase(Locale.ROOT)
                : "";

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new InvalidCredentialsException(
                                "Invalid email or password"
                        )
                );

        // ⚡ Bolt: Early return for disabled accounts to avoid expensive BCrypt hashing
        // Expected impact: Saves ~100-300ms (cost of BCrypt) when a disabled user attempts login
        if (!user.isEnabled()) {
            throw new InvalidCredentialsException(
                    "Invalid email or password"
            );
        }

        boolean matches = passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        );

        if (!matches) {
            throw new InvalidCredentialsException(
                    "Invalid email or password"
            );
        }

        return jwtService.generateLoginResponse(user);
    }

    public UserResponse getCurrentUser(String subject) {
        if (subject == null) {
            throw new InvalidCredentialsException("Unauthorized");
        }

        Long userId;
        try {
            userId = Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            throw new UserNotFoundException("User not found");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!user.isEnabled()) {
            throw new InvalidCredentialsException("Account is disabled");
        }

        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Jwt jwt) {
        if (jwt == null) {
            throw new InvalidCredentialsException("Unauthorized");
        }
        return getCurrentUser(jwt.getSubject());
    }
}
