package com.foodwise.scan;

import com.foodwise.common.enums.Unit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based parser for OCR text of Indian grocery receipts (Roadmap 4.B4). Handles the common layouts:
 * <ul>
 *   <li>{@code Name  [pack size]  amount} (Blinkit / Zepto style)</li>
 *   <li>{@code Name  qty  rate  amount} (kirana / supermarket POS columns)</li>
 *   <li>{@code Name  2 x 38.00  76.00} (multiplier)</li>
 *   <li>two-line items where the name is followed by a numbers-only line (D-Mart style)</li>
 * </ul>
 * Header/footer lines (totals, taxes, payment, addresses) are skipped. Pure and deterministic.
 */
public final class ReceiptTextParser {

    /** {@code suspicious} = the printed numbers don't add up (qty x rate != amount), so the row needs a closer look. */
    public record ReceiptLine(String rawText, String name, BigDecimal quantity, Unit unit, BigDecimal price, boolean suspicious) {
    }

    public record ParsedReceipt(String storeName, LocalDate purchaseDate, BigDecimal totalAmount, List<ReceiptLine> lines) {
    }

    private static final Pattern SKIP = Pattern.compile(
            "(?i)\\b(sub\\s*-?\\s*total|grand\\s*total|total|net\\s*amount|amount\\s*payable|payable|gst|cgst|sgst|igst|cess|tax|taxable|"
                    + "invoice|bill\\s*no|receipt|order\\s*(id|no|#)|date|time|phone|ph|tel|mob(ile)?|gstin|fssai|cin|pan|address|"
                    + "thank|thanks|visit|again|cash|card|upi|paytm|gpay|phonepe|paid|change|balance|discount|savings?|saved|you\\s*saved|"
                    + "delivery|handling|fee|charges?|tip|round(ed)?\\s*off|hsn|sac|cashier|counter|customer|www|email|"
                    + "qty\\s*total|no\\s*of\\s*items|items?\\s*count|mrp\\s*total|particulars|description|rate\\s+amount|"
                    + "tender|refund|loyalty|points|mode)\\b");
    private static final Pattern TOTAL = Pattern.compile("(?i)(grand\\s*total|net\\s*amount|amount\\s*payable|total\\s*amount|bill\\s*amount|\\btotal\\b)");
    private static final Pattern TRAILING_NUMBERS = Pattern.compile(
            "((?:\\s+(?:₹|rs\\.?|inr)?\\s*\\d{1,6}(?:[.,]\\d{1,3})?)+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("\\d{1,6}(?:[.,]\\d{1,3})?");
    private static final Pattern PACK = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?)\\s*(kgs?|kilo|grams?|gms?|gm|g|ml|ltrs?|litres?|liters?|lt|l|pcs|pc|pieces?|pkts?|packets?|nos|bunch(?:es)?|dozen|dz)\\b");
    private static final Pattern MULTIPLIER = Pattern.compile("(?i)(?:\\b(\\d{1,3})\\s*[x×*]\\s*(\\d{1,6}(?:[.,]\\d{1,3})?))|(?:\\bqty\\s*:?\\s*(\\d{1,3}))");
    private static final Pattern LEADING_INDEX = Pattern.compile("^\\s*(?:\\d{1,3}[.)\\]:-]|#\\d+)\\s+");
    private static final Pattern LONG_CODES = Pattern.compile("\\b\\d{5,}\\b");
    private static final Pattern DATE = Pattern.compile(
            "\\b(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{2,4})\\b|\\b(\\d{1,2})[\\s-]+(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*[\\s,-]+(\\d{2,4})\\b",
            Pattern.CASE_INSENSITIVE);
    private static final String[] STORES = {"blinkit", "zepto", "swiggy instamart", "instamart", "bigbasket", "big basket", "d-mart",
            "dmart", "d mart", "avenue supermarts", "reliance fresh", "reliance smart", "smart bazaar", "jiomart", "spencer's", "spencers",
            "more supermarket", "more retail", "nature's basket", "star bazaar", "ratnadeep", "lulu", "metro cash", "vishal mega mart",
            "easyday", "dunzo", "amazon fresh", "flipkart"};

    private ReceiptTextParser() {
    }

    public static ParsedReceipt parse(String text) {
        if (text == null || text.isBlank()) {
            return new ParsedReceipt(null, null, null, List.of());
        }
        String[] rawLines = text.replace("\r", "").split("\n");
        List<String> lines = new ArrayList<>();
        for (String l : rawLines) {
            String t = normaliseLine(l);
            if (!t.isBlank()) {
                lines.add(t);
            }
        }
        String store = detectStore(lines);
        LocalDate date = detectDate(lines);
        BigDecimal total = detectTotal(lines);

        List<ReceiptLine> items = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (SKIP.matcher(line).find() || letters(line) < 3) {
                continue;
            }
            Optional<ReceiptLine> parsed = parseItemLine(line);
            if (parsed.isEmpty() && i + 1 < lines.size() && isNumbersOnly(lines.get(i + 1))) {
                parsed = parseItemLine(line + " " + lines.get(i + 1));
                if (parsed.isPresent()) {
                    i++;
                }
            }
            parsed.ifPresent(items::add);
        }
        return new ParsedReceipt(store, date, total, items);
    }

    static Optional<ReceiptLine> parseItemLine(String rawLine) {
        String line = rawLine;
        BigDecimal count = null;
        Matcher mult = MULTIPLIER.matcher(line);
        if (mult.find()) { // "2 x 38.00" or "Qty 2": take the count, drop the unit price
            String c = mult.group(1) != null ? mult.group(1) : mult.group(3);
            count = c == null ? null : new BigDecimal(c);
            line = (line.substring(0, mult.start()) + " " + line.substring(mult.end())).trim();
        }
        Matcher tail = TRAILING_NUMBERS.matcher(line);
        if (!tail.find()) {
            return Optional.empty();
        }
        String head = line.substring(0, tail.start()).trim();
        List<BigDecimal> nums = new ArrayList<>();
        Matcher n = NUMBER.matcher(tail.group(1));
        while (n.find()) {
            nums.add(new BigDecimal(n.group().replace(',', '.')));
        }
        if (nums.isEmpty() || letters(head) < 3) {
            return Optional.empty();
        }
        BigDecimal price = nums.getLast();
        boolean suspicious = false;
        if (count == null) {
            if (nums.size() >= 3) {
                count = nums.get(nums.size() - 3); // qty, rate, amount
                BigDecimal expected = count.multiply(nums.get(nums.size() - 2));
                if (expected.signum() > 0 && price.subtract(expected).abs().compareTo(BigDecimal.ONE) > 0) {
                    // OCR damaged a digit: trust qty x rate when the amount is implausibly far off, and flag the row.
                    suspicious = true;
                    if (price.compareTo(expected.multiply(BigDecimal.valueOf(3))) > 0 || price.multiply(BigDecimal.valueOf(3)).compareTo(expected) < 0) {
                        price = expected;
                    }
                }
            } else if (nums.size() == 2 && isSmallInteger(nums.getFirst()) && nums.getFirst().compareTo(price) < 0) {
                count = nums.getFirst(); // qty, amount
            }
        }

        BigDecimal quantity = null;
        Unit unit = null;
        Matcher pack = PACK.matcher(head);
        if (pack.find()) {
            BigDecimal size = new BigDecimal(pack.group(1));
            String u = pack.group(2).toLowerCase(Locale.ROOT);
            if (u.equals("dozen") || u.equals("dz")) {
                size = size.multiply(BigDecimal.valueOf(12));
            }
            unit = unitFor(u);
            quantity = size;
            head = (head.substring(0, pack.start()) + " " + head.substring(pack.end())).trim();
        }
        if (count != null && count.signum() > 0) {
            if (quantity == null) {
                quantity = count;
                // a fractional quantity with no unit on a POS bill is a weighed item in kg (e.g. "Potato 1.250 32.00 40.00")
                unit = count.stripTrailingZeros().scale() > 0 ? Unit.KG : Unit.PCS;
            } else {
                quantity = quantity.multiply(count);
            }
        }
        String name = cleanName(head);
        if (name.length() < 2 || price.signum() <= 0 || price.compareTo(new BigDecimal("50000")) > 0) {
            return Optional.empty();
        }
        return Optional.of(new ReceiptLine(rawLine, name, quantity == null ? null : quantity.setScale(2, RoundingMode.HALF_UP), unit,
                price.setScale(2, RoundingMode.HALF_UP), suspicious));
    }

    static Unit unitFor(String u) {
        if (u.startsWith("kg") || u.equals("kilo")) {
            return Unit.KG;
        }
        if (u.startsWith("g")) {
            return Unit.G;
        }
        if (u.equals("ml")) {
            return Unit.ML;
        }
        if (u.startsWith("l")) {
            return Unit.L;
        }
        if (u.startsWith("pkt") || u.startsWith("packet")) {
            return Unit.PKT;
        }
        if (u.startsWith("bunch")) {
            return Unit.BUNCH;
        }
        return Unit.PCS;
    }

    static String cleanName(String head) {
        String s = LEADING_INDEX.matcher(head).replaceFirst("");
        s = LONG_CODES.matcher(s).replaceAll(" ");
        s = s.replaceAll("(?i)\\b(rs\\.?|inr|mrp)\\b", " ").replace("₹", " ");
        s = s.replaceAll("[^\\p{L}\\p{N}&'()/\\- ]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("^[\\-/()]+|[\\-/(]+$", "").trim();
        return s.length() > 100 ? s.substring(0, 100) : s;
    }

    /** Fixes common OCR confusions inside numeric tokens (O->0, l/I->1) and unifies currency marks. */
    static String normaliseLine(String line) {
        String s = line.replace('\t', ' ').replace("|", " ").replace("Rs.", "Rs ").replace("₹ ", "₹").trim();
        // OCR often appends stray marks after the amount column (e.g. "95.00   ;" or "45.00 \\").
        s = s.replaceAll("[\\s;:'\"`~|\\\\/?!,_*-]+$", "").trim();
        StringBuilder out = new StringBuilder();
        for (String tok : s.split(" +")) {
            if (tok.matches("[0-9OoIl.,]+") && tok.chars().anyMatch(Character::isDigit) && tok.length() > 1) {
                tok = tok.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1');
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(tok);
        }
        return out.toString();
    }

    private static boolean isSmallInteger(BigDecimal v) {
        return v.stripTrailingZeros().scale() <= 0 && v.compareTo(BigDecimal.ZERO) > 0 && v.compareTo(BigDecimal.valueOf(50)) <= 0;
    }

    private static boolean isNumbersOnly(String line) {
        return letters(line.replaceAll("(?i)\\b(rs|inr|x|kg|g|gm|ml|l|pcs|nos|pkt)\\b", "")) == 0 && NUMBER.matcher(line).find();
    }

    private static int letters(String s) {
        int c = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                c++;
            }
        }
        return c;
    }

    static String detectStore(List<String> lines) {
        for (int i = 0; i < Math.min(lines.size(), 8); i++) {
            String l = lines.get(i).toLowerCase(Locale.ROOT);
            for (String store : STORES) {
                if (l.contains(store)) {
                    return titleCase(store);
                }
            }
        }
        return null;
    }

    private static String titleCase(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) {
            if (!b.isEmpty()) {
                b.append(' ');
            }
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString().replace("D-mart", "D-Mart").replace("Dmart", "DMart");
    }

    static LocalDate detectDate(List<String> lines) {
        DateTimeFormatter mon = new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMM uuuu").toFormatter(Locale.ENGLISH);
        for (String l : lines) {
            Matcher m = DATE.matcher(l);
            while (m.find()) {
                try {
                    if (m.group(1) != null) {
                        int d = Integer.parseInt(m.group(1));
                        int mo = Integer.parseInt(m.group(2));
                        int y = Integer.parseInt(m.group(3));
                        if (y < 100) {
                            y += 2000;
                        }
                        LocalDate date = LocalDate.of(y, mo, d); // Indian receipts are day-first
                        if (plausible(date)) {
                            return date;
                        }
                    } else {
                        String month = m.group(5).substring(0, 3);
                        int y = Integer.parseInt(m.group(6));
                        LocalDate date = LocalDate.parse(m.group(4) + " " + month + " " + (y < 100 ? y + 2000 : y), mon);
                        if (plausible(date)) {
                            return date;
                        }
                    }
                } catch (RuntimeException ignored) {
                    // not a valid date, keep looking
                }
            }
        }
        return null;
    }

    private static boolean plausible(LocalDate d) {
        LocalDate now = LocalDate.now();
        return !d.isAfter(now.plusDays(1)) && d.isAfter(now.minusYears(2));
    }

    static BigDecimal detectTotal(List<String> lines) {
        BigDecimal best = null;
        for (String l : lines) {
            if (TOTAL.matcher(l).find() && !l.toLowerCase(Locale.ROOT).contains("sub")
                    && !l.toLowerCase(Locale.ROOT).matches(".*\\b(qty|items?)\\b.*")) {
                Matcher tail = TRAILING_NUMBERS.matcher(l);
                if (tail.find()) {
                    Matcher n = NUMBER.matcher(tail.group(1));
                    BigDecimal last = null;
                    while (n.find()) {
                        last = new BigDecimal(n.group().replace(',', '.'));
                    }
                    if (last != null && (best == null || last.compareTo(best) > 0)) {
                        best = last;
                    }
                }
            }
        }
        return best == null ? null : best.setScale(2, RoundingMode.HALF_UP);
    }
}
