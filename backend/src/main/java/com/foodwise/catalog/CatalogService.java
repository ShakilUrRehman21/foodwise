package com.foodwise.catalog;

import com.foodwise.common.ApiException;
import com.foodwise.common.enums.StorageLocation;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-mostly Indian food master catalog. The catalog (~200 rows) changes only via Flyway migrations, so it is held
 * in memory for sub-millisecond autosuggest (NFR-PERF-003) and for OCR matching.
 */
@Service
public class CatalogService {

    private static final JaroWinklerSimilarity JW = new JaroWinklerSimilarity();

    private final FoodCatalogRepository catalogRepo;
    private final FoodCategoryRepository categoryRepo;

    private volatile Snapshot snapshot;

    public CatalogService(FoodCatalogRepository catalogRepo, FoodCategoryRepository categoryRepo) {
        this.catalogRepo = catalogRepo;
        this.categoryRepo = categoryRepo;
    }

    record Snapshot(List<CategoryDto> categories, Map<UUID, CategoryDto> categoriesById, Map<String, CategoryDto> categoriesBySlug,
                    List<CatalogItemDto> items, Map<UUID, CatalogItemDto> itemsById) {
    }

    private Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s == null) {
            synchronized (this) {
                s = snapshot;
                if (s == null) {
                    s = load();
                    snapshot = s;
                }
            }
        }
        return s;
    }

    /** Reloads the cache (e.g. after a catalog migration in a long-running instance). */
    public synchronized void refresh() {
        snapshot = load();
    }

    private Snapshot load() {
        List<CategoryDto> cats = categoryRepo.findAllByOrderByDisplayOrderAsc().stream()
                .map(c -> new CategoryDto(c.getId(), c.getSlug(), c.getName(), c.getIconName(), c.getColorHex(), c.getDisplayOrder()))
                .toList();
        Map<UUID, CategoryDto> byId = new HashMap<>();
        Map<String, CategoryDto> bySlug = new HashMap<>();
        cats.forEach(c -> {
            byId.put(c.id(), c);
            bySlug.put(c.slug(), c);
        });
        List<CatalogItemDto> items = catalogRepo.findAll().stream().map(i -> {
            CategoryDto c = byId.get(i.getCategoryId());
            return new CatalogItemDto(i.getId(), i.getNameEn(), i.getNameHi(), i.getCategoryId(), c == null ? null : c.slug(),
                    c == null ? null : c.name(), i.getDefaultUnit(), i.getDefaultLocation(), i.getShelfLifeRoomTempDays(),
                    i.getShelfLifeFridgeDays(), i.getShelfLifeFreezerDays(), i.isPerishable(), i.getCommonStorageTip());
        }).sorted(Comparator.comparing(CatalogItemDto::nameEn)).toList();
        Map<UUID, CatalogItemDto> itemsById = new HashMap<>();
        items.forEach(i -> itemsById.put(i.id(), i));
        return new Snapshot(cats, byId, bySlug, items, itemsById);
    }

    public List<CategoryDto> categories() {
        return snapshot().categories();
    }

    public Optional<CategoryDto> category(UUID id) {
        return id == null ? Optional.empty() : Optional.ofNullable(snapshot().categoriesById().get(id));
    }

    public Optional<CategoryDto> categoryBySlug(String slug) {
        return Optional.ofNullable(snapshot().categoriesBySlug().get(slug));
    }

    public List<CatalogItemDto> allItems() {
        return snapshot().items();
    }

    public Optional<CatalogItemDto> item(UUID id) {
        return id == null ? Optional.empty() : Optional.ofNullable(snapshot().itemsById().get(id));
    }

    public CatalogItemDto requireItem(UUID id) {
        return item(id).orElseThrow(() -> ApiException.validation("catalogItemId", "Unknown catalog item."));
    }

    /**
     * Validated catalog estimate: {@code purchaseDate + shelf life for the location}, or empty when no approved
     * guidance exists (the normal case today). Never invents a date.
     */
    public Optional<LocalDate> estimateExpiry(UUID catalogItemId, StorageLocation location, LocalDate from) {
        return item(catalogItemId).map(i -> i.shelfLifeFor(location)).map(days -> from.plusDays(days));
    }

    /** Autosuggest ranking: exact > prefix > word-prefix > substring > fuzzy, on English and vernacular names. */
    public List<CatalogItemDto> search(String query, int limit) {
        int max = Math.max(1, Math.min(limit, 50));
        if (query == null || query.isBlank()) {
            return allItems().stream().limit(max).toList();
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        record Scored(CatalogItemDto item, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (CatalogItemDto item : allItems()) {
            double s = Math.max(score(q, item.nameEn()), score(q, item.nameHi()) - 0.01);
            if (s > 0) {
                scored.add(new Scored(item, s));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed().thenComparing(sc -> sc.item().nameEn()))
                .limit(max)
                .map(Scored::item)
                .toList();
    }

    private static double score(String q, String name) {
        if (name == null) {
            return 0;
        }
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals(q)) {
            return 1.0;
        }
        if (n.startsWith(q)) {
            return 0.95 - Math.min(0.1, (n.length() - q.length()) * 0.005);
        }
        for (String word : n.split("[\\s()/-]+")) {
            if (!word.isEmpty() && word.startsWith(q)) {
                return 0.85;
            }
        }
        if (n.contains(q)) {
            return 0.75;
        }
        if (q.length() >= 3) {
            double jw = JW.apply(q, n.length() > q.length() + 3 ? n.substring(0, Math.min(n.length(), q.length() + 2)) : n);
            if (jw >= 0.85) {
                return 0.5 * jw;
            }
        }
        return 0;
    }
}
