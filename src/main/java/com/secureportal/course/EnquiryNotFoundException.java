package com.secureportal.course;

public class EnquiryNotFoundException extends RuntimeException {

    public EnquiryNotFoundException() {
        super("That enquiry no longer exists.");
    }
}
