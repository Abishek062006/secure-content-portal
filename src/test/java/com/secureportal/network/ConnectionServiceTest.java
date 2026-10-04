package com.secureportal.network;

import com.secureportal.notification.NotificationService;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionServiceTest {

    private final ConnectionRepository connections = mock(ConnectionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

    private ConnectionService service() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new ConnectionService(connections, users, notifications, transactions);
    }

    @Test
    void aMemberCannotHaveMoreThanTheLimitOfRequestsWaiting() {
        User from = new User("a@example.com", "A", null, Role.VIEWER);
        User to = new User("b@example.com", "B", null, Role.VIEWER);
        when(users.findById(1L)).thenReturn(Optional.of(from));
        when(users.findById(2L)).thenReturn(Optional.of(to));
        when(connections.findBetween(1L, 2L)).thenReturn(Optional.empty());
        when(connections.countByRequesterIdAndStatus(1L, ConnectionStatus.PENDING)).thenReturn((long) ConnectionService.MAX_PENDING_SENT);

        assertThatThrownBy(() -> service().sendRequest(1L, 2L))
                .isInstanceOf(InvalidConnectionException.class).hasMessageContaining("waiting for an answer");

        verify(connections, never()).saveAndFlush(any());
        verify(notifications, never()).createNotification(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void aLongSearchIsCutToTheLimitBeforeItReachesTheDatabase() {
        when(users.findPeople(eq(1L), eq(Role.ADMIN), eq("x".repeat(ConnectionService.MAX_SEARCH_LENGTH)), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service().suggestions(1L, "  " + "x".repeat(500) + "  ", org.springframework.data.domain.PageRequest.of(0, 20));

        verify(users).findPeople(eq(1L), eq(Role.ADMIN), eq("x".repeat(ConnectionService.MAX_SEARCH_LENGTH)), any());
    }
}
