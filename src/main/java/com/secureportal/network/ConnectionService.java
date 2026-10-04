package com.secureportal.network;

import com.secureportal.notification.NotificationCategory;
import com.secureportal.notification.NotificationPriority;
import com.secureportal.notification.NotificationService;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Members connecting with each other. Every request or response is scoped to the signed-in member (someone else's request id looks
 * like it doesn't exist), lists are read in a handful of queries however long they are, and no email address is ever returned.
 */
@Service
public class ConnectionService {

    /** Requests a member can have waiting at once, so nobody can flood people with invitations. */
    static final int MAX_PENDING_SENT = 100;
    static final int MAX_SEARCH_LENGTH = 50;

    private final ConnectionRepository connections;
    private final UserRepository users;
    private final NotificationService notifications;
    private final TransactionTemplate tx;

    public ConnectionService(ConnectionRepository connections, UserRepository users, NotificationService notifications,
                             PlatformTransactionManager transactionManager) {
        this.connections = connections;
        this.users = users;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ---- Finding people --------------------------------------------------------------------------------------------------

    /** Other members, by name, one page at a time, each with how the signed-in member stands with them. */
    public Page<ConnectionUserDto> suggestions(Long userId, String query, Pageable pageable) {
        String search = query == null ? "" : query.strip();
        if (search.length() > MAX_SEARCH_LENGTH) {
            search = search.substring(0, MAX_SEARCH_LENGTH);
        }
        Page<User> found = users.findPeople(userId, Role.ADMIN, search, pageable);
        List<Long> ids = found.getContent().stream().map(User::getId).toList();
        if (ids.isEmpty()) {
            return found.map(u -> ConnectionUserDto.from(u, Relationship.NONE, null, 0));
        }

        Map<Long, Connection> withMe = connections.findWithAny(userId, ids).stream()
                .collect(Collectors.toMap(c -> c.otherSide(userId), Function.identity(), (a, b) -> a));
        Map<Long, Integer> mutual = mutualCounts(userId, ids);
        return found.map(person -> {
            Connection link = withMe.get(person.getId());
            return ConnectionUserDto.from(person, relationship(link, userId), link == null ? null : link.getId(),
                    mutual.getOrDefault(person.getId(), 0));
        });
    }

    private static Relationship relationship(Connection link, Long userId) {
        if (link == null) {
            return Relationship.NONE;
        }
        if (link.isAccepted()) {
            return Relationship.ACCEPTED;
        }
        return link.getRequesterId().equals(userId) ? Relationship.PENDING_SENT : Relationship.PENDING_RECEIVED;
    }

    /** For each of {@code ids}, how many of the signed-in member's connections they share. Two queries for the whole page. */
    private Map<Long, Integer> mutualCounts(Long userId, Collection<Long> ids) {
        Set<Long> mine = connections.findForUser(userId, ConnectionStatus.ACCEPTED).stream()
                .map(c -> c.otherSide(userId)).collect(Collectors.toSet());
        Set<Long> wanted = new HashSet<>(ids);
        Map<Long, Set<Long>> theirs = new HashMap<>();
        for (Connection c : connections.findTouching(ids, ConnectionStatus.ACCEPTED)) {
            if (wanted.contains(c.getRequesterId())) {
                theirs.computeIfAbsent(c.getRequesterId(), k -> new HashSet<>()).add(c.getReceiverId());
            }
            if (wanted.contains(c.getReceiverId())) {
                theirs.computeIfAbsent(c.getReceiverId(), k -> new HashSet<>()).add(c.getRequesterId());
            }
        }
        Map<Long, Integer> counts = new HashMap<>();
        theirs.forEach((id, others) -> counts.put(id, (int) others.stream().filter(mine::contains).count()));
        return counts;
    }

    // ---- Requests ---------------------------------------------------------------------------------------------------------

    public ConnectionRequestDto sendRequest(Long requesterId, Long targetId) {
        try {
            return tx.execute(status -> {
                if (requesterId.equals(targetId)) {
                    throw new InvalidConnectionException("You can't send a connection request to yourself.");
                }
                User requester = users.findById(requesterId).orElseThrow(ConnectionNotFoundException::new);
                User receiver = users.findById(targetId).orElseThrow(ConnectionNotFoundException::new);
                if (receiver.isAdmin() || requester.isAdmin()) {
                    throw new InvalidConnectionException("You can only connect with other learners.");
                }

                Connection existing = connections.findBetween(requesterId, targetId).orElse(null);
                if (existing != null) {
                    throw new ConnectionConflictException(existing.isAccepted() ? "You're already connected with this person."
                            : existing.getRequesterId().equals(requesterId) ? "You've already sent this person a request."
                            : "This person has already sent you a request. Accept it from your invitations.");
                }
                if (connections.countByRequesterIdAndStatus(requesterId, ConnectionStatus.PENDING) >= MAX_PENDING_SENT) {
                    throw new InvalidConnectionException("You have " + MAX_PENDING_SENT + " requests waiting for an answer. "
                            + "Withdraw some before sending more.");
                }

                Connection saved = connections.saveAndFlush(new Connection(requesterId, targetId));
                notifications.createNotification(targetId, NotificationCategory.CONNECTION, "New connection request",
                        ConnectionUserDto.nameOf(requester) + " sent you a connection request.", NotificationPriority.NORMAL, "/network");
                return ConnectionRequestDto.from(saved, requester, receiver);
            });
        } catch (DataIntegrityViolationException e) {
            // The other person's request, or a second tap, got in at the same moment: the database allows one relationship per pair.
            throw new ConnectionConflictException("There is already a connection request between you and this person.");
        }
    }

    /** Requests waiting for the signed-in member's answer, newest first. */
    public List<ConnectionRequestDto> receivedRequests(Long userId) {
        return requestDtos(connections.findByReceiverIdAndStatusOrderByCreatedAtDesc(userId, ConnectionStatus.PENDING));
    }

    /** Requests the signed-in member has sent that haven't been answered, newest first. */
    public List<ConnectionRequestDto> sentRequests(Long userId) {
        return requestDtos(connections.findByRequesterIdAndStatusOrderByCreatedAtDesc(userId, ConnectionStatus.PENDING));
    }

    private List<ConnectionRequestDto> requestDtos(List<Connection> requests) {
        Set<Long> ids = new HashSet<>();
        requests.forEach(c -> {
            ids.add(c.getRequesterId());
            ids.add(c.getReceiverId());
        });
        Map<Long, User> byId = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return requests.stream()
                .filter(c -> byId.containsKey(c.getRequesterId()) && byId.containsKey(c.getReceiverId()))
                .map(c -> ConnectionRequestDto.from(c, byId.get(c.getRequesterId()), byId.get(c.getReceiverId())))
                .toList();
    }

    /** Only the person a request was sent to can accept it. Accepting twice is harmless. */
    public ConnectionRequestDto acceptRequest(Long userId, Long requestId) {
        return tx.execute(status -> {
            Connection request = connections.findByIdAndReceiverId(requestId, userId).orElseThrow(ConnectionNotFoundException::new);
            User requester = users.findById(request.getRequesterId()).orElseThrow(ConnectionNotFoundException::new);
            User receiver = users.findById(request.getReceiverId()).orElseThrow(ConnectionNotFoundException::new);
            if (!request.isAccepted()) {
                request.accept();
                connections.save(request);
                notifications.createNotification(requester.getId(), NotificationCategory.CONNECTION, "Connection request accepted",
                        ConnectionUserDto.nameOf(receiver) + " accepted your connection request.", NotificationPriority.NORMAL, "/network");
            }
            return ConnectionRequestDto.from(request, requester, receiver);
        });
    }

    /** Declining removes the request; the sender isn't told. */
    public void rejectRequest(Long userId, Long requestId) {
        tx.executeWithoutResult(status -> {
            Connection request = connections.findByIdAndReceiverId(requestId, userId)
                    .filter(c -> !c.isAccepted()).orElseThrow(ConnectionNotFoundException::new);
            connections.delete(request);
        });
    }

    /** The sender taking back a request nobody has answered yet. */
    public void withdrawRequest(Long userId, Long requestId) {
        tx.executeWithoutResult(status -> {
            Connection request = connections.findByIdAndRequesterId(requestId, userId)
                    .filter(c -> !c.isAccepted()).orElseThrow(ConnectionNotFoundException::new);
            connections.delete(request);
        });
    }

    // ---- Connections ------------------------------------------------------------------------------------------------------

    /** The signed-in member's connections, with how many connections each shares with them. */
    public List<ConnectionUserDto> connectionsOf(Long userId) {
        List<Connection> accepted = connections.findForUser(userId, ConnectionStatus.ACCEPTED);
        List<Long> otherIds = accepted.stream().map(c -> c.otherSide(userId)).toList();
        if (otherIds.isEmpty()) {
            return List.of();
        }
        Map<Long, User> byId = users.findAllById(otherIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Integer> mutual = mutualCounts(userId, otherIds);
        return accepted.stream()
                .filter(c -> byId.containsKey(c.otherSide(userId)))
                .map(c -> ConnectionUserDto.from(byId.get(c.otherSide(userId)), Relationship.ACCEPTED, c.getId(),
                        mutual.getOrDefault(c.otherSide(userId), 0)))
                .toList();
    }

    /** Ends a connection from either side. Removing one that isn't there is not an error. */
    public void removeConnection(Long userId, Long otherUserId) {
        tx.executeWithoutResult(status -> connections.findBetween(userId, otherUserId).filter(Connection::isAccepted)
                .ifPresent(connections::delete));
    }

    public long connectionCount(Long userId) {
        return connections.countForUser(userId, ConnectionStatus.ACCEPTED);
    }

    public boolean areConnected(Long a, Long b) {
        return a != null && b != null && !a.equals(b)
                && connections.findBetween(a, b).filter(Connection::isAccepted).isPresent();
    }
}
