package com.secureportal.network;

import com.secureportal.user.User;
import java.time.Instant;

public record ConnectionRequestDto(
    Long id,
    Long requesterId,
    String requesterName,
    String requesterEmail,
    String requesterPictureUrl,
    Long receiverId,
    String receiverName,
    String receiverEmail,
    String receiverPictureUrl,
    ConnectionStatus status,
    Instant createdAt,
    Instant respondedAt
) {
    public static ConnectionRequestDto from(Connection connection, User requester, User receiver) {
        return new ConnectionRequestDto(
            connection.getId(),
            requester.getId(),
            requester.getDisplayName() != null ? requester.getDisplayName() : requester.getEmail(),
            requester.getEmail(),
            requester.getPictureUrl(),
            receiver.getId(),
            receiver.getDisplayName() != null ? receiver.getDisplayName() : receiver.getEmail(),
            receiver.getEmail(),
            receiver.getPictureUrl(),
            connection.getStatus(),
            connection.getCreatedAt(),
            connection.getRespondedAt()
        );
    }
}
