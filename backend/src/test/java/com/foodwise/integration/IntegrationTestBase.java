package com.foodwise.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Boots the full application against a real PostgreSQL 16 (Flyway migrations included).
 * Uses FOODWISE_TEST_DB_URL / _USER / _PASSWORD when set, otherwise a Testcontainers Postgres
 * (skipped when Docker is unavailable).
 */
@SpringBootTest(properties = {
        "foodwise.auth.mode=DEV",
        "foodwise.scheduler.enabled=false",
        "foodwise.scan.storage-dir=${java.io.tmpdir}/foodwise-test-uploads",
        "foodwise.scan.max-scans-per-hour=1000"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public abstract class IntegrationTestBase {

    static final String EXTERNAL_DB = System.getenv("FOODWISE_TEST_DB_URL");
    static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void requireDatabase() {
        if (EXTERNAL_DB == null) {
            Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                    "Docker not available and FOODWISE_TEST_DB_URL not set - skipping integration tests");
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (EXTERNAL_DB != null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_DB);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("FOODWISE_TEST_DB_USER", "postgres"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("FOODWISE_TEST_DB_PASSWORD", ""));
            return;
        }
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            return;
        }
        if (postgres == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
        }
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;

    protected static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Kolkata"));
    }

    /** A fresh user (unique email) with its auto-provisioned kitchen. */
    protected record Session(String token, UUID kitchenId) {
    }

    protected Session newUser(String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@test.foodwise";
        JsonNode tok = body(mvc.perform(post("/api/v1/dev/token").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "firstName", name)))).andReturn());
        String token = tok.at("/data/token").asText();
        JsonNode kitchens = body(mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/kitchens"), token)).andReturn());
        return new Session(token, UUID.fromString(kitchens.at("/data/0/id").asText()));
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token);
    }

    protected MockHttpServletRequestBuilder jsonBody(MockHttpServletRequestBuilder b, Object body) throws Exception {
        return b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    protected JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }
}
