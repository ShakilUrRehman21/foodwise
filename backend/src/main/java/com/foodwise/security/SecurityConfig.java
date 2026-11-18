package com.foodwise.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodwise.common.ApiResponse;
import com.foodwise.config.FoodwiseProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stateless JWT security (SYSTEM_DESIGN 4.2). Public: health, Clerk webhook, API docs (dev) and dev sign-in (dev).
 * Everything else under /api requires a valid bearer token.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    public static final String DEV_ISSUER = "foodwise-dev";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtUserConverter jwtUserConverter, ObjectMapper objectMapper,
                                            FoodwiseProperties props) throws Exception {
        boolean dev = props.auth().mode() == FoodwiseProperties.AuthMode.DEV;
        http
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/health").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/health/**").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/webhooks/clerk").permitAll();
                    auth.requestMatchers("/api/docs", "/api/docs/**", "/swagger-ui/**").permitAll();
                    if (dev) {
                        auth.requestMatchers(HttpMethod.POST, "/api/v1/dev/token").permitAll();
                    }
                    auth.requestMatchers("/error").permitAll();
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(jwtUserConverter))
                        .authenticationEntryPoint(entryPoint(objectMapper))
                        .accessDeniedHandler(deniedHandler(objectMapper)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint(objectMapper))
                        .accessDeniedHandler(deniedHandler(objectMapper)))
                .headers(h -> h
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(FoodwiseProperties props, Environment env) {
        FoodwiseProperties.Auth auth = props.auth();
        if (auth.mode() == FoodwiseProperties.AuthMode.DEV) {
            if (env.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("foodwise.auth.mode=DEV is not allowed with the prod profile. Configure Clerk.");
            }
            byte[] secret = auth.dev().jwtSecret().getBytes(StandardCharsets.UTF_8);
            if (secret.length < 32) {
                throw new IllegalStateException("foodwise.auth.dev.jwt-secret must be at least 32 bytes");
            }
            log.warn("**** FoodWise is running with DEV authentication. Never use this mode in production. ****");
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret, "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(DEV_ISSUER));
            return decoder;
        }

        FoodwiseProperties.Clerk clerk = auth.clerk();
        if (clerk.jwksUri() == null || clerk.jwksUri().isBlank()) {
            throw new IllegalStateException("CLERK_JWKS_URI must be set when foodwise.auth.mode=CLERK");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(clerk.jwksUri()).build();
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(clerk.issuer() == null || clerk.issuer().isBlank()
                ? JwtValidators.createDefault()
                : JwtValidators.createDefaultWithIssuer(clerk.issuer()));
        if (!clerk.authorizedParties().isEmpty()) {
            validators.add(authorizedPartyValidator(clerk.authorizedParties()));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    /** Clerk recommends checking {@code azp} against the frontend origins to prevent token reuse across sites. */
    private static OAuth2TokenValidator<Jwt> authorizedPartyValidator(List<String> allowed) {
        return jwt -> {
            String azp = jwt.getClaimAsString("azp");
            if (azp == null || allowed.contains(azp)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Unexpected authorized party", null));
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(FoodwiseProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id", "Content-Disposition"));
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static AuthenticationEntryPoint entryPoint(ObjectMapper mapper) {
        return (request, response, ex) -> write(mapper, response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication required. Please sign in again.");
    }

    private static AccessDeniedHandler deniedHandler(ObjectMapper mapper) {
        return (request, response, ex) -> write(mapper, response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                "You do not have access to this resource.");
    }

    private static void write(ObjectMapper mapper, HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), ApiResponse.error(code, message, Map.of()));
    }
}
