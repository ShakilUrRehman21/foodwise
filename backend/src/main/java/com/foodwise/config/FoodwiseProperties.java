package com.foodwise.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Typed application configuration (prefix {@code foodwise}). All secrets come from environment variables.
 */
@Validated
@ConfigurationProperties(prefix = "foodwise")
public record FoodwiseProperties(
        @DefaultValue("Asia/Kolkata") @NotBlank String timezone,
        @DefaultValue("http://localhost:3000") List<String> allowedOrigins,
        @DefaultValue Auth auth,
        @DefaultValue Scan scan,
        @DefaultValue Scheduler scheduler) {

    public enum AuthMode { CLERK, DEV }

    public record Auth(
            @DefaultValue("CLERK") AuthMode mode,
            @DefaultValue Clerk clerk,
            @DefaultValue Dev dev) {
    }

    /**
     * @param jwksUri         Clerk instance JWKS endpoint, e.g. https://your-app.clerk.accounts.dev/.well-known/jwks.json
     * @param issuer          expected {@code iss} claim (the Clerk frontend API URL); validated when set
     * @param authorizedParties allowed {@code azp} values (frontend origins); validated when non-empty
     * @param webhookSecret   Svix signing secret ({@code whsec_...}) for POST /api/webhooks/clerk
     * @param secretKey       optional Clerk secret key, used only to look up a user's email when the JWT lacks it
     * @param apiUrl          Clerk Backend API base URL
     */
    public record Clerk(
            String jwksUri,
            String issuer,
            @DefaultValue List<String> authorizedParties,
            String webhookSecret,
            String secretKey,
            @DefaultValue("https://api.clerk.com/v1") String apiUrl) {
    }

    /** Local development auth: the backend signs its own HS256 tokens. Never enabled in prod. */
    public record Dev(
            @DefaultValue("change-me-dev-only-secret-at-least-32-bytes-long!!") String jwtSecret,
            @DefaultValue("30") int tokenDays) {
    }

    public record Scan(
            @DefaultValue("./data/uploads") String storageDir,
            String tessdataPath,
            @DefaultValue("eng") String ocrLanguage,
            String haulModelPath,
            String haulLabelsPath,
            @DefaultValue("0.5") double haulConfidenceThreshold,
            @DefaultValue("0.75") double matchThreshold,
            @DefaultValue("20") int maxScansPerHour) {
    }

    public record Scheduler(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("0 0/15 * * * *") String digestCron,
            @DefaultValue("0 30 8 * * *") String expiredCron) {
    }
}
