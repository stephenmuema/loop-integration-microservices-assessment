package com.ncba.countryinfo.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

/**
 * JAXB binding for a single {@code tLanguage} element within the
 * {@code Languages} collection of a FullCountryInfo response.
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class TLanguage {

    @XmlElement(name = "sISOCode", namespace = SoapNamespace.NS)
    private String isoCode;

    @XmlElement(name = "sName", namespace = SoapNamespace.NS)
    private String name;
}
