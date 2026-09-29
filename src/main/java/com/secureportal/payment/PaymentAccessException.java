package com.secureportal.payment;

/** A payment order that doesn't belong to the caller, or that no longer exists. */
public class PaymentAccessException extends RuntimeException {

    public PaymentAccessException(String message) {
        super(message);
    }
}
