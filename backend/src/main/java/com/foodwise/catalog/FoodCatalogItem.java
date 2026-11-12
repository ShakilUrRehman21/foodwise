package com.foodwise.catalog;

import com.foodwise.common.enums.StorageLocation;
import com.foodwise.common.enums.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA entity for table {@code food_catalog} (see docs/DATABASE_SCHEMA.md). Generated accessors. */
@Entity
@Table(name = "food_catalog")
public class FoodCatalogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "name_hi")
    private String nameHi;

    @Column(name = "default_unit")
    private Unit defaultUnit;

    @Column(name = "default_location")
    private StorageLocation defaultLocation;

    @Column(name = "shelf_life_room_temp_days")
    private Integer shelfLifeRoomTempDays;

    @Column(name = "shelf_life_fridge_days")
    private Integer shelfLifeFridgeDays;

    @Column(name = "shelf_life_freezer_days")
    private Integer shelfLifeFreezerDays;

    @Column(name = "is_perishable")
    private boolean perishable;

    @Column(name = "common_storage_tip")
    private String commonStorageTip;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public String getNameHi() {
        return nameHi;
    }

    public void setNameHi(String nameHi) {
        this.nameHi = nameHi;
    }

    public Unit getDefaultUnit() {
        return defaultUnit;
    }

    public void setDefaultUnit(Unit defaultUnit) {
        this.defaultUnit = defaultUnit;
    }

    public StorageLocation getDefaultLocation() {
        return defaultLocation;
    }

    public void setDefaultLocation(StorageLocation defaultLocation) {
        this.defaultLocation = defaultLocation;
    }

    public Integer getShelfLifeRoomTempDays() {
        return shelfLifeRoomTempDays;
    }

    public void setShelfLifeRoomTempDays(Integer shelfLifeRoomTempDays) {
        this.shelfLifeRoomTempDays = shelfLifeRoomTempDays;
    }

    public Integer getShelfLifeFridgeDays() {
        return shelfLifeFridgeDays;
    }

    public void setShelfLifeFridgeDays(Integer shelfLifeFridgeDays) {
        this.shelfLifeFridgeDays = shelfLifeFridgeDays;
    }

    public Integer getShelfLifeFreezerDays() {
        return shelfLifeFreezerDays;
    }

    public void setShelfLifeFreezerDays(Integer shelfLifeFreezerDays) {
        this.shelfLifeFreezerDays = shelfLifeFreezerDays;
    }

    public boolean isPerishable() {
        return perishable;
    }

    public void setPerishable(boolean perishable) {
        this.perishable = perishable;
    }

    public String getCommonStorageTip() {
        return commonStorageTip;
    }

    public void setCommonStorageTip(String commonStorageTip) {
        this.commonStorageTip = commonStorageTip;
    }
}
