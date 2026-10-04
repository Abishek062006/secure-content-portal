package com.secureportal.network;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConnectionRepository extends JpaRepository<Connection, Long> {

    /** Scoped to the person it was sent to, so someone else's request id never resolves. */
    Optional<Connection> findByIdAndReceiverId(Long id, Long receiverId);

    Optional<Connection> findByIdAndRequesterId(Long id, Long requesterId);

    @Query("SELECT c FROM Connection c WHERE (c.requesterId = :a AND c.receiverId = :b) OR (c.requesterId = :b AND c.receiverId = :a)")
    Optional<Connection> findBetween(@Param("a") Long a, @Param("b") Long b);

    List<Connection> findByReceiverIdAndStatusOrderByCreatedAtDesc(Long receiverId, ConnectionStatus status);

    List<Connection> findByRequesterIdAndStatusOrderByCreatedAtDesc(Long requesterId, ConnectionStatus status);

    long countByRequesterIdAndStatus(Long requesterId, ConnectionStatus status);

    @Query("SELECT c FROM Connection c WHERE (c.requesterId = :userId OR c.receiverId = :userId) AND c.status = :status "
            + "ORDER BY c.respondedAt DESC, c.createdAt DESC")
    List<Connection> findForUser(@Param("userId") Long userId, @Param("status") ConnectionStatus status);

    @Query("SELECT COUNT(c) FROM Connection c WHERE (c.requesterId = :userId OR c.receiverId = :userId) AND c.status = :status")
    long countForUser(@Param("userId") Long userId, @Param("status") ConnectionStatus status);

    /** The signed-in member's relationships with any of {@code ids}, in one query. */
    @Query("SELECT c FROM Connection c WHERE (c.requesterId = :userId AND c.receiverId IN :ids) OR (c.receiverId = :userId AND c.requesterId IN :ids)")
    List<Connection> findWithAny(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    /** Every connection with the given status that touches any of {@code ids}: what mutual-connection counts are worked out from. */
    @Query("SELECT c FROM Connection c WHERE c.status = :status AND (c.requesterId IN :ids OR c.receiverId IN :ids)")
    List<Connection> findTouching(@Param("ids") Collection<Long> ids, @Param("status") ConnectionStatus status);
}
