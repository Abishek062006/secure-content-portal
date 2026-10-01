package com.secureportal.messaging;

import com.secureportal.user.User;
import java.time.Instant;

public record ConversationDto(
    Long id,
    Long otherUserId,
    String otherUserName,
    String otherUserEmail,
    String otherUserPictureUrl,
    String lastMessageContent,
    Instant lastMessageAt,
    long unreadCount
) {
    public static ConversationDto from(Conversation conversation, User otherUser, String lastMessageContent, long unreadCount) {
        return new ConversationDto(
            conversation.getId(),
            otherUser.getId(),
            otherUser.getDisplayName() != null ? otherUser.getDisplayName() : otherUser.getEmail(),
            otherUser.getEmail(),
            otherUser.getPictureUrl(),
            lastMessageContent,
            conversation.getLastMessageAt(),
            unreadCount
        );
    }
}
