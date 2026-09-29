package com.secureportal.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Every filter is optional: a null parameter matches everything, so the admin screen can leave any of
     *  them blank and this still reads as "all". */
    @Query("SELECT a FROM AuditLog a WHERE "
            + "(:action IS NULL OR a.action = :action) AND "
            + "(:actor IS NULL OR LOWER(a.actorEmail) LIKE LOWER(CONCAT('%', :actor, '%'))) AND "
            + "(:from IS NULL OR a.createdAt >= :from) AND "
            + "(:to IS NULL OR a.createdAt <= :to) "
            + "ORDER BY a.createdAt DESC")
    Page<AuditLog> search(@Param("action") String action, @Param("actor") String actor,
                          @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> distinctActions();
}
