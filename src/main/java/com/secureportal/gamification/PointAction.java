package com.secureportal.gamification;

import java.util.Arrays;
import java.util.Optional;

/**
 * Every kind of point movement, so action names are never free text. Actions with a {@code defaultPoints} are the ones admins
 * can re-price under Point rules; the others (badge rewards, hackathon rewards, manual adjustments) carry their own amount.
 */
public enum PointAction {
    DAILY_CHECKIN(5, true),
    LESSON_COMPLETED(10, true),
    QUIZ_PASSED(20, true),
    COURSE_COMPLETED(100, true),
    MOCK_INTERVIEW_COMPLETE(50, true),
    HACKATHON_REGISTER(0, false),
    BADGE_UNLOCKED(0, false),
    MANUAL_ADJUSTMENT(0, false);

    private final int defaultPoints;
    private final boolean priceable;

    PointAction(int defaultPoints, boolean priceable) {
        this.defaultPoints = defaultPoints;
        this.priceable = priceable;
    }

    public int defaultPoints() {
        return defaultPoints;
    }

    /** Whether admins may set this action's points in Point rules. */
    public boolean isPriceable() {
        return priceable;
    }

    public static Optional<PointAction> find(String name) {
        return Arrays.stream(values()).filter(a -> a.name().equals(name)).findFirst();
    }
}
