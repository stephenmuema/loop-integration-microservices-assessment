package com.ncba.countryinfo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the NCBA Country Info integration microservice.
 *
 * <p>The service exposes a REST API that, given a country name, consumes the
 * public CountryInfo SOAP service to resolve the ISO code and full country
 * details, persists the result to MySQL, and serves CRUD operations over the
 * stored data.</p>
 */
@SpringBootApplication
public class CountryInfoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CountryInfoApplication.class, args);
    }
}
