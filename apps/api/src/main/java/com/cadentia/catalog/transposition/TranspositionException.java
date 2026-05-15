package com.cadentia.catalog.transposition;

public class TranspositionException extends RuntimeException {

    public TranspositionException(String message) {
        super(message);
    }

    public TranspositionException(String message, Throwable cause) {
        super(message, cause);
    }
}
