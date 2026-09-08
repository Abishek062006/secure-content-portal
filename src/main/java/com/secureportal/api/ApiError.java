package com.secureportal.api;

/** Uniform JSON error body for the {@code /api/**} surface. */
public record ApiError(String error) {
}
