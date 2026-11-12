package com.foodwise.leftover;

import com.foodwise.common.enums.LeftoverType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA entity for table {@code leftover_repurposing_rules} (see docs/DATABASE_SCHEMA.md). Generated accessors. */
@Entity
@Table(name = "leftover_repurposing_rules")
public class LeftoverRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "leftover_type")
    private LeftoverType leftoverType;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "instructions")
    private List<String> instructions;

    @Column(name = "recipe_id")
    private UUID recipeId;

    @Column(name = "display_order")
    private int displayOrder;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LeftoverType getLeftoverType() {
        return leftoverType;
    }

    public void setLeftoverType(LeftoverType leftoverType) {
        this.leftoverType = leftoverType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getInstructions() {
        return instructions;
    }

    public void setInstructions(List<String> instructions) {
        this.instructions = instructions;
    }

    public UUID getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(UUID recipeId) {
        this.recipeId = recipeId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
