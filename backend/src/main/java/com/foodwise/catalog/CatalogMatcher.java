package com.foodwise.catalog;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic fuzzy matcher from noisy OCR / detector text to catalog entries (Roadmap 4.B5).
 * Strategy: normalise text, then for every catalog name (English, vernacular, curated aliases) score either exact
 * contiguous-token containment (1.0, longer names win ties) or the best Jaro-Winkler similarity over token windows.
 * Short tokens (<= 3 chars) must match exactly to avoid false positives such as "oil" ~ "toor".
 */
@Component
public class CatalogMatcher {

    private static final JaroWinklerSimilarity JW = new JaroWinklerSimilarity();

    /** Brand and packaging words common on Indian receipts that carry no item identity. */
    private static final Set<String> NOISE = Set.of(
            "fresh", "freshly", "organic", "premium", "select", "loose", "pack", "packet", "pouch", "pkt", "pcs", "pc", "kg", "gm",
            "gms", "g", "ml", "ltr", "l", "lt", "nos", "no", "x", "qty", "the", "of", "and", "with", "hybrid", "desi", "local",
            "regular", "classic", "special", "new", "big", "small", "medium", "large", "approx", "per", "rs", "inr", "mrp",
            "amul", "tata", "sampann", "aashirvaad", "fortune", "saffola", "britannia", "nandini", "heritage", "akshayakalpa",
            "mother", "dairy", "gowardhan", "fresho", "bb", "royal", "good", "life", "goodlife", "patanjali", "everest", "mdh",
            "catch", "kissan", "maggi", "nestle", "haldirams", "haldiram", "bikaji", "parle", "sunfeast", "milky", "mist",
            "milkymist", "dmart", "premia", "zepto", "blinkit", "instamart", "taaza", "gold", "toned", "double", "full",
            "slim", "trim", "homogenised", "pasteurised", "shakti", "chakki", "superior", "quality", "grade", "a", "ripe");

    // NOTE: "cream" is deliberately not noise (Fresh Cream). Ties are resolved by specificity and head-noun position.

    /** Curated aliases (receipt wording and synonyms) -> catalog name_en. Extends matching without schema changes. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("dahi", "Curd"), Map.entry("yogurt", "Curd"), Map.entry("yoghurt", "Curd"),
            Map.entry("atta", "Whole Wheat Flour"), Map.entry("wheat flour", "Whole Wheat Flour"),
            Map.entry("maida", "Refined Flour"), Map.entry("besan", "Gram Flour"), Map.entry("rava", "Semolina"),
            Map.entry("sooji", "Semolina"), Map.entry("suji", "Semolina"),
            Map.entry("coriander", "Coriander Leaves"), Map.entry("dhania", "Coriander Leaves"),
            Map.entry("cilantro", "Coriander Leaves"), Map.entry("mint", "Mint Leaves"),
            Map.entry("lady finger", "Okra"), Map.entry("ladyfinger", "Okra"), Map.entry("bhindi", "Okra"),
            Map.entry("eggplant", "Brinjal"), Map.entry("baingan", "Brinjal"),
            Map.entry("chilli green", "Green Chilli"), Map.entry("green chillies", "Green Chilli"),
            Map.entry("chillies green", "Green Chilli"), Map.entry("mirchi", "Green Chilli"),
            Map.entry("capsicum green", "Capsicum"), Map.entry("bell pepper", "Capsicum"),
            Map.entry("egg", "Eggs"), Map.entry("eggs white", "Eggs"), Map.entry("anda", "Eggs"),
            Map.entry("milk", "Milk"), Map.entry("doodh", "Milk"),
            Map.entry("cottage cheese", "Paneer"), Map.entry("malai paneer", "Paneer"),
            Map.entry("arhar", "Toor Dal"), Map.entry("tuvar dal", "Toor Dal"), Map.entry("toor", "Toor Dal"),
            Map.entry("tur dal", "Toor Dal"), Map.entry("moong", "Moong Dal"), Map.entry("masoor", "Masoor Dal"),
            Map.entry("chickpeas", "Kabuli Chana"), Map.entry("chole", "Kabuli Chana"), Map.entry("kidney beans", "Rajma"),
            Map.entry("aloo", "Potato"), Map.entry("pyaz", "Onion"), Map.entry("pyaaz", "Onion"), Map.entry("kanda", "Onion"),
            Map.entry("tamatar", "Tomato"), Map.entry("lehsun", "Garlic"), Map.entry("adrak", "Ginger"),
            Map.entry("palak", "Spinach"), Map.entry("methi", "Fenugreek Leaves"), Map.entry("gobi", "Cauliflower"),
            Map.entry("phool gobi", "Cauliflower"), Map.entry("patta gobi", "Cabbage"), Map.entry("matar", "Green Peas"),
            Map.entry("peas", "Green Peas"), Map.entry("lauki", "Bottle Gourd"), Map.entry("doodhi", "Bottle Gourd"),
            Map.entry("karela", "Bitter Gourd"), Map.entry("nimbu", "Lemon"), Map.entry("lime", "Lemon"),
            Map.entry("kela", "Banana"), Map.entry("bread white", "Bread"), Map.entry("sandwich bread", "Bread"),
            Map.entry("pav", "Pav"), Map.entry("ladi pav", "Pav"), Map.entry("butter salted", "Butter"),
            Map.entry("refined oil", "Cooking Oil"), Map.entry("sunflower oil", "Cooking Oil"),
            Map.entry("soyabean oil", "Cooking Oil"), Map.entry("groundnut oil", "Cooking Oil"), Map.entry("oil", "Cooking Oil"),
            Map.entry("kachi ghani", "Mustard Oil"), Map.entry("chai", "Tea Leaves"), Map.entry("tea", "Tea Leaves"),
            Map.entry("haldi", "Turmeric Powder"), Map.entry("turmeric", "Turmeric Powder"),
            Map.entry("jeera", "Cumin Seeds"), Map.entry("cumin", "Cumin Seeds"), Map.entry("namak", "Salt"),
            Map.entry("iodised salt", "Salt"), Map.entry("chini", "Sugar"), Map.entry("cheeni", "Sugar"),
            Map.entry("noodles", "Instant Noodles"), Map.entry("idli batter", "Idli Dosa Batter"),
            Map.entry("dosa batter", "Idli Dosa Batter"), Map.entry("ketchup", "Tomato Ketchup"),
            Map.entry("chicken curry cut", "Chicken"), Map.entry("chicken boneless", "Boneless Chicken"),
            Map.entry("keema", "Minced Meat"), Map.entry("mutton curry cut", "Mutton"));

    private final CatalogService catalog;

    public CatalogMatcher(CatalogService catalog) {
        this.catalog = catalog;
    }

    public record Match(CatalogItemDto item, double score, String matchedName) {
    }

    /** Best match regardless of threshold (callers decide), or empty if nothing is remotely similar. */
    public Optional<Match> bestMatch(String rawText) {
        List<String> tokens = tokens(rawText);
        if (tokens.isEmpty()) {
            return Optional.empty();
        }
        Candidate best = null;
        for (CatalogItemDto item : catalog.allItems()) {
            for (String name : namesFor(item)) {
                best = better(best, new Candidate(item, name, score(tokens, tokens(name))));
            }
        }
        for (Map.Entry<String, String> alias : ALIASES.entrySet()) {
            CatalogItemDto target = byName(alias.getValue());
            if (target != null) {
                best = better(best, new Candidate(target, alias.getKey(), score(tokens, tokens(alias.getKey()))));
            }
        }
        return best == null || best.score().value() < 0.5
                ? Optional.empty()
                : Optional.of(new Match(best.item(), best.score().value(), best.name()));
    }

    private record Candidate(CatalogItemDto item, String name, Score score) {
    }

    /** Higher similarity wins; on ties prefer more name tokens ("Basmati Rice" over "Rice"), then the later
     *  position in the text, since product names end with the head noun ("Full Cream Milk" is milk). */
    private static Candidate better(Candidate current, Candidate next) {
        if (next.score().value() <= 0) {
            return current;
        }
        if (current == null) {
            return next;
        }
        Score a = current.score();
        Score b = next.score();
        if (Math.abs(a.value() - b.value()) > 1e-9) {
            return b.value() > a.value() ? next : current;
        }
        if (a.tokens() != b.tokens()) {
            return b.tokens() > a.tokens() ? next : current;
        }
        return b.endPosition() > a.endPosition() ? next : current;
    }

    record Score(double value, int tokens, int endPosition) {
        static final Score NONE = new Score(0, 0, -1);
    }

    private CatalogItemDto byName(String nameEn) {
        for (CatalogItemDto i : catalog.allItems()) {
            if (i.nameEn().equalsIgnoreCase(nameEn)) {
                return i;
            }
        }
        return null;
    }

    private static List<String> namesFor(CatalogItemDto item) {
        List<String> names = new ArrayList<>(2);
        names.add(item.nameEn());
        if (item.nameHi() != null) {
            names.add(item.nameHi());
        }
        return names;
    }

    static Score score(List<String> text, List<String> name) {
        if (name.isEmpty() || text.isEmpty()) {
            return Score.NONE;
        }
        int k = name.size();
        for (int i = text.size() - k; i >= 0; i--) { // exact contiguous containment, right-most first
            if (text.subList(i, i + k).equals(name)) {
                return new Score(1.0, k, i + k);
            }
        }
        String target = String.join(" ", name);
        Score best = Score.NONE;
        for (int i = 0; i + k <= text.size(); i++) {
            String window = String.join(" ", text.subList(i, i + k));
            if (target.length() <= 3 || window.length() <= 3) {
                continue; // short words must match exactly
            }
            double v = JW.apply(window, target);
            if (v > best.value() || (v == best.value() && i + k > best.endPosition())) {
                best = new Score(v, k, i + k);
            }
        }
        if (k > 1 && text.size() < k) {
            String all = String.join(" ", text);
            if (all.length() > 3) {
                double v = JW.apply(all, target) - 0.05;
                if (v > best.value()) {
                    best = new Score(v, text.size(), text.size());
                }
            }
        }
        return best;
    }

    static List<String> tokens(String raw) {
        if (raw == null) {
            return List.of();
        }
        String cleaned = raw.toLowerCase(Locale.ROOT)
                .replaceAll("\\d+(\\.\\d+)?\\s*(kg|g|gm|gms|ml|l|ltr|lt|pcs|pc|pkt|nos)\\b", " ")
                .replaceAll("[^a-z\\s]", " ");
        List<String> out = new ArrayList<>();
        for (String t : cleaned.trim().split("\\s+")) {
            if (t.isEmpty() || NOISE.contains(t)) {
                continue;
            }
            out.add(singular(t));
        }
        return out;
    }

    private static String singular(String t) {
        if (t.length() > 4 && t.endsWith("oes")) {
            return t.substring(0, t.length() - 2); // tomatoes -> tomato, potatoes -> potato
        }
        if (t.length() > 4 && t.endsWith("ies")) {
            return t.substring(0, t.length() - 3) + "y"; // berries -> berry; chillies -> chilly (handled by JW)
        }
        if (t.length() > 3 && t.endsWith("s") && !t.endsWith("ss") && !Arrays.asList("peas", "oats", "chips", "dates", "grapes",
                "cashews", "almonds", "raisins", "peanuts", "eggs", "beans", "cloves", "seeds", "leaves", "greens", "buns",
                "slices", "nuggets", "sausages", "prawns", "shallots", "cornflakes", "flakes", "biscuits", "noodles",
                "sprouts", "makhana").contains(t)) {
            return t.substring(0, t.length() - 1);
        }
        return t;
    }
}
