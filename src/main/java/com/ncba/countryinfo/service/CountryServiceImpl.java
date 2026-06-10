package com.ncba.countryinfo.service;

import com.ncba.countryinfo.client.SoapCountryClient;
import com.ncba.countryinfo.dto.CountryResponse;
import com.ncba.countryinfo.dto.CountryUpdateRequest;
import com.ncba.countryinfo.dto.LanguageDto;
import com.ncba.countryinfo.exception.CountryNotFoundException;
import com.ncba.countryinfo.model.CountryInfo;
import com.ncba.countryinfo.model.Language;
import com.ncba.countryinfo.repository.CountryInfoRepository;
import com.ncba.countryinfo.soap.TCountryInfo;
import com.ncba.countryinfo.soap.TLanguage;
import com.ncba.countryinfo.util.StringUtils;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link CountryService} implementation. Orchestrates the SOAP client
 * and the JPA repository, and owns the SOAP→entity mapping and upsert logic.
 */
@Service
public class CountryServiceImpl implements CountryService {

    private static final Logger log = LoggerFactory.getLogger(CountryServiceImpl.class);

    private final SoapCountryClient soapClient;
    private final CountryInfoRepository repository;

    public CountryServiceImpl(SoapCountryClient soapClient, CountryInfoRepository repository) {
        this.soapClient = soapClient;
        this.repository = repository;
    }

    @Override
    @Transactional
    public CountryResponse ingestByName(String rawName) {
        String name = StringUtils.toSentenceCase(rawName);
        log.info("ingestByName: start rawName='{}' normalizedName='{}'", rawName, name);

        String isoCode = soapClient.getIsoCode(name);
        if (isoCode == null || isoCode.isBlank() || !isPlausibleIsoCode(isoCode)) {
            throw new CountryNotFoundException(
                    "No ISO code found for country name '" + name + "'");
        }

        TCountryInfo soapInfo = soapClient.getFullCountryInfo(isoCode);

        // The upstream service answers unknown countries with a sentinel record
        // (blank ISO code, name "Country not found in the database") rather than
        // a fault. Treat that as a 404 instead of persisting junk.
        if (soapInfo.getIsoCode() == null || soapInfo.getIsoCode().isBlank()) {
            throw new CountryNotFoundException(
                    "Country '" + name + "' could not be resolved by the CountryInfo service");
        }

        CountryInfo entity = repository.findByIsoCode(soapInfo.getIsoCode())
                .map(existing -> {
                    log.info("ingestByName: updating existing record id={} isoCode='{}'",
                            existing.getId(), isoCode);
                    return existing;
                })
                .orElseGet(() -> {
                    log.info("ingestByName: inserting new record isoCode='{}'", isoCode);
                    return new CountryInfo();
                });

        applySoapData(entity, soapInfo);
        CountryInfo saved = repository.save(entity);

        log.info("ingestByName: done id={} isoCode='{}' languages={}",
                saved.getId(), saved.getIsoCode(), saved.getLanguages().size());
        return CountryResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CountryResponse> getAll() {
        log.info("getAll: start");
        List<CountryResponse> result = repository.findAll().stream()
                .map(CountryResponse::fromEntity)
                .toList();
        log.info("getAll: done count={}", result.size());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public CountryResponse getById(String publicId) {
        log.info("getById: start publicId={}", publicId);
        CountryInfo entity = repository.findByPublicId(publicId)
                .orElseThrow(() -> new CountryNotFoundException("Country with id " + publicId + " not found"));
        log.info("getById: done publicId={}", publicId);
        return CountryResponse.fromEntity(entity);
    }

    @Override
    @Transactional
    public CountryResponse update(String publicId, CountryUpdateRequest request) {
        log.info("update: start publicId={}", publicId);
        CountryInfo entity = repository.findByPublicId(publicId)
                .orElseThrow(() -> new CountryNotFoundException("Country with id " + publicId + " not found"));

        entity.setName(StringUtils.toSentenceCase(request.getName()));
        entity.setCapitalCity(request.getCapitalCity());
        entity.setPhoneCode(request.getPhoneCode());
        entity.setContinentCode(request.getContinentCode());
        entity.setCurrencyISOCode(request.getCurrencyISOCode());
        entity.setCountryFlag(request.getCountryFlag());

        if (request.getLanguages() != null) {
            entity.clearLanguages();
            for (LanguageDto dto : request.getLanguages()) {
                entity.addLanguage(new Language(dto.getIsoCode(), dto.getName()));
            }
        }

        CountryInfo saved = repository.save(entity);
        log.info("update: done publicId={}", publicId);
        return CountryResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void delete(String publicId) {
        log.info("delete: start publicId={}", publicId);
        CountryInfo entity = repository.findByPublicId(publicId)
                .orElseThrow(() -> new CountryNotFoundException("Country with id " + publicId + " not found"));
        repository.delete(entity);
        log.info("delete: done publicId={}", publicId);
    }

    /**
     * Guards against the upstream's textual "not found" sentinel (e.g.
     * {@code "No country found by that name"}) being treated as an ISO code.
     * A genuine ISO code is short and alphabetic (e.g. {@code KE}).
     */
    private boolean isPlausibleIsoCode(String code) {
        String trimmed = code.trim();
        return trimmed.length() <= 3 && trimmed.chars().allMatch(Character::isLetter);
    }

    /** Copies SOAP payload fields onto the entity and rebuilds its languages. */
    private void applySoapData(CountryInfo entity, TCountryInfo soapInfo) {
        entity.setName(soapInfo.getName());
        entity.setIsoCode(soapInfo.getIsoCode());
        entity.setCapitalCity(soapInfo.getCapitalCity());
        entity.setPhoneCode(soapInfo.getPhoneCode());
        entity.setContinentCode(soapInfo.getContinentCode());
        entity.setCurrencyISOCode(soapInfo.getCurrencyISOCode());
        entity.setCountryFlag(soapInfo.getCountryFlag());

        entity.clearLanguages();
        List<TLanguage> soapLanguages = Optional.ofNullable(soapInfo.getLanguages()).orElseGet(List::of);
        for (TLanguage lang : soapLanguages) {
            entity.addLanguage(new Language(lang.getIsoCode(), lang.getName()));
        }
    }
}
