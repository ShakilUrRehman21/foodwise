package com.foodwise.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodwise.expiry.ExpiryScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CoreFlowsIntegrationTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ExpiryScheduler scheduler;

    private UUID catalogId(String nameEn) {
        return jdbc.queryForObject("SELECT id FROM food_catalog WHERE name_en = ?", UUID.class, nameEn);
    }

    private String createBatch(Session s, String name, String qty, String unit, int expiryInDays, String cost) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("catalogItemId", catalogId(name), "storageLocation", "fridge",
                "quantity", qty, "unit", unit, "expiryDate", today().plusDays(expiryInDays).toString()));
        if (cost != null) {
            body.put("costInr", cost);
        }
        JsonNode r = body(mvc.perform(jsonBody(auth(post("/api/v1/kitchens/" + s.kitchenId() + "/inventory/batches"), s.token()), body))
                .andExpect(status().isCreated()).andReturn());
        return r.at("/data/id").asText();
    }

    @Test
    void unauthenticatedRequestsGetEnvelope401() throws Exception {
        mvc.perform(get("/api/v1/kitchens")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    void provisioningCreatesKitchenAndPreferencesOnce() throws Exception {
        Session s = newUser("Rohan");
        mvc.perform(auth(get("/api/v1/users/me"), s.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data.preferences.dietaryType").value("omnivore"));
        mvc.perform(auth(get("/api/v1/kitchens"), s.token())).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Rohan's Kitchen")).andExpect(jsonPath("$.data[0].role").value("owner"));
    }

    @Test
    void kitchenIsolationReturns403() throws Exception {
        Session a = newUser("Asha");
        Session b = newUser("Bilal");
        String batch = createBatch(a, "Paneer", "200", "g", 2, "95");
        mvc.perform(auth(get("/api/v1/kitchens/" + a.kitchenId() + "/inventory/batches"), b.token())).andExpect(status().isForbidden());
        mvc.perform(jsonBody(auth(patch("/api/v1/kitchens/" + a.kitchenId() + "/inventory/batches/" + batch + "/quantity"), b.token()),
                Map.of("delta", 10))).andExpect(status().isForbidden());
        // a batch id from another kitchen is invisible through one's own kitchen
        mvc.perform(auth(get("/api/v1/kitchens/" + b.kitchenId() + "/inventory/batches/" + batch), b.token())).andExpect(status().isNotFound());
    }

    @Test
    void expiryDateIsRequiredWithoutValidatedGuidance() throws Exception {
        Session s = newUser("Meera");
        mvc.perform(jsonBody(auth(post("/api/v1/kitchens/" + s.kitchenId() + "/inventory/batches"), s.token()),
                        Map.of("catalogItemId", catalogId("Milk"), "quantity", 500, "unit", "ml")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.details.expiryDate").exists());
    }

    @Test
    void deductionLedgerAndOverdraftConflict() throws Exception {
        Session s = newUser("Kiran");
        String batch = createBatch(s, "Paneer", "200", "g", 1, "100");
        String base = "/api/v1/kitchens/" + s.kitchenId() + "/inventory/batches/" + batch;
        mvc.perform(jsonBody(auth(patch(base + "/quantity"), s.token()), Map.of("delta", 50)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentQuantity").value(150.0));
        mvc.perform(jsonBody(auth(patch(base + "/quantity"), s.token()), Map.of("delta", 500)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_events WHERE batch_id = ?::uuid", Long.class, batch)).isEqualTo(2);
        // discard the rest -> waste log of 100 * 150/200 = 75
        mvc.perform(auth(delete(base).param("reason", "spoiled"), s.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimatedLossInr").value(75.0));
    }

    @Test
    void concurrentDeductionsNeverGoNegative() throws Exception {
        Session s = newUser("Dev");
        String batch = createBatch(s, "Rice", "0.5", "kg", 30, null);
        String url = "/api/v1/kitchens/" + s.kitchenId() + "/inventory/batches/" + batch + "/quantity";
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            calls.add(() -> mvc.perform(jsonBody(auth(patch(url), s.token()), Map.of("delta", 0.3))).andReturn().getResponse().getStatus());
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : pool.invokeAll(calls)) {
            statuses.add(f.get());
        }
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT current_quantity FROM inventory_batches WHERE id = ?::uuid", Double.class, batch)).isEqualTo(0.2);
    }

    @Test
    void cookIsAtomicStrictAndDeductsEarliestExpiryFirst() throws Exception {
        Session s = newUser("Lata");
        String early = createBatch(s, "Paneer", "150", "g", 1, null);
        String late = createBatch(s, "Paneer", "150", "g", 5, null);
        createBatch(s, "Spinach", "2", "bunch", 2, null);
        UUID recipe = jdbc.queryForObject("SELECT id FROM recipes WHERE slug = 'palak-paneer'", UUID.class);
        String cook = "/api/v1/kitchens/" + s.kitchenId() + "/recipes/" + recipe + "/cook";

        // 4 servings need 400 g paneer, only 300 g in stock -> 409 and nothing deducted
        mvc.perform(jsonBody(auth(post(cook), s.token()), Map.of("servings", 4))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT sum(current_quantity) FROM inventory_batches WHERE kitchen_id = ? AND custom_item_name = 'Paneer'",
                Double.class, s.kitchenId())).isEqualTo(300.0);

        // 2 servings need 200 g: 150 from the earliest batch, 50 from the later one
        mvc.perform(jsonBody(auth(post(cook), s.token()), Map.of("servings", 2))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT current_quantity FROM inventory_batches WHERE id = ?::uuid", Double.class, early)).isEqualTo(0.0);
        assertThat(jdbc.queryForObject("SELECT current_quantity FROM inventory_batches WHERE id = ?::uuid", Double.class, late)).isEqualTo(100.0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meal_plans WHERE kitchen_id = ? AND status = 'cooked'", Long.class, s.kitchenId())).isEqualTo(1);
    }

    @Test
    void schedulerDigestIsIdempotent() throws Exception {
        Session s = newUser("Nisha");
        createBatch(s, "Paneer", "200", "g", 0, null);
        jdbc.update("UPDATE user_preferences SET notify_daily_digest_time = '00:00' WHERE user_id = (SELECT user_id FROM kitchen_members WHERE kitchen_id = ?)", s.kitchenId());
        scheduler.processDailyExpiryDigests();
        scheduler.processDailyExpiryDigests();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE kitchen_id = ? AND type = 'expiry_warning'",
                Long.class, s.kitchenId())).isEqualTo(1);
    }

    @Test
    void shoppingListDedupesAndImportsWithExpiry() throws Exception {
        Session s = newUser("Arjun");
        String base = "/api/v1/kitchens/" + s.kitchenId() + "/shopping";
        UUID tomato = catalogId("Tomato");
        mvc.perform(jsonBody(auth(post(base + "/items"), s.token()), Map.of("catalogItemId", tomato, "quantity", 500)))
                .andExpect(jsonPath("$.data.added").value(1));
        JsonNode list = body(mvc.perform(jsonBody(auth(post(base + "/items"), s.token()), Map.of("catalogItemId", tomato))).andReturn());
        assertThat(list.at("/data/alreadyOnList").asInt()).isEqualTo(1);
        String listId = list.at("/data/list/id").asText();
        String itemId = list.at("/data/list/items/0/id").asText();
        mvc.perform(jsonBody(auth(patch(base + "/items/" + itemId + "/check"), s.token()), Map.of("checked", true))).andExpect(status().isOk());
        mvc.perform(jsonBody(auth(post(base + "/" + listId + "/import-to-inventory"), s.token()),
                        Map.of("items", List.of(Map.of("itemId", itemId, "storageLocation", "fridge")))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(jsonBody(auth(post(base + "/" + listId + "/import-to-inventory"), s.token()),
                        Map.of("items", List.of(Map.of("itemId", itemId, "storageLocation", "fridge", "expiryDate", today().plusDays(4).toString(), "costInr", 30)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created.length()").value(1))
                .andExpect(jsonPath("$.data.list.items.length()").value(0));
    }
}
