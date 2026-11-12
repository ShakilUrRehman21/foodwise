package com.foodwise.security;

import java.util.UUID;

/** Authenticated principal resolved from the JWT and the local {@code users} row. */
public record CurrentUser(UUID id, String externalId, String email) {
}
