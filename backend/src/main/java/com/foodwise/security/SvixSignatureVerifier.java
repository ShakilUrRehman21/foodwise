package com.foodwise.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Verifies Clerk webhooks signed by Svix: HMAC-SHA256 over {@code "<svix-id>.<svix-timestamp>.<body>"} with the
 * base64 secret after the {@code whsec_} prefix; header {@code svix-signature} holds space-separated {@code v1,<sig>}.
 */
public final class SvixSignatureVerifier {

    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final byte[] key;

    public SvixSignatureVerifier(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Webhook secret is not configured");
        }
        String raw = secret.startsWith("whsec_") ? secret.substring(6) : secret;
        this.key = Base64.getDecoder().decode(raw);
    }

    public boolean verify(String id, String timestamp, String signatureHeader, String body, Instant now) {
        if (id == null || timestamp == null || signatureHeader == null || body == null) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Duration.between(Instant.ofEpochSecond(ts), now).abs().compareTo(TOLERANCE) > 0) {
            return false;
        }
        byte[] expected = sign(id + "." + timestamp.trim() + "." + body);
        for (String part : signatureHeader.trim().split(" ")) {
            int comma = part.indexOf(',');
            if (comma < 0 || !"v1".equals(part.substring(0, comma))) {
                continue;
            }
            try {
                byte[] given = Base64.getDecoder().decode(part.substring(comma + 1));
                if (MessageDigest.isEqual(expected, given)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // malformed signature segment
            }
        }
        return false;
    }

    public String signForTest(String id, String timestamp, String body) {
        return "v1," + Base64.getEncoder().encodeToString(sign(id + "." + timestamp + "." + body));
    }

    private byte[] sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }
}
