package com.secureportal.messaging;

import com.secureportal.user.User;

import java.time.Instant;

public record MessageDto(Long id, Long conversationId, Long senderId, String senderName, String senderPictureUrl, String content,
                         Instant createdAt, Instant readAt) {

    public static MessageDto from(Message message, User sender) {
        return new MessageDto(message.getId(), message.getConversationId(), message.getSenderId(), ConversationDto.nameOf(sender),
                sender == null ? null : sender.getPictureUrl(), message.getContent(), message.getCreatedAt(), message.getReadAt());
    }
}
