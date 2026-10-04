package com.secureportal.network;

/** A request is pending until the person it was sent to accepts it; declining or withdrawing one simply removes it. */
public enum ConnectionStatus {
    PENDING,
    ACCEPTED
}
