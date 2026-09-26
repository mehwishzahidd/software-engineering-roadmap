package com.example.library.service;

/** Thrown when a business rule prevents a checkout or return. */
public class LoanNotAllowedException extends RuntimeException {

    public LoanNotAllowedException(String message) {
        super(message);
    }
}
