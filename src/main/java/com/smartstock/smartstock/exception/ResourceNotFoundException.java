package com.smartstock.smartstock.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// When @ResponseStatus is here, Spring automatically
// returns 404 when this exception is thrown
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    // Convenience constructor
    // Usage: throw new ResourceNotFoundException("Product", "id", 5)
    // Message: "Product not found with id: 5"
    public ResourceNotFoundException(String resource,
                                     String field,
                                     Object value) {
        super(resource + " not found with " + field + ": " + value);
    }
}