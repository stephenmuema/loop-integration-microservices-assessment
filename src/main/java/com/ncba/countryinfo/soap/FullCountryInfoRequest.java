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
 * JAXB binding for the {@code FullCountryInfo} SOAP request.
 *
 * <pre>{@code
 * <FullCountryInfo><sCountryISOCode>KE</sCountryISOCode></FullCountryInfo>
 * }</pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@XmlRootElement(name = "FullCountryInfo", namespace = SoapNamespace.NS)
@XmlAccessorType(XmlAccessType.FIELD)
public class FullCountryInfoRequest {

    @XmlElement(name = "sCountryISOCode", namespace = SoapNamespace.NS)
    private String countryISOCode;
}
