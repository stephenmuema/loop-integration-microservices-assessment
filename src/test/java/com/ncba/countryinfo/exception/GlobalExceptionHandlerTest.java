package com.ncba.countryinfo.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ncba.countryinfo.dto.CountryNameRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/countries/5");
    }

    @Test
    void notFoundMapsTo404() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new CountryNotFoundException("Country with id 5 not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getError()).isEqualTo("Not Found");
        assertThat(response.getBody().getMessage()).contains("id 5");
        assertThat(response.getBody().getPath()).isEqualTo("/api/countries/5");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }

    @Test
    void soapMapsTo502() {
        ResponseEntity<ErrorResponse> response =
                handler.handleSoap(new SoapIntegrationException("upstream down"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getStatus()).isEqualTo(502);
    }

    @Test
    void genericMapsTo500WithSafeMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneric(new RuntimeException("DB password is hunter2"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).doesNotContain("hunter2");
        assertThat(response.getBody().getMessage()).contains("unexpected error");
    }

    @Test
    void validationMapsTo400WithFieldErrors() throws Exception {
        BindingResult bindingResult =
                new BeanPropertyBindingResult(new CountryNameRequest(), "countryNameRequest");
        bindingResult.rejectValue("name", "NotBlank", "name must not be blank");

        MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class), 0);
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        List<ErrorResponse.FieldErrorDetail> fieldErrors = response.getBody().getFieldErrors();
        assertThat(fieldErrors).hasSize(1);
        assertThat(fieldErrors.get(0).getField()).isEqualTo("name");
        assertThat(fieldErrors.get(0).getMessage()).isEqualTo("name must not be blank");
    }

    @SuppressWarnings("unused")
    private void dummy(String name) {
        // Target method for MethodParameter in the validation test.
    }
}
