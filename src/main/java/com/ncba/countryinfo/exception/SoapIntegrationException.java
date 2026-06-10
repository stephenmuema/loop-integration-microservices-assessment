package com.ncba.countryinfo.exception;

/**
 * Raised when the downstream CountryInfo SOAP service cannot be reached, times
 * out, returns a fault, or yields an unusable response (including when the
 * circuit breaker is open). Surfaced to clients as HTTP 502 Bad Gateway.
 */
public class SoapIntegrationException extends RuntimeException {

    public SoapIntegrationException(String message) {
        super(message);
    }

    public SoapIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
