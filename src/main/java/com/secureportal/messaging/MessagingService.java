package com.secureportal.messaging;

import com.secureportal.network.ConnectionService;
import com.secureportal.network.NetworkExceptions;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MessagingService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConnectionService connectionService;
    private final UserRepository userRepository;

    public MessagingService(ConversationRepository conversationRepository,
                           MessageRepository messageRepository,
                           ConnectionService connectionService,
                           UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.connectionService = connectionService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> getConversations(Long currentUserId) {
        List<Conversation> conversations = conversationRepository.findAllForUser(currentUserId);

        return conversations.stream().map(c -> {
            Long otherUserId = c.getOtherUserId(currentUserId);
            User otherUser = userRepository.findById(otherUserId).orElse(null);
            if (otherUser == null) return null;

            String lastContent = messageRepository.findTop1ByConversationIdOrderByCreatedAtDesc(c.getId())
                    .map(Message::getContent)
                    .orElse("No messages yet");

            long unread = messageRepository.countUnreadMessages(c.getId(), currentUserId);

            return ConversationDto.from(c, otherUser, lastContent, unread);
        }).filter(dto -> dto != null).toList();
    }

    @Transactional(readOnly = true)
    public Page<MessageDto> getConversationHistory(Long currentUserId, Long conversationId, Pageable pageable) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(NetworkExceptions.ConnectionNotFoundException::new);

        if (!conversation.getUser1Id().equals(currentUserId) && !conversation.getUser2Id().equals(currentUserId)) {
            throw new NetworkExceptions.ConnectionAccessDeniedException("You are not a participant in this conversation.");
        }

        Page<Message> messagePage = messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);

        List<MessageDto> dtoList = messagePage.getContent().stream().map(m -> {
            User sender = userRepository.findById(m.getSenderId()).orElse(null);
            return MessageDto.from(m, sender);
        }).toList();

        return new PageImpl<>(dtoList, pageable, messagePage.getTotalElements());
    }

    @Transactional
    public MessageDto sendMessage(Long senderId, Long targetUserId, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty.");
        }

        String sanitizedContent = content.trim();
        if (sanitizedContent.length() > 2000) {
            sanitizedContent = sanitizedContent.substring(0, 2000);
        }

        // STRICT SECURITY ENFORCEMENT: Users can message ONLY accepted connections
        if (!connectionService.areConnected(senderId, targetUserId)) {
            throw new NetworkExceptions.ConnectionAccessDeniedException("You can only message accepted connections.");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(NetworkExceptions.ConnectionNotFoundException::new);
        User recipient = userRepository.findById(targetUserId)
                .orElseThrow(NetworkExceptions.ConnectionNotFoundException::new);

        Conversation conversation = conversationRepository.findConversationBetween(senderId, targetUserId)
                .orElseGet(() -> conversationRepository.save(new Conversation(senderId, targetUserId)));

        Message message = new Message(conversation.getId(), senderId, sanitizedContent);
        Message savedMessage = messageRepository.save(message);

        conversation.setLastMessageAt(savedMessage.getCreatedAt());
        conversationRepository.save(conversation);

        return MessageDto.from(savedMessage, sender);
    }

    @Transactional
    public int markAsRead(Long currentUserId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(NetworkExceptions.ConnectionNotFoundException::new);

        if (!conversation.getUser1Id().equals(currentUserId) && !conversation.getUser2Id().equals(currentUserId)) {
            throw new NetworkExceptions.ConnectionAccessDeniedException("You are not a participant in this conversation.");
        }

        return messageRepository.markAsRead(conversationId, currentUserId, Instant.now());
    }

    @Transactional(readOnly = true)
    public ConversationDto getOrCreateConversation(Long currentUserId, Long targetUserId) {
        if (!connectionService.areConnected(currentUserId, targetUserId)) {
            throw new NetworkExceptions.ConnectionAccessDeniedException("You can only message accepted connections.");
        }

        User otherUser = userRepository.findById(targetUserId)
                .orElseThrow(NetworkExceptions.ConnectionNotFoundException::new);

        Conversation conversation = conversationRepository.findConversationBetween(currentUserId, targetUserId)
                .orElseGet(() -> conversationRepository.save(new Conversation(currentUserId, targetUserId)));

        String lastContent = messageRepository.findTop1ByConversationIdOrderByCreatedAtDesc(conversation.getId())
                .map(Message::getContent)
                .orElse("");

        long unread = messageRepository.countUnreadMessages(conversation.getId(), currentUserId);

        return ConversationDto.from(conversation, otherUser, lastContent, unread);
    }
}
