package com.foodwise.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodwise.common.ApiException;
import com.foodwise.common.ApiResponse;
import com.foodwise.config.FoodwiseProperties;
import com.foodwise.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Authoritative user provisioning path: Clerk {@code user.created} / {@code user.updated} webhooks (SYSTEM_DESIGN 4.3).
 * Public endpoint; authenticity is proven by the Svix signature.
 */
@RestController
public class ClerkWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ClerkWebhookController.class);

    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final SvixSignatureVerifier verifier;

    public ClerkWebhookController(UserService userService, ObjectMapper objectMapper, FoodwiseProperties props) {
        this.userService = userService;
        this.objectMapper = objectMapper;
        String secret = props.auth().clerk().webhookSecret();
        this.verifier = secret == null || secret.isBlank() ? null : new SvixSignatureVerifier(secret);
    }

    @PostMapping("/api/webhooks/clerk")
    public ApiResponse<Void> handle(@RequestHeader(value = "svix-id", required = false) String id,
                                    @RequestHeader(value = "svix-timestamp", required = false) String timestamp,
                                    @RequestHeader(value = "svix-signature", required = false) String signature,
                                    @RequestBody String body) throws Exception {
        if (verifier == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_NOT_CONFIGURED", "Webhook secret is not configured.");
        }
        if (!verifier.verify(id, timestamp, signature, body, Instant.now())) {
            throw ApiException.unauthorized("Invalid webhook signature.");
        }
        JsonNode event = objectMapper.readTree(body);
        String type = event.path("type").asText();
        JsonNode data = event.path("data");
        switch (type) {
            case "user.created" -> userService.provisionUserIfAbsent(ClerkPayloads.identityFromUserJson(data));
            case "user.updated" -> userService.syncFromIdentity(ClerkPayloads.identityFromUserJson(data));
            // user.deleted: kitchens may be shared and the event ledger references users (ON DELETE RESTRICT),
            // so account data removal is a deliberate manual/admin process rather than an automatic cascade.
            default -> log.info("Ignoring Clerk webhook event type {}", type);
        }
        return ApiResponse.ok(null);
    }
}
