package com.ncba.countryinfo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for {@code POST /api/countries}, e.g. {@code {"name":"Kenya"}}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CountryNameRequest {

    @NotBlank(message = "name must not be blank")
    private String name;
}
