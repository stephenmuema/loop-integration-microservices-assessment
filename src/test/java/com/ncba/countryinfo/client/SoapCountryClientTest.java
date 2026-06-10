package com.ncba.countryinfo.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ncba.countryinfo.exception.SoapIntegrationException;
import com.ncba.countryinfo.soap.CountryISOCodeResponse;
import com.ncba.countryinfo.soap.FullCountryInfoResponse;
import com.ncba.countryinfo.soap.TCountryInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;

class SoapCountryClientTest {

    @Mock
    private WebServiceTemplate webServiceTemplate;

    private SoapCountryClient client;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        client = new SoapCountryClient(webServiceTemplate);
    }

    @Test
    void getIsoCodeReturnsResult() {
        CountryISOCodeResponse response = new CountryISOCodeResponse();
        response.setCountryISOCodeResult("KE");
        when(webServiceTemplate.marshalSendAndReceive(any())).thenReturn(response);

        assertThat(client.getIsoCode("Kenya")).isEqualTo("KE");
    }

    @Test
    void getIsoCodeReturnsNullWhenResponseNull() {
        when(webServiceTemplate.marshalSendAndReceive(any())).thenReturn(null);

        assertThat(client.getIsoCode("Atlantis")).isNull();
    }

    @Test
    void getFullCountryInfoReturnsResult() {
        TCountryInfo info = new TCountryInfo();
        info.setIsoCode("KE");
        info.setName("Kenya");
        FullCountryInfoResponse response = new FullCountryInfoResponse();
        response.setFullCountryInfoResult(info);
        when(webServiceTemplate.marshalSendAndReceive(any())).thenReturn(response);

        TCountryInfo result = client.getFullCountryInfo("KE");

        assertThat(result.getName()).isEqualTo("Kenya");
        assertThat(result.getIsoCode()).isEqualTo("KE");
    }

    @Test
    void getFullCountryInfoThrowsWhenResultEmpty() {
        FullCountryInfoResponse response = new FullCountryInfoResponse();
        response.setFullCountryInfoResult(null);
        when(webServiceTemplate.marshalSendAndReceive(any())).thenReturn(response);

        assertThatThrownBy(() -> client.getFullCountryInfo("XX"))
                .isInstanceOf(SoapIntegrationException.class)
                .hasMessageContaining("empty result");
    }

    @Test
    void isoCodeFallbackWrapsAsSoapIntegrationException() {
        WebServiceIOException cause = new WebServiceIOException("timeout");

        assertThatThrownBy(() -> client.getIsoCodeFallback("Kenya", cause))
                .isInstanceOf(SoapIntegrationException.class)
                .hasMessageContaining("Kenya")
                .hasCause(cause);
    }

    @Test
    void fullInfoFallbackWrapsAsSoapIntegrationException() {
        WebServiceIOException cause = new WebServiceIOException("connection refused");

        assertThatThrownBy(() -> client.getFullCountryInfoFallback("KE", cause))
                .isInstanceOf(SoapIntegrationException.class)
                .hasMessageContaining("KE")
                .hasCause(cause);
    }
}
