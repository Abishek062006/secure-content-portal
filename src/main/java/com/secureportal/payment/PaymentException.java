package com.secureportal.payment;

/** A payment request that's invalid on its face: the course is free, isn't payable at all (it's a REGISTER
 *  course), or the requested provider isn't turned on. */
public class PaymentException extends RuntimeException {

    public PaymentException(String message) {
        super(message);
    }
}
