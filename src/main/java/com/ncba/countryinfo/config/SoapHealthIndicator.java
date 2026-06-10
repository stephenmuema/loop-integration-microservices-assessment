package com.ncba.countryinfo.config;

import java.net.HttpURLConnection;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator health indicator reporting reachability of the CountryInfo SOAP
 * service. Contributes to {@code /actuator/health} under the {@code soap} key.
 *
 * <p>Deliberately kept out of the Kubernetes <em>readiness</em> group (see
 * {@code application.yml}): a transient outage of an external dependency should
 * not eject pods that can still serve persisted CRUD reads. It remains visible
 * for monitoring and alerting.</p>
 */
@Component("soap")
public class SoapHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(SoapHealthIndicator.class);

    private final String endpoint;
    private final int probeTimeoutMs;

    public SoapHealthIndicator(
            @Value("${soap.country-info.endpoint:http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso}") String endpoint,
            @Value("${soap.country-info.health-timeout-ms:3000}") int probeTimeoutMs) {
        this.endpoint = endpoint;
        this.probeTimeoutMs = probeTimeoutMs;
    }

    @Override
    public Health health() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(endpoint + "?WSDL").toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(probeTimeoutMs);
            connection.setReadTimeout(probeTimeoutMs);
            int code = connection.getResponseCode();
            if (code >= 200 && code < 400) {
                return Health.up().withDetail("endpoint", endpoint).withDetail("httpStatus", code).build();
            }
            return Health.down().withDetail("endpoint", endpoint).withDetail("httpStatus", code).build();
        } catch (Exception ex) {
            log.warn("SOAP health probe failed for {}: {}", endpoint, ex.toString());
            return Health.down().withDetail("endpoint", endpoint).withDetail("error", ex.getMessage()).build();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
