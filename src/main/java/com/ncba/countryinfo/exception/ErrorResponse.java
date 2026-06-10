package com.ncba.countryinfo.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Structured error payload returned for every handled exception, replacing
 * Spring's default error body.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String message;
    private final String path;

    /** Field-level validation errors; present only for 400 responses. */
    private final List<FieldErrorDetail> fieldErrors;

    /** A single field validation failure. */
    @Getter
    @Builder
    public static class FieldErrorDetail {
        private final String field;
        private final String message;
    }
}
