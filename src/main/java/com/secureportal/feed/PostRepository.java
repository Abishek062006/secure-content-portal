package com.secureportal.feed;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    @Query("SELECT p FROM Post p WHERE p.publishAt <= :now ORDER BY p.pinned DESC, p.publishAt DESC")
    Page<Post> findPublished(@Param("now") Instant now, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.publishAt > :now ORDER BY p.publishAt ASC")
    List<Post> findScheduled(@Param("now") Instant now);
}
