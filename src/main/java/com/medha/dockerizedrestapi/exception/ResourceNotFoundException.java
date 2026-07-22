package com.medha.dockerizedrestapi.exception;

/** Thrown when a requested entity does not exist. Mapped to HTTP 404 by the global handler. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException forEntity(String entity, Object id) {
        return new ResourceNotFoundException(entity + " not found with id " + id);
    }
}
