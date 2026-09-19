package com.fincore.common.exception;

/**
 * Base class for all expected business exceptions in FinCore.
 * These map to specific HTTP statuses via a global exception handler.
 */
public abstract class BusinessException extends RuntimeException {

    private final String code;

    protected BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}