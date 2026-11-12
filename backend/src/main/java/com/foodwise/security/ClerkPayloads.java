package com.foodwise.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodwise.user.UserService;

/** Parsing helpers for Clerk user JSON (webhook {@code data} object and Backend API user resource). */
final class ClerkPayloads {

    private ClerkPayloads() {
    }

    static UserService.ExternalIdentity identityFromUserJson(JsonNode u) {
        String primaryId = text(u, "primary_email_address_id");
        String email = null;
        JsonNode emails = u.path("email_addresses");
        if (emails.isArray()) {
            for (JsonNode e : emails) {
                if (email == null || (primaryId != null && primaryId.equals(text(e, "id")))) {
                    email = text(e, "email_address");
                }
            }
        }
        return new UserService.ExternalIdentity(text(u, "id"), email, text(u, "first_name"), text(u, "last_name"), text(u, "image_url"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
