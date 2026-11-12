package com.foodwise.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodwise.config.FoodwiseProperties;
import com.foodwise.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/** Optional Clerk Backend API lookup (only when CLERK_SECRET_KEY is configured). Failures are non-fatal. */
@Component
public class ClerkUserClient {

    private static final Logger log = LoggerFactory.getLogger(ClerkUserClient.class);

    private final RestClient client;

    public ClerkUserClient(FoodwiseProperties props) {
        String secret = props.auth().clerk().secretKey();
        if (secret == null || secret.isBlank()) {
            this.client = null;
        } else {
            SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
            rf.setConnectTimeout(3000);
            rf.setReadTimeout(3000);
            this.client = RestClient.builder()
                    .baseUrl(props.auth().clerk().apiUrl())
                    .requestFactory(rf)
                    .defaultHeader("Authorization", "Bearer " + secret)
                    .build();
        }
    }

    public Optional<UserService.ExternalIdentity> fetch(String clerkUserId) {
        if (client == null) {
            return Optional.empty();
        }
        try {
            JsonNode user = client.get().uri("/users/{id}", clerkUserId).retrieve().body(JsonNode.class);
            return Optional.ofNullable(user).map(ClerkPayloads::identityFromUserJson);
        } catch (Exception e) {
            log.warn("Clerk user lookup failed for {}: {}", clerkUserId, e.getMessage());
            return Optional.empty();
        }
    }
}
