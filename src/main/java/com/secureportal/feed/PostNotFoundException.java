package com.secureportal.feed;

public class PostNotFoundException extends RuntimeException {

    public PostNotFoundException() {
        super("That post doesn't exist.");
    }
}
