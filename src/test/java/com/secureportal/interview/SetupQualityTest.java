package com.secureportal.interview;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SetupQualityTest {

    @Test
    void keepsWholePercentagesWhenThereAreEnoughReadings() {
        SetupQuality.Measured m = new SetupQuality(96.4, 87.6, 240).measured().orElseThrow();

        assertThat(m.faceVisiblePercent()).isEqualTo(96);
        assertThat(m.lightingGoodPercent()).isEqualTo(88);
    }

    @Test
    void tooFewReadingsSayNothingAboutTheWholeInterview() {
        assertThat(new SetupQuality(100.0, 100.0, SetupQuality.MIN_SAMPLES - 1).measured()).isEmpty();
        assertThat(new SetupQuality(100.0, 100.0, null).measured()).isEmpty();
        assertThat(new SetupQuality(100.0, 100.0, SetupQuality.MIN_SAMPLES).measured()).isPresent();
    }

    @Test
    void aMissingReadingStaysMissingAndNothingAtAllIsDropped() {
        SetupQuality.Measured lightingOnly = new SetupQuality(null, 70.0, 100).measured().orElseThrow();
        assertThat(lightingOnly.faceVisiblePercent()).isNull();
        assertThat(lightingOnly.lightingGoodPercent()).isEqualTo(70);

        assertThat(new SetupQuality(null, null, 100).measured()).isEmpty();
    }

    @Test
    void impossibleNumbersAreClampedAndNonNumbersIgnored() {
        SetupQuality.Measured m = new SetupQuality(250.0, -40.0, 100).measured().orElseThrow();
        assertThat(m.faceVisiblePercent()).isEqualTo(100);
        assertThat(m.lightingGoodPercent()).isZero();

        SetupQuality.Measured partly = new SetupQuality(Double.NaN, Double.POSITIVE_INFINITY, 100).measured().orElse(null);
        assertThat(partly).isNull();
    }
}
