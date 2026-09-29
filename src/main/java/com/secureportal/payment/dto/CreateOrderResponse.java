package com.secureportal.payment.dto;

import com.secureportal.payment.PaymentProvider;

import java.util.UUID;

/** {@code checkoutUrl} is set for Stripe (redirect there) and the simulated path; {@code keyId} is
 *  Razorpay's public key id, needed client-side to open its checkout widget. */
public record CreateOrderResponse(
        UUID paymentOrderId,
        UUID courseId,
        String courseTitle,
        int amountRupees,
        String currency,
        PaymentProvider provider,
        String orderId,
        String checkoutUrl,
        String keyId
) {
}
