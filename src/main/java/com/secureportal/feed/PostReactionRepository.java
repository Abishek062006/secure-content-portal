package com.secureportal.feed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostReactionRepository extends JpaRepository<PostReaction, Long> {

    Optional<PostReaction> findByPostIdAndUserId(UUID postId, Long userId);

    List<PostReaction> findByUserIdAndPostIdIn(Long userId, Collection<UUID> postIds);

    /** Rows of (post id, reaction type, count). */
    @Query("SELECT r.postId, r.type, COUNT(r) FROM PostReaction r WHERE r.postId IN :ids GROUP BY r.postId, r.type")
    List<Object[]> countByPostAndType(@Param("ids") Collection<UUID> ids);
}
