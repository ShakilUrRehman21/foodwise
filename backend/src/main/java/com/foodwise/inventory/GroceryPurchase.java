package com.foodwise.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** JPA entity for table {@code grocery_purchases} (see docs/DATABASE_SCHEMA.md). Generated accessors. */
@Entity
@Table(name = "grocery_purchases")
public class GroceryPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kitchen_id")
    private UUID kitchenId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "store_name")
    private String storeName;

    @Column(name = "total_amount_inr")
    private BigDecimal totalAmountInr;

    @Column(name = "receipt_scan_session_id")
    private UUID receiptScanSessionId;

    @Column(name = "receipt_image_url")
    private String receiptImageUrl;

    @Column(name = "notes")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public BigDecimal getTotalAmountInr() {
        return totalAmountInr;
    }

    public void setTotalAmountInr(BigDecimal totalAmountInr) {
        this.totalAmountInr = totalAmountInr;
    }

    public UUID getReceiptScanSessionId() {
        return receiptScanSessionId;
    }

    public void setReceiptScanSessionId(UUID receiptScanSessionId) {
        this.receiptScanSessionId = receiptScanSessionId;
    }

    public String getReceiptImageUrl() {
        return receiptImageUrl;
    }

    public void setReceiptImageUrl(String receiptImageUrl) {
        this.receiptImageUrl = receiptImageUrl;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
