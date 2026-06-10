package com.ncba.countryinfo.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * JAXB binding for the {@code FullCountryInfoResult} complex type returned by
 * the CountryInfo SOAP service.
 *
 * <p>The {@code Languages} wrapper holds zero or more {@code tLanguage}
 * elements, mapped here with {@link XmlElementWrapper}.</p>
 */
@Getter
@Setter
@XmlAccessorType(XmlAccessType.FIELD)
public class TCountryInfo {

    @XmlElement(name = "sISOCode", namespace = SoapNamespace.NS)
    private String isoCode;

    @XmlElement(name = "sName", namespace = SoapNamespace.NS)
    private String name;

    @XmlElement(name = "sCapitalCity", namespace = SoapNamespace.NS)
    private String capitalCity;

    @XmlElement(name = "sPhoneCode", namespace = SoapNamespace.NS)
    private String phoneCode;

    @XmlElement(name = "sContinentCode", namespace = SoapNamespace.NS)
    private String continentCode;

    @XmlElement(name = "sCurrencyISOCode", namespace = SoapNamespace.NS)
    private String currencyISOCode;

    @XmlElement(name = "sCountryFlag", namespace = SoapNamespace.NS)
    private String countryFlag;

    @XmlElementWrapper(name = "Languages", namespace = SoapNamespace.NS)
    @XmlElement(name = "tLanguage", namespace = SoapNamespace.NS)
    private List<TLanguage> languages = new ArrayList<>();
}
