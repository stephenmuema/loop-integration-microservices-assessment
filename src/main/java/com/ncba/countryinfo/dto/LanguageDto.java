package com.ncba.countryinfo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** API representation of a language belonging to a country. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LanguageDto {

    private String isoCode;
    private String name;
}
