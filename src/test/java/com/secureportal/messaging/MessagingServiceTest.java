package com.secureportal.messaging;

import com.secureportal.network.ConnectionService;
import com.secureportal.network.NetworkExceptions;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagingServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MessagingService messagingService;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = new User("usera@example.com", "User A", null, Role.VIEWER);
        setField(userA, "id", 1L);

        userB = new User("userb@example.com", "User B", null, Role.VIEWER);
        setField(userB, "id", 2L);
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
    void sendMessage_whenNotConnected_throwsConnectionAccessDeniedException() {
        when(connectionService.areConnected(1L, 2L)).thenReturn(false);

        assertThrows(NetworkExceptions.ConnectionAccessDeniedException.class, () -> {
            messagingService.sendMessage(1L, 2L, "Hello user B!");
        });

        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void sendMessage_whenConnected_success() {
        when(connectionService.areConnected(1L, 2L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userA));
        when(userRepository.findById(2L)).thenReturn(Optional.of(userB));

        Conversation conversation = new Conversation(1L, 2L);
        setField(conversation, "id", 10L);
        when(conversationRepository.findConversationBetween(1L, 2L)).thenReturn(Optional.of(conversation));

        Message savedMsg = new Message(10L, 1L, "Hello user B!");
        setField(savedMsg, "id", 100L);
        when(messageRepository.save(any(Message.class))).thenReturn(savedMsg);

        MessageDto result = messagingService.sendMessage(1L, 2L, "Hello user B!");

        assertNotNull(result);
        assertEquals(100L, result.id());
        assertEquals(10L, result.conversationId());
        assertEquals(1L, result.senderId());
        assertEquals("Hello user B!", result.content());
    }

    @Test
    void getConversationHistory_whenNotParticipant_throwsConnectionAccessDeniedException() {
        Conversation conversation = new Conversation(1L, 2L); // Participants: User 1 and User 2
        setField(conversation, "id", 10L);
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));

        // User 3 tries to access conversation 10
        assertThrows(NetworkExceptions.ConnectionAccessDeniedException.class, () -> {
            messagingService.getConversationHistory(3L, 10L, org.springframework.data.domain.PageRequest.of(0, 10));
        });
    }

    @Test
    void getOrCreateConversation_whenNotConnected_throwsConnectionAccessDeniedException() {
        when(connectionService.areConnected(1L, 2L)).thenReturn(false);

        assertThrows(NetworkExceptions.ConnectionAccessDeniedException.class, () -> {
            messagingService.getOrCreateConversation(1L, 2L);
        });
    }
}
