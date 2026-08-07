package com.apps.ecommerce.exception;

/**
 * ResourceNotFoundException
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(Class<?> resource, Object identifier) {
        super(resource.getSimpleName() + " with " + identifier + " Not Found.");
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
