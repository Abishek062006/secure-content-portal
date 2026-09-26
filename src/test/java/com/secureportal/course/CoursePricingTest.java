package com.secureportal.course;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoursePricingTest {

    private final Instant now = Instant.parse("2026-09-26T10:00:00Z");

    @Test
    void aFreeCourseHasNoDiscountEvenIfOneIsSubmitted() {
        CoursePricing pricing = CoursePricing.check(0, 50, now, now.plus(1, ChronoUnit.DAYS), now);

        assertThat(pricing.free()).isTrue();
        assertThat(pricing.discountPercent()).isZero();
        assertThat(pricing.discountEnd()).isNull();
        assertThat(pricing.finalPriceRupees(now)).isZero();
    }

    @Test
    void anActiveDiscountShowsTheReducedPriceAndOnlyWithinItsPeriod() {
        CoursePricing pricing = CoursePricing.check(1249, 20, null, now.plus(3, ChronoUnit.DAYS), now);

        assertThat(pricing.discountStart()).as("a discount with no start begins now").isEqualTo(now);
        assertThat(pricing.discountActive(now)).isTrue();
        assertThat(pricing.finalPriceRupees(now)).isEqualTo(999);
        assertThat(pricing.discountActive(now.plus(3, ChronoUnit.DAYS))).as("ended").isFalse();
        assertThat(pricing.finalPriceRupees(now.plus(4, ChronoUnit.DAYS))).isEqualTo(1249);
        assertThat(pricing.discountActive(now.minusSeconds(1))).as("not started").isFalse();
    }

    @Test
    void noDiscountMeansTheListedPrice() {
        CoursePricing pricing = CoursePricing.check(500, null, null, null, now);

        assertThat(pricing.free()).isFalse();
        assertThat(pricing.finalPriceRupees(now)).isEqualTo(500);
    }

    @Test
    void rejectsPricesAndDiscountsThatDontMakeSense() {
        Instant later = now.plus(1, ChronoUnit.DAYS);
        assertThatThrownBy(() -> CoursePricing.check(-1, 0, null, null, now)).isInstanceOf(CourseStructureException.class);
        assertThatThrownBy(() -> CoursePricing.check(2_000_000, 0, null, null, now)).isInstanceOf(CourseStructureException.class);
        assertThatThrownBy(() -> CoursePricing.check(100, 101, null, later, now)).isInstanceOf(CourseStructureException.class);
        assertThatThrownBy(() -> CoursePricing.check(100, 10, null, null, now))
                .hasMessageContaining("when the discount ends");
        assertThatThrownBy(() -> CoursePricing.check(100, 10, later, now.plusSeconds(5), now))
                .hasMessageContaining("end after it starts");
        assertThatThrownBy(() -> CoursePricing.check(100, 10, now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), now))
                .hasMessageContaining("already passed");
    }
}
