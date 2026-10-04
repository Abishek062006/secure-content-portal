package com.secureportal.interview;

import java.util.Optional;

/**
 * How well the learner's camera setup worked during an interview, as measured by their browser: the share of the time their face
 * was in frame and the share of the time the lighting was good. Feedback only. The browser is not trusted: too few readings to
 * say anything, or numbers that can't be right, are dropped.
 */
public record SetupQuality(Double faceVisiblePercent, Double lightingGoodPercent, Integer samples) {

    /** About a minute of readings (one a second); less says too little about the whole interview. */
    static final int MIN_SAMPLES = 30;

    /** What is kept: whole percentages from 0 to 100, each null when the browser had no reading for it. */
    public record Measured(Integer faceVisiblePercent, Integer lightingGoodPercent) {
    }

    public Optional<Measured> measured() {
        if (samples == null || samples < MIN_SAMPLES) {
            return Optional.empty();
        }
        Integer face = percent(faceVisiblePercent);
        Integer lighting = percent(lightingGoodPercent);
        return face == null && lighting == null ? Optional.empty() : Optional.of(new Measured(face, lighting));
    }

    private static Integer percent(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        return (int) Math.max(0, Math.min(100, Math.round(value)));
    }
}
