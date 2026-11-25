package com.shaurya.workorders.error;

/** The request is valid but clashes with current state (duplicate, wrong status, ...). Maps to 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
