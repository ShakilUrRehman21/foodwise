package com.foodwise.recipe;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * In-memory snapshot of recipes with their ingredient lists. Seed recipes change only via Flyway, so scoring ~80
 * recipes against a kitchen is a fast in-memory loop with no N+1 queries.
 */
@Component
public class RecipeCatalog {

    private final RecipeRepository recipeRepo;
    private final RecipeIngredientRepository ingredientRepo;
    private volatile Snapshot snapshot;

    public RecipeCatalog(RecipeRepository recipeRepo, RecipeIngredientRepository ingredientRepo) {
        this.recipeRepo = recipeRepo;
        this.ingredientRepo = ingredientRepo;
    }

    public record Entry(Recipe recipe, List<RecipeIngredient> ingredients) {
    }

    private record Snapshot(List<Entry> entries, Map<UUID, Entry> byId, Map<String, Entry> bySlug) {
    }

    private Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s == null) {
            synchronized (this) {
                if (snapshot == null) {
                    snapshot = load();
                }
                s = snapshot;
            }
        }
        return s;
    }

    public synchronized void refresh() {
        snapshot = load();
    }

    private Snapshot load() {
        Map<UUID, List<RecipeIngredient>> ingredients = ingredientRepo.findAll().stream()
                .sorted(Comparator.comparingInt(RecipeIngredient::getDisplayOrder))
                .collect(Collectors.groupingBy(RecipeIngredient::getRecipeId, LinkedHashMap::new, Collectors.toList()));
        List<Entry> entries = recipeRepo.findAll().stream()
                .sorted(Comparator.comparing(Recipe::getTitle))
                .map(r -> new Entry(r, List.copyOf(ingredients.getOrDefault(r.getId(), List.of()))))
                .toList();
        Map<UUID, Entry> byId = new HashMap<>();
        Map<String, Entry> bySlug = new HashMap<>();
        entries.forEach(e -> {
            byId.put(e.recipe().getId(), e);
            bySlug.put(e.recipe().getSlug(), e);
        });
        return new Snapshot(entries, byId, bySlug);
    }

    public List<Entry> all() {
        return snapshot().entries();
    }

    public Optional<Entry> byId(UUID id) {
        return Optional.ofNullable(snapshot().byId().get(id));
    }

    /** Accepts a slug or a UUID string. */
    public Optional<Entry> byIdOrSlug(String key) {
        Entry e = snapshot().bySlug().get(key);
        if (e == null) {
            try {
                e = snapshot().byId().get(UUID.fromString(key));
            } catch (IllegalArgumentException ignored) {
                // not a UUID
            }
        }
        return Optional.ofNullable(e);
    }
}
