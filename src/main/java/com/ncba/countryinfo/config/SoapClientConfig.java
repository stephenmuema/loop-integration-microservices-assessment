package com.ncba.countryinfo.config;

import com.ncba.countryinfo.soap.CountryISOCodeRequest;
import com.ncba.countryinfo.soap.CountryISOCodeResponse;
import com.ncba.countryinfo.soap.FullCountryInfoRequest;
import com.ncba.countryinfo.soap.FullCountryInfoResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

/**
 * Wires the Spring-WS {@link WebServiceTemplate} used to call the CountryInfo
 * SOAP service.
 *
 * <p>Configures:</p>
 * <ul>
 *     <li>A JAXB marshaller bound to the request/response payload classes.</li>
 *     <li>A message sender with explicit connect/read timeouts so a slow
 *         upstream cannot exhaust request threads.</li>
 *     <li>Full SOAP envelope logging at DEBUG via Spring-WS message tracing —
 *         set {@code org.springframework.ws.client.MessageTracing} to DEBUG
 *         (request/response) or TRACE (full payloads), see {@code application.yml}.</li>
 * </ul>
 */
@Configuration
public class SoapClientConfig {

    @Value("${soap.country-info.endpoint:http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso}")
    private String endpoint;

    @Value("${soap.country-info.connect-timeout-ms:5000}")
    private long connectTimeoutMs;

    @Value("${soap.country-info.read-timeout-ms:10000}")
    private long readTimeoutMs;

    @Bean
    public Jaxb2Marshaller countryInfoMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(
                CountryISOCodeRequest.class,
                CountryISOCodeResponse.class,
                FullCountryInfoRequest.class,
                FullCountryInfoResponse.class);
        return marshaller;
    }

    @Bean
    public HttpUrlConnectionMessageSender countryInfoMessageSender() {
        HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender();
        sender.setConnectionTimeout(Duration.ofMillis(connectTimeoutMs));
        sender.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return sender;
    }

    @Bean
    public WebServiceTemplate countryInfoWebServiceTemplate(Jaxb2Marshaller countryInfoMarshaller,
                                                            HttpUrlConnectionMessageSender countryInfoMessageSender) {
        WebServiceTemplate template = new WebServiceTemplate();
        template.setMarshaller(countryInfoMarshaller);
        template.setUnmarshaller(countryInfoMarshaller);
        template.setDefaultUri(endpoint);
        template.setMessageSender(countryInfoMessageSender);
        return template;
    }
}
