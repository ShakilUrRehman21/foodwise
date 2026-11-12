package com.foodwise.security;

import com.foodwise.common.ApiResponse;
import com.foodwise.config.FoodwiseProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;

/**
 * Local development sign-in (foodwise.auth.mode=DEV only). Issues HS256 tokens so the app runs without a Clerk
 * account. The bean does not exist in CLERK mode, and startup fails if DEV mode is combined with the prod profile.
 */
@RestController
@ConditionalOnProperty(prefix = "foodwise.auth", name = "mode", havingValue = "DEV")
public class DevAuthController {

    private final FoodwiseProperties props;

    public DevAuthController(FoodwiseProperties props) {
        this.props = props;
    }

    public record DevLoginRequest(@NotBlank @Email @Size(max = 255) String email,
                                  @Size(max = 100) String firstName,
                                  @Size(max = 100) String lastName) {
    }

    public record DevTokenResponse(String token, Instant expiresAt) {
    }

    @PostMapping("/api/v1/dev/token")
    public ApiResponse<DevTokenResponse> token(@Valid @RequestBody DevLoginRequest req) throws JOSEException {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        Instant exp = now.plus(props.auth().dev().tokenDays(), ChronoUnit.DAYS);
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("dev_" + email)
                .issuer(SecurityConfig.DEV_ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .claim("email", email);
        if (req.firstName() != null && !req.firstName().isBlank()) {
            claims.claim("first_name", req.firstName().trim());
        }
        if (req.lastName() != null && !req.lastName().isBlank()) {
            claims.claim("last_name", req.lastName().trim());
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(props.auth().dev().jwtSecret().getBytes(StandardCharsets.UTF_8)));
        return ApiResponse.ok(new DevTokenResponse(jwt.serialize(), exp));
    }
}
