package com.secureportal.course;

public class MaterialNotFoundException extends RuntimeException {

    public MaterialNotFoundException() {
        super("That material doesn't exist.");
    }
}
