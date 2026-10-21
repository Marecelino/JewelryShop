package com.example.login.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.hibernate.validator.internal.util.Contracts.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordEncoderTest {

    private final PasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder(12);

    @Test
    void shouldEncodeAndVerifyCorrectPassword() {
        String rawPassword = "password";

        String encodedPassword =
                passwordEncoder.encode(rawPassword);

        assertNotEquals(rawPassword, encodedPassword);

        assertTrue(
                passwordEncoder.matches(
                        rawPassword,
                        encodedPassword
                )
        );
    }

    @Test
    void shouldRejectIncorrectPassword() {
        String encodedPassword =
                passwordEncoder.encode("password");

        assertFalse(
                passwordEncoder.matches(
                        "wrong-password",
                        encodedPassword
                )
        );
    }

    @Test
    void shouldVerifyPasswordHash() {
        String hash = passwordEncoder.encode("password");
        assertTrue(passwordEncoder.matches("password", hash));
    }
}