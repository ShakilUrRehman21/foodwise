package com.foodwise.kitchen;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface KitchenRepository extends JpaRepository<Kitchen, UUID> {

    @Query("select k from Kitchen k where k.id in (select m.kitchenId from KitchenMember m where m.userId = :userId) order by k.createdAt")
    List<Kitchen> findAllForUser(UUID userId);
}
