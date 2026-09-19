package com.fincore.common.exception;

public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }

    public NotFoundException(String resource, Object id) {
        super("NOT_FOUND", resource + " not found: " + id);
    }
}