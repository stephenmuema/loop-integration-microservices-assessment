package com.ncba.countryinfo.service;

import com.ncba.countryinfo.dto.CountryResponse;
import com.ncba.countryinfo.dto.CountryUpdateRequest;
import java.util.List;

/**
 * Business operations for country information: the SOAP-backed ingestion flow
 * plus CRUD over persisted records.
 */
public interface CountryService {

    /**
     * Ingests a country by name: sentence-cases the name, resolves its ISO code
     * and full detail via SOAP, then upserts the record keyed on ISO code.
     *
     * @param rawName the country name as supplied by the client
     * @return the persisted country as a response DTO
     */
    CountryResponse ingestByName(String rawName);

    /** Returns all persisted countries. */
    List<CountryResponse> getAll();

    /** Returns a single country by its opaque public id, or throws if absent. */
    CountryResponse getById(String publicId);

    /** Updates the mutable fields of a country by its opaque public id. */
    CountryResponse update(String publicId, CountryUpdateRequest request);

    /** Deletes a country by its opaque public id, or throws if absent. */
    void delete(String publicId);
}
