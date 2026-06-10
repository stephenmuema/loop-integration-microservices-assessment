package com.ncba.countryinfo.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for {@code PUT /api/countries/{id}}. Carries the mutable fields
 * of a country record. The ISO code is treated as the natural key and is not
 * editable here.
 */
@Getter
@Setter
public class CountryUpdateRequest {

    @NotBlank(message = "name must not be blank")
    private String name;

    private String capitalCity;
    private String phoneCode;
    private String continentCode;
    private String currencyISOCode;
    private String countryFlag;
    private List<LanguageDto> languages;
}
