package com.ncba.countryinfo.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persistent representation of a country, populated from the CountryInfo SOAP
 * service. Uniqueness is keyed on {@link #isoCode} to support the upsert flow.
 */
@Entity
@Table(name = "country_info")
@Getter
@Setter
@NoArgsConstructor
public class CountryInfo {

    /**
     * Internal sequential primary key. Used only for joins/indexing and is
     * never exposed over the API — see {@link #publicId}.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Opaque, non-guessable public identifier used in all REST paths and
     * responses. Mitigates IDOR / record enumeration that a sequential
     * {@link #id} would otherwise allow. Assigned at construction so it is
     * always present, immutable once persisted.
     */
    @Column(name = "public_id", nullable = false, unique = true, updatable = false, length = 36)
    private String publicId = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String name;

    @Column(name = "iso_code", nullable = false, unique = true)
    private String isoCode;

    @Column(name = "capital_city")
    private String capitalCity;

    @Column(name = "phone_code")
    private String phoneCode;

    @Column(name = "continent_code")
    private String continentCode;

    @Column(name = "currency_iso_code")
    private String currencyISOCode;

    @Column(name = "country_flag", length = 1024)
    private String countryFlag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "countryInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Language> languages = new ArrayList<>();

    /** Adds a language and maintains the bidirectional association. */
    public void addLanguage(Language language) {
        language.setCountryInfo(this);
        this.languages.add(language);
    }

    /** Clears all languages, severing the child associations. */
    public void clearLanguages() {
        this.languages.forEach(l -> l.setCountryInfo(null));
        this.languages.clear();
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
