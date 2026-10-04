package com.secureportal.messaging;

import com.secureportal.user.User;

import java.time.Instant;

/** A conversation as its member sees it: who it's with (a name and picture, never an email) and what was said last. */
public record ConversationDto(Long id, Long otherUserId, String otherUserName, String otherUserPictureUrl, String lastMessageContent,
                              Instant lastMessageAt, long unreadCount) {

    static final String UNNAMED = "Member";

    static String nameOf(User user) {
        return user == null || user.getDisplayName() == null || user.getDisplayName().isBlank() ? UNNAMED : user.getDisplayName();
    }

    public static ConversationDto from(Conversation conversation, User other, String lastMessage, long unread) {
        return new ConversationDto(conversation.getId(), other.getId(), nameOf(other), other.getPictureUrl(), lastMessage,
                conversation.getLastMessageAt(), unread);
    }
}
