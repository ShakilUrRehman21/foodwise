package com.foodwise.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;

/** Authentication whose principal is the resolved local {@link CurrentUser}. */
public class FoodwiseAuthentication extends AbstractAuthenticationToken {

    private final CurrentUser principal;
    private final Jwt jwt;

    public FoodwiseAuthentication(CurrentUser principal, Jwt jwt) {
        super(AuthorityUtils.createAuthorityList("ROLE_USER"));
        this.principal = principal;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }

    @Override
    public CurrentUser getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.id().toString();
    }
}
