package com.ncba.countryinfo.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JAXB binding for the {@code CountryISOCode} SOAP request.
 *
 * <pre>{@code
 * <CountryISOCode><sCountryName>Kenya</sCountryName></CountryISOCode>
 * }</pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@XmlRootElement(name = "CountryISOCode", namespace = SoapNamespace.NS)
@XmlAccessorType(XmlAccessType.FIELD)
public class CountryISOCodeRequest {

    @XmlElement(name = "sCountryName", namespace = SoapNamespace.NS)
    private String countryName;
}
