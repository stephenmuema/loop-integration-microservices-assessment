package com.ncba.countryinfo.dto;

import com.ncba.countryinfo.model.CountryInfo;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;

/**
 * API representation of a persisted {@link CountryInfo}. Decouples the wire
 * contract from the JPA entity so persistence changes do not leak to clients.
 */
@Getter
@Builder
public class CountryResponse {

    /** Opaque public identifier (UUID). The internal numeric PK is never exposed. */
    private final String id;
    private final String name;
    private final String isoCode;
    private final String capitalCity;
    private final String phoneCode;
    private final String continentCode;
    private final String currencyISOCode;
    private final String countryFlag;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final List<LanguageDto> languages;

    /** Maps a persisted entity to its API response form. */
    public static CountryResponse fromEntity(CountryInfo entity) {
        List<LanguageDto> languages = entity.getLanguages().stream()
                .map(l -> new LanguageDto(l.getIsoCode(), l.getName()))
                .collect(Collectors.toList());

        return CountryResponse.builder()
                .id(entity.getPublicId())
                .name(entity.getName())
                .isoCode(entity.getIsoCode())
                .capitalCity(entity.getCapitalCity())
                .phoneCode(entity.getPhoneCode())
                .continentCode(entity.getContinentCode())
                .currencyISOCode(entity.getCurrencyISOCode())
                .countryFlag(entity.getCountryFlag())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .languages(languages)
                .build();
    }
}
