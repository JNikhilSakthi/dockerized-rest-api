package com.medha.dockerizedrestapi.exception;

/** Thrown when a create/update would violate a business uniqueness rule. Mapped to HTTP 409. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
