package com.secureportal.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SimulatedCheckoutRequest(@NotNull UUID paymentOrderId) {
}
