package com.foodwise.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {

    Optional<UserPreferences> findByUserId(UUID userId);

    /** Users whose configured digest time has been reached today (scheduler due-window, SYSTEM_DESIGN 8.1). */
    @Query("select p from UserPreferences p where p.notifyDailyDigestTime <= :now")
    List<UserPreferences> findDigestDue(LocalTime now);
}
