package com.secureportal.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

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

    long countByRole(Role role);
}
