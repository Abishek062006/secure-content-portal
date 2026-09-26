package com.secureportal.api.dto;

import com.secureportal.gamification.PointTransaction;

import java.time.Instant;

/** One line of a points history. What earned it (the internal source id) stays server-side. */
public record PointTransactionDto(Long id, Long userId, int amount, String actionType, String description, String stream, Instant createdAt) {

    public static PointTransactionDto of(PointTransaction transaction) {
        return new PointTransactionDto(transaction.getId(), transaction.getUserId(), transaction.getAmount(),
                transaction.getActionType(), transaction.getDescription(), transaction.getStream(), transaction.getCreatedAt());
    }
}
