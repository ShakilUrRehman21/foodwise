package com.foodwise.security;

import com.foodwise.user.User;
import com.foodwise.user.UserService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Turns a verified JWT into a {@link FoodwiseAuthentication}. If the user row does not exist yet (webhook delayed),
 * it runs the idempotent JIT fallback {@link UserService#provisionUserIfAbsent} (SYSTEM_DESIGN 4.3).
 */
@Component
public class JwtUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserService userService;
    private final ClerkUserClient clerkUserClient;

    public JwtUserConverter(UserService userService, ClerkUserClient clerkUserClient) {
        this.userService = userService;
        this.clerkUserClient = clerkUserClient;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String subject = jwt.getSubject();
        User user = userService.findByExternalId(subject).orElseGet(() -> userService.provisionUserIfAbsent(identityFrom(jwt)));
        return new FoodwiseAuthentication(new CurrentUser(user.getId(), subject, user.getEmail()), jwt);
    }

    private UserService.ExternalIdentity identityFrom(Jwt jwt) {
        String email = firstClaim(jwt, "email", "primary_email", "email_address");
        String first = firstClaim(jwt, "first_name", "given_name");
        String last = firstClaim(jwt, "last_name", "family_name");
        String image = firstClaim(jwt, "image_url", "picture");
        UserService.ExternalIdentity identity = new UserService.ExternalIdentity(jwt.getSubject(), email, first, last, image);
        if (email == null) {
            // Clerk's default session token has no email; ask the Backend API if a secret key is configured.
            identity = clerkUserClient.fetch(jwt.getSubject()).orElse(identity);
        }
        return identity;
    }

    private static String firstClaim(Jwt jwt, String... names) {
        for (String n : names) {
            Object v = jwt.getClaims().get(n);
            if (v instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }
}
