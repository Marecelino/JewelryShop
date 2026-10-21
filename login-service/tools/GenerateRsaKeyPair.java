package com.example.login.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class GenerateRsaKeyPair {

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);

        KeyPair keyPair = generator.generateKeyPair();

        Path keyDirectory = Path.of("keys");
        Files.createDirectories(keyDirectory);

        Files.writeString(
                keyDirectory.resolve("private_key.pem"),
                toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded())
        );

        Files.writeString(
                keyDirectory.resolve("public_key.pem"),
                toPem("PUBLIC KEY", keyPair.getPublic().getEncoded())
        );

        System.out.println("RSA keys generated successfully.");
    }

    private static String toPem(String type, byte[] keyBytes) {
        String encoded = Base64.getMimeEncoder(
                64,
                "\n".getBytes(StandardCharsets.US_ASCII)
        ).encodeToString(keyBytes);

        return "-----BEGIN " + type + "-----\n"
                + encoded
                + "\n-----END " + type + "-----\n";
    }
}