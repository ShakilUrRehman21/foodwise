package com.foodwise.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodwise.catalog.CatalogItemDto;
import com.foodwise.catalog.CatalogMatcher;
import com.foodwise.catalog.CatalogService;
import com.foodwise.common.ApiException;
import com.foodwise.common.AppClock;
import com.foodwise.common.Units;
import com.foodwise.common.enums.ScanStatus;
import com.foodwise.common.enums.ScanType;
import com.foodwise.common.enums.StorageLocation;
import com.foodwise.common.enums.Unit;
import com.foodwise.config.FoodwiseProperties;
import com.foodwise.inventory.BatchDto;
import com.foodwise.inventory.BatchMapper;
import com.foodwise.inventory.InventoryBatch;
import com.foodwise.inventory.InventoryOperations;
import com.foodwise.security.KitchenAuthGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scan orchestration (Roadmap 4.B7). Processing runs synchronously within the request (target < 6 s) without
 * holding a DB transaction; the session row is persisted first (pending) and then updated with results or a
 * human-readable failure. Confirmation is a single transaction that creates batches + purchase records.
 */
@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    private final ScanSessionRepository sessionRepo;
    private final ImageStorageService storage;
    private final ImagePreprocessor preprocessor;
    private final OcrEngine ocr;
    private final HaulDetector haulDetector;
    private final CatalogMatcher matcher;
    private final CatalogService catalog;
    private final InventoryOperations ops;
    private final BatchMapper mapper;
    private final ScanRateLimiter rateLimiter;
    private final KitchenAuthGuard guard;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final AppClock clock;
    private final double matchThreshold;

    public ScanService(ScanSessionRepository sessionRepo, ImageStorageService storage, ImagePreprocessor preprocessor, OcrEngine ocr,
                       HaulDetector haulDetector, CatalogMatcher matcher, CatalogService catalog, InventoryOperations ops,
                       BatchMapper mapper, ScanRateLimiter rateLimiter, KitchenAuthGuard guard, TransactionTemplate tx,
                       ObjectMapper objectMapper, AppClock clock, FoodwiseProperties props) {
        this.sessionRepo = sessionRepo;
        this.storage = storage;
        this.preprocessor = preprocessor;
        this.ocr = ocr;
        this.haulDetector = haulDetector;
        this.matcher = matcher;
        this.catalog = catalog;
        this.ops = ops;
        this.mapper = mapper;
        this.rateLimiter = rateLimiter;
        this.guard = guard;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.matchThreshold = props.scan().matchThreshold();
    }

    // ------------------------------------------------------------------ DTOs

    public record ScanSessionDto(UUID id, ScanType scanType, ScanStatus status, List<ScanCandidate> items, BigDecimal overallConfidence,
                                 boolean lowConfidence, boolean experimental, String storeName, String purchaseDate,
                                 BigDecimal detectedTotalInr, int confirmedItemsCount, String errorMessage, Instant createdAt) {
    }

    public record ConfirmItem(
            @Size(max = 100) String itemName,
            UUID catalogItemId,
            UUID categoryId,
            @NotNull @Positive @DecimalMax("99999") BigDecimal quantity,
            @NotNull Unit unit,
            StorageLocation storageLocation,
            LocalDate expiryDate,
            @DecimalMin("0") @DecimalMax("9999999") BigDecimal priceInr) {
    }

    public record ConfirmRequest(@NotEmpty @Size(max = 100) List<@Valid ConfirmItem> items,
                                 @Size(max = 100) String storeName, LocalDate purchaseDate) {
    }

    public record ConfirmResult(ScanSessionDto session, List<BatchDto> created) {
    }

    // ------------------------------------------------------------------ processing

    public ScanSessionDto process(UUID kitchenId, UUID userId, byte[] imageBytes, ScanType type) {
        guard.requireMembership(kitchenId, userId);
        if (type == ScanType.FRIDGE_SHELF) {
            throw ApiException.validation("scanType", "Fridge shelf scans are not supported yet.");
        }
        rateLimiter.acquire(kitchenId);
        ImageStorageService.StoredImage stored = storage.store(kitchenId, imageBytes);

        ScanSession session = new ScanSession();
        session.setKitchenId(kitchenId);
        session.setUserId(userId);
        session.setImageUrl("local:" + stored.relativePath());
        session.setScanType(type);
        session.setStatus(ScanStatus.PENDING);
        session.setParsedItems(List.of());
        UUID sessionId = sessionRepo.save(session).getId();

        long started = System.nanoTime();
        try {
            Outcome outcome = type == ScanType.RECEIPT ? runReceipt(stored.image()) : runHaul(stored.image());
            ScanSession s = sessionRepo.findById(sessionId).orElseThrow();
            s.setParsedItems(outcome.items());
            s.setRawProcessingOutput(outcome.raw());
            s.setOverallConfidence(outcome.confidence());
            sessionRepo.save(s);
            log.info("Scan {} ({}) produced {} candidates in {} ms", sessionId, type.dbValue(), outcome.items().size(),
                    (System.nanoTime() - started) / 1_000_000);
            return toDto(s);
        } catch (OcrEngine.OcrUnavailableException | IllegalStateException e) {
            return fail(sessionId, e instanceof OcrEngine.OcrUnavailableException ? e.getMessage()
                    : "We could not read this image. Try a sharper, well-lit photo.", e);
        } catch (Exception e) {
            return fail(sessionId, "We could not read this image. Try a sharper, well-lit photo.", e);
        }
    }

    private ScanSessionDto fail(UUID sessionId, String message, Exception e) {
        log.warn("Scan {} failed: {}", sessionId, e.getMessage());
        ScanSession s = sessionRepo.findById(sessionId).orElseThrow();
        s.setStatus(ScanStatus.FAILED);
        s.setErrorMessage(message);
        sessionRepo.save(s);
        return toDto(s);
    }

    private record Outcome(List<ScanCandidate> items, ObjectNode raw, BigDecimal confidence) {
    }

    private Outcome runReceipt(BufferedImage image) {
        String text = ocr.extractText(preprocessor.prepareReceipt(image));
        ReceiptTextParser.ParsedReceipt receipt = ReceiptTextParser.parse(text);
        LocalDate purchaseDate = receipt.purchaseDate() == null ? clock.today() : receipt.purchaseDate();
        List<ScanCandidate> items = new ArrayList<>();
        ArrayNode tokens = objectMapper.createArrayNode();
        for (ReceiptTextParser.ReceiptLine line : receipt.lines()) {
            var match = matcher.bestMatch(line.name());
            double score = match.map(CatalogMatcher.Match::score).orElse(0.0);
            CatalogItemDto item = match.filter(m -> m.score() >= 0.5).map(CatalogMatcher.Match::item).orElse(null);
            // Numbers that don't add up halve the confidence so the review screen highlights the row.
            double confidence = line.suspicious() ? Math.min(score, 0.5) : score;
            items.add(candidate(item, line.name(), line.rawText(), line.quantity(), line.unit(), line.price(), confidence, purchaseDate, false));
            ObjectNode t = tokens.addObject();
            t.put("line", line.rawText());
            t.put("name", line.name());
            t.put("match", item == null ? null : item.nameEn());
            t.put("score", round3(score));
        }
        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("engine", "tesseract");
        raw.put("text", text.length() > 20000 ? text.substring(0, 20000) : text);
        raw.set("tokens", tokens);
        if (receipt.storeName() != null) {
            raw.put("storeName", receipt.storeName());
        }
        if (receipt.purchaseDate() != null) {
            raw.put("purchaseDate", receipt.purchaseDate().toString());
        }
        if (receipt.totalAmount() != null) {
            raw.put("totalAmount", receipt.totalAmount());
        }
        return new Outcome(items, raw, meanConfidence(items));
    }

    private Outcome runHaul(BufferedImage image) throws Exception {
        if (!haulDetector.isAvailable()) {
            throw new OcrEngine.OcrUnavailableException(haulDetector.unavailableReason());
        }
        List<HaulDetector.Detection> detections = haulDetector.detect(image);
        Map<String, List<HaulDetector.Detection>> byLabel = new LinkedHashMap<>();
        detections.forEach(d -> byLabel.computeIfAbsent(d.label(), k -> new ArrayList<>()).add(d));
        List<ScanCandidate> items = new ArrayList<>();
        ArrayNode rawDetections = objectMapper.createArrayNode();
        for (Map.Entry<String, List<HaulDetector.Detection>> e : byLabel.entrySet()) {
            double conf = e.getValue().stream().mapToDouble(HaulDetector.Detection::confidence).max().orElse(0);
            CatalogItemDto item = matcher.bestMatch(e.getKey()).filter(m -> m.score() >= matchThreshold)
                    .map(CatalogMatcher.Match::item).orElse(null);
            items.add(candidate(item, e.getKey(), e.getKey(), BigDecimal.valueOf(e.getValue().size()), Unit.PCS, null, conf,
                    clock.today(), true));
        }
        for (HaulDetector.Detection d : detections) {
            ObjectNode n = rawDetections.addObject();
            n.put("label", d.label());
            n.put("confidence", round3(d.confidence()));
            n.putArray("box").add(d.x()).add(d.y()).add(d.w()).add(d.h());
        }
        ObjectNode raw = objectMapper.createObjectNode();
        raw.put("engine", "onnx-yolov8");
        raw.set("detections", rawDetections);
        return new Outcome(items, raw, meanConfidence(items));
    }

    private ScanCandidate candidate(CatalogItemDto item, String name, String raw, BigDecimal qty, Unit unit, BigDecimal price,
                                    double confidence, LocalDate purchaseDate, boolean experimental) {
        StorageLocation loc = item == null ? StorageLocation.FRIDGE : item.defaultLocation();
        Unit u = unit;
        BigDecimal q = qty;
        if (item != null && (u == null || !Units.isInventoryUnit(u))) {
            u = item.defaultUnit();
        }
        if (item != null && u == Unit.PCS && (item.defaultUnit() == Unit.BUNCH || item.defaultUnit() == Unit.PKT)) {
            u = item.defaultUnit(); // "1" on a bill for coriander means one bunch, for bread one packet
        }
        if (u == null) {
            u = Unit.PCS;
        }
        if (item != null && !Units.compatible(u, item.defaultUnit()) && q == null) {
            u = item.defaultUnit();
        }
        if (q == null || q.signum() <= 0) {
            q = BigDecimal.ONE;
        }
        String expiry = item == null ? null : catalog.estimateExpiry(item.id(), loc, purchaseDate).map(LocalDate::toString).orElse(null);
        String display = item == null ? name : item.nameEn();
        return new ScanCandidate(display, raw, item == null ? null : item.id(), item == null ? null : item.categoryId(),
                Units.qty(q), u, loc, expiry, expiry != null, price, round3(confidence), confidence < matchThreshold, experimental);
    }

    private static BigDecimal meanConfidence(List<ScanCandidate> items) {
        if (items.isEmpty()) {
            return null;
        }
        double mean = items.stream().mapToDouble(ScanCandidate::confidenceScore).average().orElse(0);
        return BigDecimal.valueOf(Math.min(1.0, Math.max(0.0, mean))).setScale(3, RoundingMode.HALF_UP);
    }

    private static double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    // ------------------------------------------------------------------ review

    @Transactional(readOnly = true)
    public ScanSessionDto get(UUID kitchenId, UUID userId, UUID sessionId) {
        guard.requireMembership(kitchenId, userId);
        return toDto(sessionRepo.findByIdAndKitchenId(sessionId, kitchenId).orElseThrow(() -> ApiException.notFound("Scan not found.")));
    }

    @Transactional(readOnly = true)
    public List<ScanSessionDto> history(UUID kitchenId, UUID userId, int limit) {
        guard.requireMembership(kitchenId, userId);
        return sessionRepo.findByKitchenIdOrderByCreatedAtDesc(kitchenId, PageRequest.of(0, Math.max(1, Math.min(limit, 50))))
                .stream().map(this::toDto).toList();
    }

    public record ImageData(byte[] bytes, String contentType) {
    }

    @Transactional(readOnly = true)
    public ImageData image(UUID kitchenId, UUID userId, UUID sessionId) {
        guard.requireMembership(kitchenId, userId);
        ScanSession s = sessionRepo.findByIdAndKitchenId(sessionId, kitchenId).orElseThrow(() -> ApiException.notFound("Scan not found."));
        String path = s.getImageUrl().startsWith("local:") ? s.getImageUrl().substring(6) : null;
        if (path == null) {
            throw ApiException.notFound("Image not stored locally.");
        }
        return new ImageData(storage.read(path), storage.contentType(path));
    }

    /** Human-in-the-loop confirmation: the only path from a scan into inventory (FR-SCAN-004). */
    @Transactional
    public ConfirmResult confirm(UUID kitchenId, UUID userId, UUID sessionId, ConfirmRequest req) {
        guard.requireMembership(kitchenId, userId);
        ScanSession s = sessionRepo.lockByIdAndKitchenId(sessionId, kitchenId).orElseThrow(() -> ApiException.notFound("Scan not found."));
        if (s.getStatus() != ScanStatus.PENDING) {
            throw ApiException.conflict("This scan has already been " + s.getStatus().dbValue() + ".");
        }
        LocalDate purchaseDate = req.purchaseDate() == null ? clock.today() : req.purchaseDate();
        List<BatchDto> created = new ArrayList<>();
        List<InventoryOperations.PurchaseLine> lines = new ArrayList<>();
        for (int i = 0; i < req.items().size(); i++) {
            ConfirmItem item = req.items().get(i);
            InventoryBatch b = ops.createBatch(new InventoryOperations.NewBatch(kitchenId, item.catalogItemId(), item.itemName(),
                    item.categoryId(), item.storageLocation(), item.quantity(), item.unit(), purchaseDate, item.expiryDate(),
                    item.priceInr(), false, null, null, null), userId, "items[" + i + "].");
            created.add(mapper.toDto(b));
            lines.add(new InventoryOperations.PurchaseLine(b, item.priceInr()));
        }
        String store = req.storeName() != null ? req.storeName() : rawText(s, "storeName");
        ops.recordPurchase(kitchenId, userId, purchaseDate, store, s.getScanType() == ScanType.RECEIPT ? s.getId() : null,
                s.getScanType() == ScanType.RECEIPT ? s.getImageUrl() : null, lines);
        s.setStatus(ScanStatus.CONFIRMED);
        s.setConfirmedItemsCount(created.size());
        sessionRepo.save(s);
        return new ConfirmResult(toDto(s), created);
    }

    @Transactional
    public ScanSessionDto reject(UUID kitchenId, UUID userId, UUID sessionId) {
        guard.requireMembership(kitchenId, userId);
        ScanSession s = sessionRepo.lockByIdAndKitchenId(sessionId, kitchenId).orElseThrow(() -> ApiException.notFound("Scan not found."));
        if (s.getStatus() == ScanStatus.CONFIRMED) {
            throw ApiException.conflict("This scan was already added to your inventory.");
        }
        s.setStatus(ScanStatus.REJECTED);
        return toDto(sessionRepo.save(s));
    }

    private ScanSessionDto toDto(ScanSession s) {
        List<ScanCandidate> items = s.getParsedItems() == null ? List.of() : s.getParsedItems();
        boolean low = s.getOverallConfidence() != null && s.getOverallConfidence().doubleValue() < matchThreshold;
        BigDecimal total = null;
        if (s.getRawProcessingOutput() != null && s.getRawProcessingOutput().hasNonNull("totalAmount")) {
            total = s.getRawProcessingOutput().get("totalAmount").decimalValue();
        }
        return new ScanSessionDto(s.getId(), s.getScanType(), s.getStatus(), items, s.getOverallConfidence(), low,
                s.getScanType() == ScanType.GROCERY_HAUL, rawText(s, "storeName"), rawText(s, "purchaseDate"), total,
                s.getConfirmedItemsCount(), s.getErrorMessage(), s.getCreatedAt());
    }

    private static String rawText(ScanSession s, String field) {
        return s.getRawProcessingOutput() != null && s.getRawProcessingOutput().hasNonNull(field)
                ? s.getRawProcessingOutput().get(field).asText() : null;
    }
}
