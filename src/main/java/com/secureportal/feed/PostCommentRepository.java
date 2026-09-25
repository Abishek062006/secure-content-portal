package com.secureportal.feed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {

    List<PostComment> findByPostIdOrderByCreatedAtAsc(UUID postId);

    /** Rows of (post id, count). */
    @Query("SELECT c.postId, COUNT(c) FROM PostComment c WHERE c.postId IN :ids GROUP BY c.postId")
    List<Object[]> countByPost(@Param("ids") Collection<UUID> ids);
}
