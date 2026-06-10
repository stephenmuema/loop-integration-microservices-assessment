package com.ncba.countryinfo.client;

import com.ncba.countryinfo.exception.SoapIntegrationException;
import com.ncba.countryinfo.soap.CountryISOCodeRequest;
import com.ncba.countryinfo.soap.CountryISOCodeResponse;
import com.ncba.countryinfo.soap.FullCountryInfoRequest;
import com.ncba.countryinfo.soap.FullCountryInfoResponse;
import com.ncba.countryinfo.soap.TCountryInfo;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;

/**
 * Resilient SOAP client for the public CountryInfo service.
 *
 * <p>Each outbound call is wrapped with Resilience4j {@link Retry} (3 attempts,
 * exponential backoff — configured in {@code application.yml}) and
 * {@link CircuitBreaker}. When the call ultimately fails or the breaker is
 * open, the matching {@code *Fallback} method runs and translates the failure
 * into a {@link SoapIntegrationException} so the web layer can return a clean
 * 502 instead of leaking a low-level transport error.</p>
 *
 * <p>Enabling DEBUG on {@code org.springframework.ws.client.MessageTracing}
 * prints the full request and response SOAP envelopes (see
 * {@code SoapClientConfig} and {@code application.yml}).</p>
 */
@Component
public class SoapCountryClient {

    private static final Logger log = LoggerFactory.getLogger(SoapCountryClient.class);
    private static final String BACKEND = "countryInfoSoap";

    private final WebServiceTemplate webServiceTemplate;

    public SoapCountryClient(WebServiceTemplate countryInfoWebServiceTemplate) {
        this.webServiceTemplate = countryInfoWebServiceTemplate;
    }

    /**
     * Resolves a country name to its ISO code via the {@code CountryISOCode}
     * SOAP operation.
     *
     * @param countryName the (already sentence-cased) country name
     * @return the ISO code, or {@code null}/blank if the service did not
     *         recognise the country
     */
    @Retry(name = BACKEND)
    @CircuitBreaker(name = BACKEND, fallbackMethod = "getIsoCodeFallback")
    public String getIsoCode(String countryName) {
        log.info("SOAP CountryISOCode request: countryName='{}'", countryName);
        CountryISOCodeRequest request = new CountryISOCodeRequest(countryName);

        CountryISOCodeResponse response =
                (CountryISOCodeResponse) webServiceTemplate.marshalSendAndReceive(request);

        String isoCode = response != null ? response.getCountryISOCodeResult() : null;
        log.info("SOAP CountryISOCode response: countryName='{}' -> isoCode='{}'", countryName, isoCode);
        return isoCode;
    }

    /**
     * Fetches the full country detail for an ISO code via the
     * {@code FullCountryInfo} SOAP operation.
     *
     * @param isoCode the country ISO code (e.g. {@code KE})
     * @return the populated {@link TCountryInfo}, never {@code null}
     */
    @Retry(name = BACKEND)
    @CircuitBreaker(name = BACKEND, fallbackMethod = "getFullCountryInfoFallback")
    public TCountryInfo getFullCountryInfo(String isoCode) {
        log.info("SOAP FullCountryInfo request: isoCode='{}'", isoCode);
        FullCountryInfoRequest request = new FullCountryInfoRequest(isoCode);

        FullCountryInfoResponse response =
                (FullCountryInfoResponse) webServiceTemplate.marshalSendAndReceive(request);

        if (response == null || response.getFullCountryInfoResult() == null) {
            throw new SoapIntegrationException(
                    "FullCountryInfo returned an empty result for ISO code '" + isoCode + "'");
        }
        TCountryInfo result = response.getFullCountryInfoResult();
        log.info("SOAP FullCountryInfo response: isoCode='{}' -> name='{}'", isoCode, result.getName());
        return result;
    }

    /**
     * Fallback for {@link #getIsoCode(String)} — invoked after retries are
     * exhausted or while the circuit breaker is open.
     */
    @SuppressWarnings("unused")
    String getIsoCodeFallback(String countryName, Throwable t) {
        log.error("SOAP CountryISOCode failed for countryName='{}': {}", countryName, t.toString(), t);
        throw new SoapIntegrationException(
                "Unable to resolve ISO code for country '" + countryName
                        + "' from the CountryInfo SOAP service", t);
    }

    /**
     * Fallback for {@link #getFullCountryInfo(String)} — invoked after retries
     * are exhausted or while the circuit breaker is open.
     */
    @SuppressWarnings("unused")
    TCountryInfo getFullCountryInfoFallback(String isoCode, Throwable t) {
        log.error("SOAP FullCountryInfo failed for isoCode='{}': {}", isoCode, t.toString(), t);
        throw new SoapIntegrationException(
                "Unable to fetch full country info for ISO code '" + isoCode
                        + "' from the CountryInfo SOAP service", t);
    }
}
