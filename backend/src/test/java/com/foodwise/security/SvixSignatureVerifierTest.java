package com.foodwise.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SvixSignatureVerifierTest {

    private final String secret = "whsec_" + Base64.getEncoder().encodeToString("super-secret-signing-key-123456".getBytes());
    private final SvixSignatureVerifier verifier = new SvixSignatureVerifier(secret);

    @Test
    void acceptsValidSignature() {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = verifier.signForTest("msg_1", ts, "{\"type\":\"user.created\"}");
        assertThat(verifier.verify("msg_1", ts, "v1,bogus " + sig, "{\"type\":\"user.created\"}", Instant.now())).isTrue();
    }

    @Test
    void rejectsTamperedBodyOldTimestampAndMissingHeaders() {
        String ts = String.valueOf(Instant.now().getEpochSecond());
        String sig = verifier.signForTest("msg_1", ts, "{}");
        assertThat(verifier.verify("msg_1", ts, sig, "{\"x\":1}", Instant.now())).isFalse();
        assertThat(verifier.verify("msg_1", ts, sig, "{}", Instant.now().plusSeconds(600))).isFalse();
        assertThat(verifier.verify(null, ts, sig, "{}", Instant.now())).isFalse();
    }
}
