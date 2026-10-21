package com.example.login;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

@SpringBootTest
@Import(LoginServiceApplicationTests.TestKeyConfig.class)
class LoginServiceApplicationTests {

    @TestConfiguration
    static class TestKeyConfig {
        @Bean
        KeyPair testKeyPair() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }
    }

    @Test
    void contextLoads() {
    }

}
