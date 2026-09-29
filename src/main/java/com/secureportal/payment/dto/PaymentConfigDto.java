package com.secureportal.payment.dto;

import com.secureportal.payment.PaymentProvider;

import java.util.List;

public record PaymentConfigDto(
        boolean stripeEnabled,
        String stripePublishableKey,
        boolean razorpayEnabled,
        String razorpayKeyId,
        boolean simulatedEnabled,
        List<PaymentProvider> availableProviders
) {
}
