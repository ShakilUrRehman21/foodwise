package com.foodwise.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByClerkId(String clerkId);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Idempotent insert used by both the Clerk webhook and the JIT fallback. Postgres makes a concurrent
     * duplicate wait for the first transaction and then skip, so exactly one row is ever created per clerk_id.
     *
     * @return 1 if this call created the user, 0 if it already existed
     */
    @Modifying
    @Query(value = """
            INSERT INTO users (id, clerk_id, email, first_name, last_name, image_url, created_at, updated_at)
            VALUES (gen_random_uuid(), :clerkId, :email, :firstName, :lastName, :imageUrl, now(), now())
            ON CONFLICT (clerk_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("clerkId") String clerkId, @Param("email") String email,
                       @Param("firstName") String firstName, @Param("lastName") String lastName,
                       @Param("imageUrl") String imageUrl);
}
