package com.ncba.countryinfo.repository;

import com.ncba.countryinfo.model.CountryInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link CountryInfo}. The {@code findByIsoCode}
 * derived query backs the upsert decision in the service layer.
 */
@Repository
public interface CountryInfoRepository extends JpaRepository<CountryInfo, Long> {

    /** Looks up an existing record by its (unique) ISO code. */
    Optional<CountryInfo> findByIsoCode(String isoCode);

    /** Looks up a record by its opaque public identifier (used by the API). */
    Optional<CountryInfo> findByPublicId(String publicId);
}
