package com.foodwise.leftover;

import com.foodwise.common.enums.LeftoverType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeftoverRuleRepository extends JpaRepository<LeftoverRule, UUID> {

    List<LeftoverRule> findByLeftoverTypeOrderByDisplayOrder(LeftoverType type);
}
