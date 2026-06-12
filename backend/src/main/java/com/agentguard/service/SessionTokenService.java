package com.agentguard.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SessionTokenService {

    private final byte[] secretKey;

    public SessionTokenService() {
        // Cryptographically secure runtime key
        this.secretKey = new byte[32];
        new SecureRandom().nextBytes(this.secretKey);
    }

    /**
     * Generates a signed composite token mapping the 3-Layer Identity model:
     * Format: Base64(user_id:device_id:agent_id) + "." + Base64(HMAC_SHA256(payload, key))
     */
    public String generateCompositeToken(String userId, String deviceId, String agentId) {
        try {
            String compositePayload = userId + ":" + deviceId + ":" + agentId;
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(compositePayload.getBytes(StandardCharsets.UTF_8));
            String signature = calculateHmac(encodedPayload);
            return encodedPayload + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate secure composite token.", e);
        }
    }

    /**
     * Validates signature and extracts individual 3-layer identity details.
     * Returns an array: [userId, deviceId, agentId]
     */
    public String[] validateAndExtractIdentity(String token) {
        if (token == null || !token.contains(".")) {
            throw new IllegalArgumentException("Invalid token: Cryptographic signature payload missing.");
        }

        // Support default human local sessions (unsigned) as baseline
        if (token.equals("development-human")) {
            return new String[]{"nataraj", "ubuntu-local", "development-human"};
        }

        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed token signature structure.");
        }

        String encodedPayload = parts[0];
        String providedSignature = parts[1];

        try {
            // Verify HMAC signature integrity
            String calculatedSignature = calculateHmac(encodedPayload);
            if (!calculatedSignature.equals(providedSignature)) {
                throw new SecurityException("Mismatched security token signature! Token cannot be trusted.");
            }

            byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedPayload);
            String compositePayload = new String(decodedBytes, StandardCharsets.UTF_8);
            
            String[] identityParts = compositePayload.split(":");
            if (identityParts.length != 3) {
                throw new IllegalArgumentException("Composite token payload structure is corrupt.");
            }

            return identityParts;
        } catch (Exception e) {
            throw new SecurityException("Cryptographic identity validation failed: " + e.getMessage(), e);
        }
    }

    private String calculateHmac(String data) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(this.secretKey, "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
    }
}
