package com.secureportal.network;

import com.secureportal.user.User;

import java.time.Instant;

/** A connection request with the names and pictures of both people; no email addresses. */
public record ConnectionRequestDto(Long id, Long requesterId, String requesterName, String requesterPictureUrl, Long receiverId,
                                   String receiverName, String receiverPictureUrl, ConnectionStatus status, Instant createdAt,
                                   Instant respondedAt) {

    public static ConnectionRequestDto from(Connection connection, User requester, User receiver) {
        return new ConnectionRequestDto(connection.getId(), requester.getId(), ConnectionUserDto.nameOf(requester),
                requester.getPictureUrl(), receiver.getId(), ConnectionUserDto.nameOf(receiver), receiver.getPictureUrl(),
                connection.getStatus(), connection.getCreatedAt(), connection.getRespondedAt());
    }
}
