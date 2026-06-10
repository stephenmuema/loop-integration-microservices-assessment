package com.ncba.countryinfo.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

/**
 * JAXB binding for the {@code CountryISOCodeResponse} SOAP response.
 *
 * <pre>{@code
 * <CountryISOCodeResponse><CountryISOCodeResult>KE</CountryISOCodeResult></CountryISOCodeResponse>
 * }</pre>
 */
@Getter
@Setter
@XmlRootElement(name = "CountryISOCodeResponse", namespace = SoapNamespace.NS)
@XmlAccessorType(XmlAccessType.FIELD)
public class CountryISOCodeResponse {

    @XmlElement(name = "CountryISOCodeResult", namespace = SoapNamespace.NS)
    private String countryISOCodeResult;
}
