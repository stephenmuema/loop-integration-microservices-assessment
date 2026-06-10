package com.ncba.countryinfo.exception;

/**
 * Raised when a requested {@code CountryInfo} record does not exist, or when
 * the SOAP service cannot resolve a supplied country name to an ISO code.
 * Surfaced to clients as HTTP 404 Not Found.
 */
public class CountryNotFoundException extends RuntimeException {

    public CountryNotFoundException(String message) {
        super(message);
    }
}
