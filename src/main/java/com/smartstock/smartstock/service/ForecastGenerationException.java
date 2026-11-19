package com.smartstock.smartstock.service;

public class ForecastGenerationException extends RuntimeException {
    public ForecastGenerationException(String message) {
        super(message);
    }

    public ForecastGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
