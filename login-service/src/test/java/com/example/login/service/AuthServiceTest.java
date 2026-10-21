package com.example.login.service;

import com.example.login.dto.request.LoginRequest;
import com.example.login.dto.request.RegisterRequest;
import com.example.login.dto.response.LoginResponse;
import com.example.login.dto.response.UserResponse;
import com.example.login.dto.response.UserSummary;
import com.example.login.entity.User;
import com.example.login.exception.DuplicateAccountException;
import com.example.login.exception.InvalidCredentialsException;
import com.example.login.exception.UserNotFoundException;
import com.example.login.mapper.UserMapper;
import com.example.login.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRegisterRequest;
    private LoginRequest validLoginRequest;
    private User activeUser;

    @BeforeEach
    void setUp() {
        validRegisterRequest = new RegisterRequest("  NewUser  ", "  NewUser@example.com  ", "securePass123");
        validLoginRequest = new LoginRequest("thu@gmail.com", "password");

        activeUser = new User();
        activeUser.setUsername("user1");
        activeUser.setEmail("thu@gmail.com");
        activeUser.setPasswordHash("$2a$12$hashedPassword");
        activeUser.setRole("USER");
        activeUser.setEnabled(true);
    }

    @Test
    void register_ShouldNormalizeAndSaveUser_WhenValidRequest() {
        when(userRepository.existsByUsername("NewUser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("securePass123")).thenReturn("$2a$12$hashedPassword");

        User savedUser = new User();
        savedUser.setUsername("NewUser");
        savedUser.setEmail("newuser@example.com");
        savedUser.setPasswordHash("$2a$12$hashedPassword");
        savedUser.setRole("USER");
        savedUser.setEnabled(true);

        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);

        UserResponse expectedResponse = new UserResponse(
                1L,
                "NewUser",
                "newuser@example.com",
                "USER",
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(userMapper.toResponse(savedUser)).thenReturn(expectedResponse);

        UserResponse result = authService.register(validRegisterRequest);

        assertNotNull(result);
        assertEquals("NewUser", result.getUsername());
        assertEquals("newuser@example.com", result.getEmail());
        assertEquals("USER", result.getRole());
        assertTrue(result.isEnabled());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertEquals("NewUser", capturedUser.getUsername());
        assertEquals("newuser@example.com", capturedUser.getEmail());
        assertEquals("$2a$12$hashedPassword", capturedUser.getPasswordHash());
        assertEquals("USER", capturedUser.getRole());
        assertTrue(capturedUser.isEnabled());
    }

    @Test
    void register_ShouldThrowDuplicateAccountException_WhenUsernameExists() {
        when(userRepository.existsByUsername("NewUser")).thenReturn(true);

        DuplicateAccountException ex = assertThrows(
                DuplicateAccountException.class,
                () -> authService.register(validRegisterRequest)
        );

        assertEquals("Account already exists", ex.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_ShouldThrowDuplicateAccountException_WhenEmailExists() {
        when(userRepository.existsByUsername("NewUser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(true);

        DuplicateAccountException ex = assertThrows(
                DuplicateAccountException.class,
                () -> authService.register(validRegisterRequest)
        );

        assertEquals("Account already exists", ex.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_ShouldThrowDuplicateAccountException_WhenConcurrentUniqueConstraintFails() {
        when(userRepository.existsByUsername("NewUser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedPassword");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Violation of UNIQUE KEY constraint 'UQ_users_email'"));

        DuplicateAccountException ex = assertThrows(
                DuplicateAccountException.class,
                () -> authService.register(validRegisterRequest)
        );

        assertEquals("Account already exists", ex.getMessage());
    }

    @Test
    void register_ShouldRethrow_WhenDataIntegrityViolationIsNotUniqueConstraint() {
        when(userRepository.existsByUsername("NewUser")).thenReturn(false);
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedPassword");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("Cannot insert the value NULL into column 'role'"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> authService.register(validRegisterRequest)
        );
    }

    @Test
    void login_ShouldReturnLoginResponse_WhenCredentialsAreValid() {
        when(userRepository.findByEmail("thu@gmail.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password", activeUser.getPasswordHash())).thenReturn(true);

        UserSummary summary = new UserSummary(1L, "user1", "thu@gmail.com", "USER", true);
        LoginResponse expectedResponse = new LoginResponse("mock-jwt-token", "Bearer", Instant.now().plusSeconds(900), summary);
        when(jwtService.generateLoginResponse(activeUser)).thenReturn(expectedResponse);

        LoginResponse result = authService.login(validLoginRequest);

        assertNotNull(result);
        assertEquals("mock-jwt-token", result.getAccessToken());
        assertEquals("Bearer", result.getTokenType());
        assertNotNull(result.getUser());
        assertEquals("user1", result.getUser().getUsername());
    }

    @Test
    void login_ShouldThrowInvalidCredentialsException_WhenEmailNotFound() {
        when(userRepository.findByEmail("thu@gmail.com")).thenReturn(Optional.empty());

        InvalidCredentialsException ex = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(validLoginRequest)
        );

        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    void login_ShouldThrowInvalidCredentialsException_WhenPasswordDoesNotMatch() {
        when(userRepository.findByEmail("thu@gmail.com")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("password", activeUser.getPasswordHash())).thenReturn(false);

        InvalidCredentialsException ex = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(validLoginRequest)
        );

        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    void login_ShouldThrowInvalidCredentialsException_WhenUserIsDisabled() {
        activeUser.setEnabled(false);
        when(userRepository.findByEmail("thu@gmail.com")).thenReturn(Optional.of(activeUser));

        InvalidCredentialsException ex = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(validLoginRequest)
        );

        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    void getCurrentUser_ShouldReturnUserResponse_WhenJwtValid() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        UserResponse expectedResponse = new UserResponse(
                1L,
                "user1",
                "thu@gmail.com",
                "USER",
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(userMapper.toResponse(activeUser)).thenReturn(expectedResponse);

        UserResponse result = authService.getCurrentUser(jwt);

        assertNotNull(result);
        assertEquals("user1", result.getUsername());
        assertEquals("thu@gmail.com", result.getEmail());
    }

    @Test
    void getCurrentUser_ShouldThrowUserNotFoundException_WhenUserNotInDatabase() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("999");
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        UserNotFoundException ex = assertThrows(
                UserNotFoundException.class,
                () -> authService.getCurrentUser(jwt)
        );

        assertEquals("User not found", ex.getMessage());
    }

    @Test
    void getCurrentUser_ShouldThrowInvalidCredentialsException_WhenUserIsDisabled() {
        activeUser.setEnabled(false);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        InvalidCredentialsException ex = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.getCurrentUser(jwt)
        );

        assertEquals("Account is disabled", ex.getMessage());
    }
}
