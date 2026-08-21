package com.internship.crm.common.exception;

import java.util.Objects;

/**
 * Client-safe description of one invalid request field or parameter.
 */
public record FieldValidationError(String field, String message) {

    public FieldValidationError {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }
}
