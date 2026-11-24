package com.frugaltesting.q2;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Q2HmacReplayTest {

    private static final String BASE_URL = "http://localhost:3001";
    private static final String SECRET = "frugal-testing-local-secret";

    private String calculateHmac(
            String body,
            String entityId,
            String timestamp,
            String nonce
    ) throws Exception {

        String message =
                body + "\n" +
                entityId + "\n" +
                timestamp + "\n" +
                nonce;

        Mac mac = Mac.getInstance("HmacSHA256");

        SecretKeySpec key = new SecretKeySpec(
                SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );

        mac.init(key);

        byte[] hash = mac.doFinal(
                message.getBytes(StandardCharsets.UTF_8)
        );

        StringBuilder hex = new StringBuilder();

        for (byte value : hash) {
            hex.append(String.format("%02x", value));
        }

        return hex.toString();
    }

    @Test
    void tamperedReplayMustBeRejectedWithin300Milliseconds()
            throws Exception {

        RestAssured.baseURI = BASE_URL;

        // 1. Create a new entity.
        Response createResponse = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body("{}")
                .post("/api/entities");

        assertEquals(201, createResponse.statusCode());

        String entityId = createResponse.jsonPath().getString("id");

        assertNotNull(entityId);
        assertFalse(entityId.isBlank());

        System.out.println("Created entity: " + entityId);

        // 2. Generate runtime authentication values.
        String timestamp =
                String.valueOf(System.currentTimeMillis() / 1000);

        String nonce = UUID.randomUUID().toString();

        String originalBody = """
                {"value":"APPROVED"}
                """.trim();

        String signature = calculateHmac(
                originalBody,
                entityId,
                timestamp,
                nonce
        );

        // 3. Send the valid signed PUT.
        Response firstResponse = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature-Auth", signature)
                .body(originalBody)
                .put("/api/entities/" + entityId);

        long firstCompletionNanos = System.nanoTime();

        assertEquals(
                200,
                firstResponse.statusCode(),
                "The original signed PUT should succeed."
        );

        System.out.println("Original PUT: HTTP " +
                firstResponse.statusCode());

        // 4. Change exactly one character.
        String tamperedBody = originalBody.replace(
                "APPROVED",
                "APPROVEZ"
        );

        assertNotEquals(originalBody, tamperedBody);

        // 5. Replay with the original signature, timestamp and nonce.
        Response replayResponse = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature-Auth", signature)
                .body(tamperedBody)
                .put("/api/entities/" + entityId);

        long replayCompletionNanos = System.nanoTime();

        double elapsedMs =
                (replayCompletionNanos - firstCompletionNanos)
                        / 1_000_000.0;

        // 6. Verify timing and rejection.
        assertTrue(
                elapsedMs <= 300,
                "Tampered replay exceeded 300 ms: " + elapsedMs
        );

        assertEquals(
                401,
                replayResponse.statusCode(),
                "Tampered payload must be rejected."
        );

        assertEquals(
                "SIGNATURE_MISMATCH",
                replayResponse.jsonPath().getString("error")
        );

        System.out.println("Tampered PUT: HTTP " +
                replayResponse.statusCode());
        System.out.println("Gateway error: " +
                replayResponse.jsonPath().getString("error"));
        System.out.printf("Replay interval: %.3f ms%n", elapsedMs);
        System.out.println("Q2 HMAC REPLAY TEST: PASSED");
    }
}
