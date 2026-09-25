package com.secureportal.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AttemptSummaryDto(UUID id, String status, Instant startedAt, Instant submittedAt, Integer scorePercent,
                                Boolean passed, boolean timedOut) {
}
