package com.secureportal.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findByRole(Role role);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.leaderboardHidden = :hidden WHERE u.id = :id")
    int setLeaderboardHidden(@Param("id") Long id, @Param("hidden") boolean hidden);

    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Pass "" rather than null to disable the search filter — a null value
     * bound into a parameter also wrapped in LOWER(...) elsewhere in the
     * query confuses PostgreSQL's type inference for that placeholder. See
     * ContentRepository.search, which hit this for real.
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:search = ''
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY u.email
            """)
    Page<User> search(@Param("search") String search, Pageable pageable);

    /**
     * Other members to connect with, by name only (never by email), one page at a time. {@code excludeRole} keeps admins out, since
     * they run the platform rather than take part in the network. Pass "" rather than null to list everyone.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.id <> :excludeId AND u.role <> :excludeRole
              AND (:search = '' OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY u.displayName, u.id
            """)
    Page<User> findPeople(@Param("excludeId") Long excludeId, @Param("excludeRole") Role excludeRole, @Param("search") String search,
                          Pageable pageable);

    long countByRole(Role role);
}
