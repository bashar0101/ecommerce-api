package com.apps.ecommerce.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productName, int available, int requested) {
        super("Not enough stock for " + productName + ": have " + available + ", want " + requested);
    }
}