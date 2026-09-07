package com.secureportal.content;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ContentRepository extends JpaRepository<ContentItem, UUID> {

    /**
     * Catalog query backing search and filtering. {@code contentType} is
     * optional (pass {@code null} to disable that clause); {@code search}
     * and {@code category} must be passed as {@code ""} rather than
     * {@code null} to disable theirs — binding a null String into a
     * parameter that's also wrapped in {@code LOWER(...)} elsewhere in the
     * same query confuses PostgreSQL's type inference for that placeholder
     * (it was resolving to {@code bytea}, not {@code text}), which
     * {@code LOWER()} then rejects outright.
     */
    @Query("""
            SELECT c FROM ContentItem c
            WHERE (:search = ''
                   OR LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:contentType IS NULL OR c.contentType = :contentType)
              AND (:category = '' OR LOWER(c.category) = LOWER(:category))
            """)
    Page<ContentItem> search(@Param("search") String search,
                             @Param("contentType") ContentType contentType,
                             @Param("category") String category,
                             Pageable pageable);

    @Query("SELECT DISTINCT c.category FROM ContentItem c WHERE c.category IS NOT NULL AND c.category <> '' ORDER BY c.category")
    List<String> findDistinctCategories();

    /**
     * Recorded as a bulk update rather than by mutating the entity so that a
     * view never races with a concurrent metadata edit.
     */
    @Modifying
    @Query("UPDATE ContentItem c SET c.viewCount = c.viewCount + 1, c.lastViewedAt = :now WHERE c.id = :id")
    void recordView(@Param("id") UUID id, @Param("now") Instant now);
}
