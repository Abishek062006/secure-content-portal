package com.secureportal.network;

import com.secureportal.notification.NotificationCategory;
import com.secureportal.notification.NotificationPriority;
import com.secureportal.notification.NotificationService;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ConnectionService(ConnectionRepository connectionRepository,
                             UserRepository userRepository,
                             NotificationService notificationService) {
        this.connectionRepository = connectionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public Page<ConnectionUserDto> getSuggestions(Long currentUserId, String query, Pageable pageable) {
        List<User> allCandidates = userRepository.findAll();
        Set<Long> myConnectedIds = new HashSet<>(connectionRepository.findConnectedUserIds(currentUserId));

        List<Connection> myPendingConnections = connectionRepository.findPendingConnectionsForUser(currentUserId);
        Map<Long, Connection> relationshipMap = myPendingConnections.stream()
                .collect(Collectors.toMap(
                        c -> c.getRequesterId().equals(currentUserId) ? c.getReceiverId() : c.getRequesterId(),
                        c -> c,
                        (c1, c2) -> c1
                ));

        String q = query != null ? query.trim().toLowerCase() : "";

        List<User> filtered = allCandidates.stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .filter(u -> q.isEmpty() ||
                        (u.getDisplayName() != null && u.getDisplayName().toLowerCase().contains(q)) ||
                        u.getEmail().toLowerCase().contains(q))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filtered.size());
        List<User> pageContent = (start <= filtered.size()) ? filtered.subList(start, end) : Collections.emptyList();

        List<ConnectionUserDto> dtoList = pageContent.stream().map(targetUser -> {
            String relStatus = "NONE";
            Long connId = null;

            if (myConnectedIds.contains(targetUser.getId())) {
                relStatus = "ACCEPTED";
                Optional<Connection> rel = connectionRepository.findRelationship(currentUserId, targetUser.getId());
                if (rel.isPresent()) connId = rel.get().getId();
            } else if (relationshipMap.containsKey(targetUser.getId())) {
                Connection conn = relationshipMap.get(targetUser.getId());
                connId = conn.getId();
                if (conn.getRequesterId().equals(currentUserId)) {
                    relStatus = "PENDING_SENT";
                } else {
                    relStatus = "PENDING_RECEIVED";
                }
            }

            Set<Long> targetConnectedIds = new HashSet<>(connectionRepository.findConnectedUserIds(targetUser.getId()));
            targetConnectedIds.retainAll(myConnectedIds);
            int mutualCount = targetConnectedIds.size();

            return ConnectionUserDto.from(targetUser, relStatus, connId, mutualCount);
        }).toList();

        return new PageImpl<>(dtoList, pageable, filtered.size());
    }

    @Transactional
    public ConnectionRequestDto sendRequest(Long requesterId, Long targetUserId) {
        if (requesterId.equals(targetUserId)) {
            throw new NetworkExceptions.SelfConnectionException();
        }

        User requester = userRepository.findById(requesterId)
                .orElseThrow(NetworkExceptions.ConnectionNotFoundException::new);
        User receiver = userRepository.findById(targetUserId)
                .orElseThrow(NetworkExceptions.ConnectionNotFoundException::new);

        Optional<Connection> existing = connectionRepository.findRelationship(requesterId, targetUserId);
        if (existing.isPresent()) {
            Connection conn = existing.get();
            if (conn.getStatus() == ConnectionStatus.ACCEPTED) {
                throw new NetworkExceptions.DuplicateConnectionException("You are already connected with this user.");
            }
            if (conn.getStatus() == ConnectionStatus.PENDING) {
                throw new NetworkExceptions.DuplicateConnectionException("A connection request is already pending.");
            }
            // If rejected, allow re-requesting
            connectionRepository.delete(conn);
        }

        Connection newConn = new Connection(requesterId, targetUserId);
        Connection saved = connectionRepository.save(newConn);

        String requesterName = requester.getDisplayName() != null ? requester.getDisplayName() : requester.getEmail();
        notificationService.createNotification(
                targetUserId,
                NotificationCategory.CONNECTION,
                "New Connection Request",
                requesterName + " sent you a connection request.",
                NotificationPriority.NORMAL,
                "/network"
        );

        return ConnectionRequestDto.from(saved, requester, receiver);
    }

    @Transactional(readOnly = true)
    public List<ConnectionRequestDto> getReceivedRequests(Long userId) {
        List<Connection> requests = connectionRepository.findByReceiverIdAndStatusOrderByCreatedAtDesc(userId, ConnectionStatus.PENDING);
        return requests.stream().map(c -> {
            User requester = userRepository.findById(c.getRequesterId()).orElse(null);
            User receiver = userRepository.findById(c.getReceiverId()).orElse(null);
            if (requester == null || receiver == null) return null;
            return ConnectionRequestDto.from(c, requester, receiver);
        }).filter(dto -> dto != null).toList();
    }

    @Transactional(readOnly = true)
    public List<ConnectionRequestDto> getSentRequests(Long userId) {
        List<Connection> requests = connectionRepository.findByRequesterIdAndStatusOrderByCreatedAtDesc(userId, ConnectionStatus.PENDING);
        return requests.stream().map(c -> {
            User requester = userRepository.findById(c.getRequesterId()).orElse(null);
            User receiver = userRepository.findById(c.getReceiverId()).orElse(null);
            if (requester == null || receiver == null) return null;
            return ConnectionRequestDto.from(c, requester, receiver);
        }).filter(dto -> dto != null).toList();
    }

    @Transactional
    public ConnectionRequestDto acceptRequest(Long currentUserId, Long requestId) {
        Connection conn = connectionRepository.findById(requestId)
                .orElseThrow(NetworkExceptions.ConnectionNotFoundException::new);

        if (!conn.getReceiverId().equals(currentUserId)) {
            throw new NetworkExceptions.ConnectionAccessDeniedException("Only the recipient of a connection request can accept it.");
        }

        if (conn.getStatus() != ConnectionStatus.PENDING) {
            User requester = userRepository.findById(conn.getRequesterId()).orElseThrow(NetworkExceptions.ConnectionNotFoundException::new);
            User receiver = userRepository.findById(conn.getReceiverId()).orElseThrow(NetworkExceptions.ConnectionNotFoundException::new);
            return ConnectionRequestDto.from(conn, requester, receiver);
        }

        conn.setStatus(ConnectionStatus.ACCEPTED);
        conn.setRespondedAt(Instant.now());
        Connection updated = connectionRepository.save(conn);

        User requester = userRepository.findById(conn.getRequesterId()).orElseThrow(NetworkExceptions.ConnectionNotFoundException::new);
        User receiver = userRepository.findById(conn.getReceiverId()).orElseThrow(NetworkExceptions.ConnectionNotFoundException::new);

        String receiverName = receiver.getDisplayName() != null ? receiver.getDisplayName() : receiver.getEmail();
        notificationService.createNotification(
                conn.getRequesterId(),
                NotificationCategory.CONNECTION,
                "Connection Request Accepted",
                receiverName + " accepted your connection request.",
                NotificationPriority.NORMAL,
                "/network"
        );

        return ConnectionRequestDto.from(updated, requester, receiver);
    }

    @Transactional
    public void rejectRequest(Long currentUserId, Long requestId) {
        Connection conn = connectionRepository.findById(requestId)
                .orElseThrow(NetworkExceptions.ConnectionNotFoundException::new);

        if (!conn.getReceiverId().equals(currentUserId)) {
            throw new NetworkExceptions.ConnectionAccessDeniedException("Only the recipient of a connection request can reject it.");
        }

        connectionRepository.delete(conn);
    }

    @Transactional
    public void withdrawRequest(Long currentUserId, Long requestId) {
        Connection conn = connectionRepository.findById(requestId)
                .orElseThrow(NetworkExceptions.ConnectionNotFoundException::new);

        if (!conn.getRequesterId().equals(currentUserId)) {
            throw new NetworkExceptions.ConnectionAccessDeniedException("Only the sender of a connection request can withdraw it.");
        }

        connectionRepository.delete(conn);
    }

    @Transactional(readOnly = true)
    public List<ConnectionUserDto> getConnections(Long currentUserId) {
        List<Connection> connections = connectionRepository.findAcceptedConnections(currentUserId);
        Set<Long> myConnectedIds = new HashSet<>(connectionRepository.findConnectedUserIds(currentUserId));

        return connections.stream().map(conn -> {
            Long otherUserId = conn.getRequesterId().equals(currentUserId) ? conn.getReceiverId() : conn.getRequesterId();
            User otherUser = userRepository.findById(otherUserId).orElse(null);
            if (otherUser == null) return null;

            Set<Long> otherConnectedIds = new HashSet<>(connectionRepository.findConnectedUserIds(otherUserId));
            otherConnectedIds.retainAll(myConnectedIds);
            int mutualCount = otherConnectedIds.size();

            return ConnectionUserDto.from(otherUser, "ACCEPTED", conn.getId(), mutualCount);
        }).filter(dto -> dto != null).toList();
    }

    @Transactional
    public void removeConnection(Long currentUserId, Long targetUserId) {
        Optional<Connection> conn = connectionRepository.findRelationship(currentUserId, targetUserId);
        if (conn.isPresent()) {
            connectionRepository.delete(conn.get());
        }
    }

    @Transactional(readOnly = true)
    public long getAcceptedConnectionCount(Long userId) {
        return connectionRepository.countAcceptedConnections(userId);
    }

    @Transactional(readOnly = true)
    public boolean areConnected(Long userA, Long userB) {
        if (userA == null || userB == null || userA.equals(userB)) return false;
        Optional<Connection> conn = connectionRepository.findRelationship(userA, userB);
        return conn.isPresent() && conn.get().getStatus() == ConnectionStatus.ACCEPTED;
    }
}
