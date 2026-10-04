package com.secureportal.messaging;

import com.secureportal.network.ConnectionNotFoundException;
import com.secureportal.network.ConnectionService;
import com.secureportal.network.InvalidConnectionException;
import com.secureportal.network.NetworkAccessException;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One-to-one messages, only between members who have accepted each other's connection. A conversation is only ever visible to its
 * two members (anyone else's id looks like it doesn't exist) and every list is read in a few queries.
 */
@Service
public class MessagingService {

    public static final int MAX_LENGTH = 2000;
    /** How many conversations the list shows. */
    static final int RECENT_CONVERSATIONS = 100;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ConnectionService connectionService;
    private final UserRepository users;
    private final TransactionTemplate tx;

    public MessagingService(ConversationRepository conversations, MessageRepository messages, ConnectionService connectionService,
                            UserRepository users, PlatformTransactionManager transactionManager) {
        this.conversations = conversations;
        this.messages = messages;
        this.connectionService = connectionService;
        this.users = users;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** The member's conversations, most recent first, each with its last message and how many are unread. */
    public List<ConversationDto> conversations(Long userId) {
        List<Conversation> recent = conversations.findRecentForUser(userId, PageRequest.of(0, RECENT_CONVERSATIONS));
        if (recent.isEmpty()) {
            return List.of();
        }
        List<Long> ids = recent.stream().map(Conversation::getId).toList();
        Map<Long, String> last = messages.findLatestIn(ids).stream()
                .collect(Collectors.toMap(Message::getConversationId, Message::getContent, (a, b) -> a));
        Map<Long, Long> unread = new HashMap<>();
        for (Object[] row : messages.unreadCounts(ids, userId)) {
            unread.put((Long) row[0], ((Number) row[1]).longValue());
        }
        Map<Long, User> others = users.findAllById(recent.stream().map(c -> c.otherUserId(userId)).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return recent.stream()
                .filter(c -> others.containsKey(c.otherUserId(userId)))
                .map(c -> ConversationDto.from(c, others.get(c.otherUserId(userId)), last.getOrDefault(c.getId(), ""),
                        unread.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    /** One page of a conversation, newest first. */
    public Page<MessageDto> history(Long userId, Long conversationId, Pageable pageable) {
        Conversation conversation = conversations.findForParticipant(conversationId, userId).orElseThrow(ConnectionNotFoundException::new);
        Page<Message> page = messages.findByConversationIdOrderByCreatedAtDescIdDesc(conversationId, pageable);
        Map<Long, User> people = users.findAllById(Set.of(conversation.getUser1Id(), conversation.getUser2Id())).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return new PageImpl<>(page.getContent().stream().map(m -> MessageDto.from(m, people.get(m.getSenderId()))).toList(),
                pageable, page.getTotalElements());
    }

    /** Sends a message to a connection. Someone who isn't one gets a refusal; a first message that races the other person's is retried. */
    public MessageDto send(Long senderId, Long targetId, String content) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            throw new InvalidConnectionException("Write a message first.");
        }
        if (text.length() > MAX_LENGTH) {
            throw new InvalidConnectionException("Keep a message under " + MAX_LENGTH + " characters.");
        }
        try {
            return tx.execute(status -> store(senderId, targetId, text));
        } catch (DataIntegrityViolationException e) {
            // Both of you sent the very first message at the same moment; the conversation exists now, so use it.
            return tx.execute(status -> store(senderId, targetId, text));
        }
    }

    private MessageDto store(Long senderId, Long targetId, String text) {
        if (!connectionService.areConnected(senderId, targetId)) {
            throw new NetworkAccessException("You can only message people you're connected with.");
        }
        User sender = users.findById(senderId).orElseThrow(ConnectionNotFoundException::new);
        Conversation conversation = conversationBetween(senderId, targetId);
        Message saved = messages.save(new Message(conversation.getId(), senderId, text));
        conversation.messageSentAt(saved.getCreatedAt());
        conversations.save(conversation);
        return MessageDto.from(saved, sender);
    }

    /** Marks everything the other person sent in this conversation as read. */
    public int markRead(Long userId, Long conversationId) {
        return tx.execute(status -> {
            conversations.findForParticipant(conversationId, userId).orElseThrow(ConnectionNotFoundException::new);
            return messages.markRead(conversationId, userId, Instant.now());
        });
    }

    /** The conversation with a connection, started if there isn't one yet. */
    public ConversationDto open(Long userId, Long targetId) {
        if (!connectionService.areConnected(userId, targetId)) {
            throw new NetworkAccessException("You can only message people you're connected with.");
        }
        try {
            return tx.execute(status -> describe(userId, targetId));
        } catch (DataIntegrityViolationException e) {
            return tx.execute(status -> describe(userId, targetId));
        }
    }

    private ConversationDto describe(Long userId, Long targetId) {
        User other = users.findById(targetId).orElseThrow(ConnectionNotFoundException::new);
        Conversation conversation = conversationBetween(userId, targetId);
        String last = messages.findLatestIn(List.of(conversation.getId())).stream().map(Message::getContent).findFirst().orElse("");
        long unread = messages.unreadCounts(List.of(conversation.getId()), userId).stream()
                .mapToLong(row -> ((Number) row[1]).longValue()).sum();
        return ConversationDto.from(conversation, other, last, unread);
    }

    private Conversation conversationBetween(Long a, Long b) {
        long low = Math.min(a, b);
        long high = Math.max(a, b);
        return conversations.findByUser1IdAndUser2Id(low, high).orElseGet(() -> conversations.saveAndFlush(new Conversation(a, b)));
    }
}
