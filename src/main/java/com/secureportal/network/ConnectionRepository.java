package com.secureportal.network;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConnectionRepository extends JpaRepository<Connection, Long> {

    Optional<Connection> findByRequesterIdAndReceiverId(Long requesterId, Long receiverId);

    @Query("SELECT c FROM Connection c WHERE (c.requesterId = :userA AND c.receiverId = :userB) OR (c.requesterId = :userB AND c.receiverId = :userA)")
    Optional<Connection> findRelationship(@Param("userA") Long userA, @Param("userB") Long userB);

    List<Connection> findByReceiverIdAndStatusOrderByCreatedAtDesc(Long receiverId, ConnectionStatus status);

    List<Connection> findByRequesterIdAndStatusOrderByCreatedAtDesc(Long requesterId, ConnectionStatus status);

    @Query("SELECT c FROM Connection c WHERE (c.requesterId = :userId OR c.receiverId = :userId) AND c.status = 'ACCEPTED' ORDER BY c.respondedAt DESC, c.createdAt DESC")
    List<Connection> findAcceptedConnections(@Param("userId") Long userId);

    @Query("SELECT COUNT(c) FROM Connection c WHERE (c.requesterId = :userId OR c.receiverId = :userId) AND c.status = 'ACCEPTED'")
    long countAcceptedConnections(@Param("userId") Long userId);

    @Query("SELECT CASE WHEN c.requesterId = :userId THEN c.receiverId ELSE c.requesterId END FROM Connection c WHERE (c.requesterId = :userId OR c.receiverId = :userId) AND c.status = 'ACCEPTED'")
    List<Long> findConnectedUserIds(@Param("userId") Long userId);

    @Query("SELECT c FROM Connection c WHERE (c.requesterId = :userId OR c.receiverId = :userId) AND c.status = 'PENDING'")
    List<Connection> findPendingConnectionsForUser(@Param("userId") Long userId);
}
