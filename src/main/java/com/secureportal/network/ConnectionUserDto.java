package com.secureportal.network;

import com.secureportal.user.Role;
import com.secureportal.user.User;

public record ConnectionUserDto(
    Long id,
    String displayName,
    String email,
    String pictureUrl,
    Role role,
    String relationshipStatus,
    Long connectionId,
    int mutualConnectionsCount
) {
    public static ConnectionUserDto from(User user, String relationshipStatus, Long connectionId, int mutualConnectionsCount) {
        return new ConnectionUserDto(
            user.getId(),
            user.getDisplayName() != null ? user.getDisplayName() : user.getEmail(),
            user.getEmail(),
            user.getPictureUrl(),
            user.getRole(),
            relationshipStatus,
            connectionId,
            mutualConnectionsCount
        );
    }
}
