package com.secureportal.payment.dto;

import com.secureportal.payment.PaymentProvider;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateOrderRequest(@NotNull UUID courseId, @NotNull PaymentProvider provider) {
}
