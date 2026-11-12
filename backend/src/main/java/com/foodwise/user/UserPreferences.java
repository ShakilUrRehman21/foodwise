package com.foodwise.user;

import com.foodwise.common.enums.DietaryPreference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** JPA entity for table {@code user_preferences} (see docs/DATABASE_SCHEMA.md). Generated accessors. */
@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "dietary_type")
    private DietaryPreference dietaryType;

    @Column(name = "currency_code")
    private String currencyCode;

    @Column(name = "currency_symbol")
    private String currencySymbol;

    @Column(name = "notify_expiry_days_before")
    private int notifyExpiryDaysBefore;

    @Column(name = "notify_daily_digest_time")
    private LocalTime notifyDailyDigestTime;

    @Column(name = "email_notifications_enabled")
    private boolean emailNotificationsEnabled;

    @Column(name = "push_notifications_enabled")
    private boolean pushNotificationsEnabled;

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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public DietaryPreference getDietaryType() {
        return dietaryType;
    }

    public void setDietaryType(DietaryPreference dietaryType) {
        this.dietaryType = dietaryType;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    public int getNotifyExpiryDaysBefore() {
        return notifyExpiryDaysBefore;
    }

    public void setNotifyExpiryDaysBefore(int notifyExpiryDaysBefore) {
        this.notifyExpiryDaysBefore = notifyExpiryDaysBefore;
    }

    public LocalTime getNotifyDailyDigestTime() {
        return notifyDailyDigestTime;
    }

    public void setNotifyDailyDigestTime(LocalTime notifyDailyDigestTime) {
        this.notifyDailyDigestTime = notifyDailyDigestTime;
    }

    public boolean isEmailNotificationsEnabled() {
        return emailNotificationsEnabled;
    }

    public void setEmailNotificationsEnabled(boolean emailNotificationsEnabled) {
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }

    public boolean isPushNotificationsEnabled() {
        return pushNotificationsEnabled;
    }

    public void setPushNotificationsEnabled(boolean pushNotificationsEnabled) {
        this.pushNotificationsEnabled = pushNotificationsEnabled;
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
