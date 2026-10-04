package com.secureportal.network;

import com.secureportal.user.User;

/**
 * Another member as the network page shows them: a name and a picture, never an email address, so signing in is not enough to
 * learn who else has an account.
 */
public record ConnectionUserDto(Long id, String displayName, String pictureUrl, Relationship relationshipStatus, Long connectionId,
                                int mutualConnectionsCount) {

    /** What to call someone who has no display name. */
    static final String UNNAMED = "Member";

    static String nameOf(User user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank() ? UNNAMED : user.getDisplayName();
    }

    public static ConnectionUserDto from(User user, Relationship relationship, Long connectionId, int mutualConnections) {
        return new ConnectionUserDto(user.getId(), nameOf(user), user.getPictureUrl(), relationship, connectionId, mutualConnections);
    }
}
