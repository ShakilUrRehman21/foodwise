package com.foodwise.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodwise.common.enums.ScanStatus;
import com.foodwise.common.enums.ScanType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** JPA entity for table {@code scan_sessions} (see docs/DATABASE_SCHEMA.md). Generated accessors. */
@Entity
@Table(name = "scan_sessions")
public class ScanSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kitchen_id")
    private UUID kitchenId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "image_public_id")
    private String imagePublicId;

    @Column(name = "scan_type")
    private ScanType scanType;

    @Column(name = "status")
    private ScanStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_processing_output")
    private JsonNode rawProcessingOutput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_items")
    private List<ScanCandidate> parsedItems;

    @Column(name = "confirmed_items_count")
    private int confirmedItemsCount;

    @Column(name = "overall_confidence")
    private BigDecimal overallConfidence;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getKitchenId() {
        return kitchenId;
    }

    public void setKitchenId(UUID kitchenId) {
        this.kitchenId = kitchenId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImagePublicId() {
        return imagePublicId;
    }

    public void setImagePublicId(String imagePublicId) {
        this.imagePublicId = imagePublicId;
    }

    public ScanType getScanType() {
        return scanType;
    }

    public void setScanType(ScanType scanType) {
        this.scanType = scanType;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public void setStatus(ScanStatus status) {
        this.status = status;
    }

    public JsonNode getRawProcessingOutput() {
        return rawProcessingOutput;
    }

    public void setRawProcessingOutput(JsonNode rawProcessingOutput) {
        this.rawProcessingOutput = rawProcessingOutput;
    }

    public List<ScanCandidate> getParsedItems() {
        return parsedItems;
    }

    public void setParsedItems(List<ScanCandidate> parsedItems) {
        this.parsedItems = parsedItems;
    }

    public int getConfirmedItemsCount() {
        return confirmedItemsCount;
    }

    public void setConfirmedItemsCount(int confirmedItemsCount) {
        this.confirmedItemsCount = confirmedItemsCount;
    }

    public BigDecimal getOverallConfidence() {
        return overallConfidence;
    }

    public void setOverallConfidence(BigDecimal overallConfidence) {
        this.overallConfidence = overallConfidence;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
