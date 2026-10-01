package com.secureportal.network;

import com.secureportal.notification.NotificationCategory;
import com.secureportal.notification.NotificationPriority;
import com.secureportal.notification.NotificationService;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionServiceTest {

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ConnectionService connectionService;

    private User alice;
    private User bob;
    private User charlie;

    @BeforeEach
    void setUp() {
        alice = new User("alice@example.com", "Alice Smith", null, Role.VIEWER);
        setField(alice, "id", 1L);

        bob = new User("bob@example.com", "Bob Jones", null, Role.VIEWER);
        setField(bob, "id", 2L);

        charlie = new User("charlie@example.com", "Charlie Brown", null, Role.VIEWER);
        setField(charlie, "id", 3L);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void sendRequestToSelf_throwsSelfConnectionException() {
        assertThrows(NetworkExceptions.SelfConnectionException.class, () -> {
            connectionService.sendRequest(1L, 1L);
        });
    }

    @Test
    void sendRequest_whenAlreadyPending_throwsDuplicateConnectionException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));

        Connection pendingConn = new Connection(1L, 2L);
        when(connectionRepository.findRelationship(1L, 2L)).thenReturn(Optional.of(pendingConn));

        assertThrows(NetworkExceptions.DuplicateConnectionException.class, () -> {
            connectionService.sendRequest(1L, 2L);
        });
    }

    @Test
    void sendRequest_whenAlreadyAccepted_throwsDuplicateConnectionException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));

        Connection acceptedConn = new Connection(1L, 2L);
        acceptedConn.setStatus(ConnectionStatus.ACCEPTED);
        when(connectionRepository.findRelationship(1L, 2L)).thenReturn(Optional.of(acceptedConn));

        assertThrows(NetworkExceptions.DuplicateConnectionException.class, () -> {
            connectionService.sendRequest(1L, 2L);
        });
    }

    @Test
    void sendRequest_success_createsConnectionAndTriggersNotification() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(connectionRepository.findRelationship(1L, 2L)).thenReturn(Optional.empty());

        Connection savedConn = new Connection(1L, 2L);
        setField(savedConn, "id", 100L);
        when(connectionRepository.save(any(Connection.class))).thenReturn(savedConn);

        ConnectionRequestDto result = connectionService.sendRequest(1L, 2L);

        assertNotNull(result);
        assertEquals(100L, result.id());
        assertEquals(1L, result.requesterId());
        assertEquals(2L, result.receiverId());
        assertEquals(ConnectionStatus.PENDING, result.status());

        verify(notificationService).createNotification(
                eq(2L),
                eq(NotificationCategory.CONNECTION),
                eq("New Connection Request"),
                contains("Alice Smith sent you a connection request."),
                eq(NotificationPriority.NORMAL),
                eq("/network")
        );
    }

    @Test
    void acceptRequest_byNonReceiver_throwsConnectionAccessDeniedException() {
        Connection conn = new Connection(1L, 2L); // Requester: Alice(1), Receiver: Bob(2)
        setField(conn, "id", 50L);

        when(connectionRepository.findById(50L)).thenReturn(Optional.of(conn));

        // Charlie (id 3) tries to accept Bob's request
        assertThrows(NetworkExceptions.ConnectionAccessDeniedException.class, () -> {
            connectionService.acceptRequest(3L, 50L);
        });
    }

    @Test
    void acceptRequest_success_updatesStatusAndTriggersNotification() {
        Connection conn = new Connection(1L, 2L);
        setField(conn, "id", 50L);

        when(connectionRepository.findById(50L)).thenReturn(Optional.of(conn));
        when(connectionRepository.save(any(Connection.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));

        ConnectionRequestDto result = connectionService.acceptRequest(2L, 50L);

        assertEquals(ConnectionStatus.ACCEPTED, result.status());
        assertNotNull(result.respondedAt());

        verify(notificationService).createNotification(
                eq(1L),
                eq(NotificationCategory.CONNECTION),
                eq("Connection Request Accepted"),
                contains("Bob Jones accepted your connection request."),
                eq(NotificationPriority.NORMAL),
                eq("/network")
        );
    }

    @Test
    void rejectRequest_byNonReceiver_throwsConnectionAccessDeniedException() {
        Connection conn = new Connection(1L, 2L);
        setField(conn, "id", 50L);

        when(connectionRepository.findById(50L)).thenReturn(Optional.of(conn));

        // Alice (requester) tries to reject her own request instead of withdrawing it
        assertThrows(NetworkExceptions.ConnectionAccessDeniedException.class, () -> {
            connectionService.rejectRequest(1L, 50L);
        });
    }

    @Test
    void withdrawRequest_byNonSender_throwsConnectionAccessDeniedException() {
        Connection conn = new Connection(1L, 2L);
        setField(conn, "id", 50L);

        when(connectionRepository.findById(50L)).thenReturn(Optional.of(conn));

        // Bob (receiver) tries to withdraw Alice's request instead of rejecting it
        assertThrows(NetworkExceptions.ConnectionAccessDeniedException.class, () -> {
            connectionService.withdrawRequest(2L, 50L);
        });
    }
}
