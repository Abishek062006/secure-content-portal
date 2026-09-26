package com.secureportal.course;

import java.time.Instant;

/** The price rules in one place: a whole-rupee price and an optional percentage discount that runs for a period. */
public record CoursePricing(int priceRupees, int discountPercent, Instant discountStart, Instant discountEnd) {

    static final int MAX_PRICE = 1_000_000;

    /** Validates admin input and fills in the defaults (a discount with no start begins now; a free course has none). */
    public static CoursePricing check(Integer price, Integer percent, Instant start, Instant end, Instant now) {
        int rupees = price == null ? 0 : price;
        int discount = percent == null ? 0 : percent;
        if (rupees < 0 || rupees > MAX_PRICE) {
            throw new CourseStructureException("The price must be between 0 and " + MAX_PRICE + " rupees.");
        }
        if (discount < 0 || discount > 100) {
            throw new CourseStructureException("The discount must be between 0 and 100 percent.");
        }
        if (rupees == 0 || discount == 0) {
            return new CoursePricing(rupees, 0, null, null);
        }
        if (end == null) {
            throw new CourseStructureException("Set when the discount ends.");
        }
        Instant from = start == null ? now : start;
        if (!end.isAfter(from)) {
            throw new CourseStructureException("The discount must end after it starts.");
        }
        if (!end.isAfter(now)) {
            throw new CourseStructureException("The discount's end date has already passed.");
        }
        return new CoursePricing(rupees, discount, from, end);
    }

    public boolean free() {
        return priceRupees == 0;
    }

    public boolean discountActive(Instant now) {
        return discountPercent > 0 && discountStart != null && discountEnd != null
                && !now.isBefore(discountStart) && now.isBefore(discountEnd);
    }

    /** What a learner would pay right now. */
    public int finalPriceRupees(Instant now) {
        return discountActive(now) ? (int) Math.round(priceRupees * (100 - discountPercent) / 100.0) : priceRupees;
    }
}
